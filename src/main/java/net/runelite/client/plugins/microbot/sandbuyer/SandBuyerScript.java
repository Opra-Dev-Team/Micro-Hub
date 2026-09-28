package net.runelite.client.plugins.microbot.sandbuyer;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class SandBuyerScript extends Script {

    static final WorldPoint TRADER_TILE = new WorldPoint(2796, 3414, 0);
    static final String     SAND        = "Bucket of sand";
    static final String     ASH         = "Soda ash";
    static final int        SAND_ID     = ItemID.BUCKET_OF_SAND;
    static final int        ASH_ID      = ItemID.SODA_ASH;
    static final int        ITEM_COST   = 5;
    static final int        PER_ROUND   = 10;

    private static final String TRADER_NAME       = "Trader Crewmember";
    private static final int    MAX_HOP_ATTEMPTS  = 3;
    private static final long   HOP_TIMEOUT_MS    = 10_000L;
    private static final long   HOP_IDLE_SETTLE_MS = 700L;
    private static final int    SHOP_ITEMS_WIDGET = 19660816;
    private static final int[]  BUY_QUANTITIES    = {10, 5, 1};
    private static final Set<String> BLOCKED_WORLD_TYPES = blockedWorldTypeNames();
    private static final String[] BLOCKED_ACTIVITY_SNIPPETS = {
            "skill total", "total level", "pvp", "deadman", "high risk", "bounty",
            "speedrun", "fresh start", "tournament", "last man standing", "lms",
            "beta", "league", "arena"
    };

    public volatile State state       = State.OPEN_SHOP;
    public volatile int   sandBought;
    public volatile int   ashBought;
    public volatile int   hopCount;
    public volatile int   currentWorld;

    private final Map<Integer, Long> worldCooldowns = new HashMap<>();

    private boolean hopPending;
    private boolean hopAwaitingLanding;
    private int     beforeHopWorld;
    private int     targetWorld;
    private long    lastHopAttemptMs;
    private long    hopIdleSinceMs;
    private int     hopAttempts;

    public boolean run(SandBuyerConfig config) {
        shutdown();
        state          = State.OPEN_SHOP;
        sandBought     = 0;
        ashBought      = 0;
        hopCount       = 0;
        currentWorld   = 0;
        hopPending     = false;
        hopAwaitingLanding = false;
        beforeHopWorld = 0;
        targetWorld    = 0;
        lastHopAttemptMs = 0;
        hopIdleSinceMs = 0;
        hopAttempts    = 0;
        worldCooldowns.clear();

        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyGeneralBasicSetup();
        Rs2Antiban.setActivity(Activity.GENERAL_COLLECTING);
        Rs2Antiban.setActivityIntensity(ActivityIntensity.HIGH);

        Microbot.status = "Starting Sand Buyer";

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                currentWorld = Microbot.getClient().getWorld();

                switch (state) {
                    case OPEN_SHOP: openShop();      break;
                    case BUY:       buy(config);     break;
                    case DEPOSIT:   deposit(config); break;
                    case HOP:       hop(config);     break;
                    case DONE:      done();          break;
                    default:        break;
                }
            } catch (Exception ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    return;
                }
                Microbot.logStackTrace(getClass().getSimpleName(), ex);
            }
        }, 0, 200, TimeUnit.MILLISECONDS);

        return true;
    }

    private void openShop() {
        WorldPoint pos = Rs2Player.getWorldLocation();
        if (pos == null) {
            return;
        }
        if (pos.distanceTo(TRADER_TILE) > 15) {
            Microbot.status = "Walking to Trader";
            Rs2Walker.walkTo(TRADER_TILE, 5);
            return;
        }

        if (Rs2Shop.isOpen() && isShopReady()) {
            state = State.BUY;
            return;
        }

        if (Rs2Shop.getNearestShopNpc(TRADER_NAME, true) == null
                && Rs2Shop.getNearestShopNpc("Crewmember", false) == null) {
            Microbot.status = "Waiting for trader";
            return;
        }

        long started = System.currentTimeMillis();
        Microbot.status = "Opening shop";
        boolean opened = Rs2Shop.openShop(TRADER_NAME, true);
        if (!opened) {
            opened = Rs2Shop.openShop("Crewmember", false);
        }
        if (!opened || !Rs2Shop.isOpen()) {
            return;
        }
        sleepUntil(this::isShopReady, 2500);
        if (!isShopReady()) {
            log.warn("[SandBuyer] shop opened but item widget was not ready");
            return;
        }
        log.info("[SandBuyer] shop ready in {}ms", System.currentTimeMillis() - started);
        state = State.BUY;
    }

    private void buy(SandBuyerConfig config) {
        if (!Rs2Shop.isOpen() || !isShopReady()) {
            state = State.OPEN_SHOP;
            return;
        }

        int buySand = nextBuyAmount(config.totalSandTarget(), sandBought);
        int buyAsh  = nextBuyAmount(config.totalAshTarget(), ashBought);
        if (buySand <= 0 && buyAsh <= 0) {
            if (Rs2Shop.isOpen()) {
                Rs2Shop.closeShop();
            }
            state = State.DONE;
            return;
        }

        Microbot.status = String.format("Buying %d sand, %d ash", buySand, buyAsh);
        long started = System.currentTimeMillis();
        int sandBefore = Rs2Inventory.count(SAND_ID);
        int ashBefore  = Rs2Inventory.count(ASH_ID);

        if (buySand > 0) {
            buyQuantityFast(SAND, SAND_ID, buySand);
        }
        if (buyAsh > 0 && isShopReady()) {
            buyQuantityFast(ASH, ASH_ID, buyAsh);
        }

        int sandDelta = Math.max(0, Rs2Inventory.count(SAND_ID) - sandBefore);
        int ashDelta  = Math.max(0, Rs2Inventory.count(ASH_ID) - ashBefore);
        sandBought += sandDelta;
        ashBought  += ashDelta;
        log.info("[SandBuyer] bought sand={} ash={} in {}ms",
                sandDelta, ashDelta, System.currentTimeMillis() - started);

        if (Rs2Shop.isOpen()) {
            Rs2Shop.closeShop();
        }

        if (sandDelta == 0 && ashDelta == 0) {
            state = State.HOP;
        } else {
            state = State.DEPOSIT;
        }
    }

    private void buyQuantityFast(String name, int itemId, int desired) {
        if (desired <= 0 || !Rs2Shop.hasStock(name)) {
            return;
        }
        for (int qty : purchasePlan(desired)) {
            if (!isShopReady()) {
                log.warn("[SandBuyer] shop widget disappeared before buying {} x{}", name, qty);
                return;
            }
            int before = Rs2Inventory.count(itemId);
            if (!Rs2Shop.buyItem(name, String.valueOf(qty))) {
                return;
            }
            sleepUntil(() -> Rs2Inventory.count(itemId) > before, 1500);
            if (Rs2Inventory.count(itemId) <= before) {
                log.warn("[SandBuyer] buy {} x{} did not land", name, qty);
                return;
            }
        }
    }

    private void deposit(SandBuyerConfig config) {
        if (!Rs2Inventory.contains(SAND_ID) && !Rs2Inventory.contains(ASH_ID)) {
            afterDeposit(config);
            return;
        }

        long started = System.currentTimeMillis();
        Microbot.status = "Depositing";
        if (!Rs2DepositBox.isOpen()) {
            if (!Rs2DepositBox.openDepositBox() || !Rs2DepositBox.isOpen()) {
                return;
            }
        }

        Rs2DepositBox.depositAll(SAND_ID, ASH_ID);
        if (Rs2Inventory.contains(SAND_ID) || Rs2Inventory.contains(ASH_ID)) {
            sleepUntil(() -> !Rs2Inventory.contains(SAND_ID) && !Rs2Inventory.contains(ASH_ID), 2000);
        }

        if (Rs2DepositBox.isOpen()) {
            Rs2DepositBox.closeDepositBox();
        }

        log.info("[SandBuyer] deposited in {}ms", System.currentTimeMillis() - started);
        afterDeposit(config);
    }

    private void afterDeposit(SandBuyerConfig config) {
        int sandTarget = config.totalSandTarget();
        int ashTarget  = config.totalAshTarget();
        boolean sandMet = sandTarget <= 0 || sandBought >= sandTarget;
        boolean ashMet  = ashTarget <= 0 || ashBought >= ashTarget;
        if (sandMet && ashMet && (sandTarget > 0 || ashTarget > 0)) {
            state = State.DONE;
        } else {
            state = State.HOP;
        }
    }

    private void hop(SandBuyerConfig config) {
        if (observePendingHop()) {
            return;
        }

        closeBlockingInterfaces();
        if (!isPlayerIdleForHop()) {
            hopIdleSinceMs = 0;
            Microbot.status = "Waiting to hop";
            return;
        }
        if (hopIdleSinceMs == 0) {
            hopIdleSinceMs = System.currentTimeMillis();
        }
        if (System.currentTimeMillis() - hopIdleSinceMs < HOP_IDLE_SETTLE_MS) {
            Microbot.status = "Waiting to hop";
            return;
        }

        if (hopAttempts >= MAX_HOP_ATTEMPTS) {
            hopAttempts = 0;
            targetWorld = 0;
        }

        int current = Microbot.getClient().getWorld();
        int target = (targetWorld > 0 && targetWorld != current)
                ? targetWorld
                : pickSafeWorld(config, current);
        if (target <= 0) {
            Microbot.status = "No safe world available";
            return;
        }

        beforeHopWorld     = current;
        targetWorld        = target;
        lastHopAttemptMs   = System.currentTimeMillis();
        hopPending         = true;
        hopAwaitingLanding = true;
        hopAttempts++;
        Microbot.status    = "Hopping to world " + target;
        log.info("[SandBuyer] hop requested {} -> {} attempt {}", current, target, hopAttempts);
        boolean ok = Microbot.hopToWorld(target);
        if (ok || Microbot.isHopping() || hopLanded(beforeHopWorld, Microbot.getClient().getWorld())) {
            observePendingHop();
            return;
        }
        hopAwaitingLanding = false;
        hopAttempts = Math.max(0, hopAttempts - 1);
        hopIdleSinceMs = 0;
        log.warn("[SandBuyer] hop to {} rejected while busy, retrying same world after idle", target);
    }

    private boolean observePendingHop() {
        if (!hopPending) {
            return false;
        }

        GameState gs = Microbot.getClient().getGameState();
        if (gs == GameState.HOPPING || gs == GameState.LOGIN_SCREEN || gs == GameState.LOADING) {
            Microbot.status = "Hop in progress";
            return true;
        }
        if (gs != GameState.LOGGED_IN) {
            Microbot.status = "Hop in progress";
            return true;
        }

        int current = Microbot.getClient().getWorld();
        if (hopLanded(beforeHopWorld, current)) {
            long elapsed = System.currentTimeMillis() - lastHopAttemptMs;
            worldCooldowns.put(beforeHopWorld, System.currentTimeMillis());
            hopCount++;
            currentWorld = current;
            hopPending = false;
            hopAwaitingLanding = false;
            hopAttempts = 0;
            targetWorld = 0;
            hopIdleSinceMs = 0;
            log.info("[SandBuyer] hop confirmed world {} in {}ms", current, elapsed);
            Microbot.status = "Hopped to world " + current;
            if (Rs2Random.dicePercentage(8)) {
                Rs2Antiban.moveMouseRandomly();
            }
            state = State.OPEN_SHOP;
            return true;
        }

        if (hopAwaitingLanding && hopShouldWait(System.currentTimeMillis() - lastHopAttemptMs, HOP_TIMEOUT_MS)) {
            Microbot.status = "Waiting for hop to land";
            return true;
        }
        if (hopAwaitingLanding) {
            log.warn("[SandBuyer] hop timed out after {}ms (from {} to {})",
                    System.currentTimeMillis() - lastHopAttemptMs, beforeHopWorld, targetWorld);
            hopAwaitingLanding = false;
            hopPending = false;
            targetWorld = 0;
        }
        return false;
    }

    private void done() {
        String msg = String.format("Done! Sand: %,d  Ash: %,d  GP spent: %s",
                sandBought, ashBought, formatGp((long) (sandBought + ashBought) * ITEM_COST));
        Microbot.status = msg;
        log.info("[SandBuyer] {}", msg);
        shutdown();
    }

    private boolean isShopReady() {
        return Boolean.TRUE.equals(Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget shop = Microbot.getClient().getWidget(SHOP_ITEMS_WIDGET);
            if (shop == null || shop.isHidden()) {
                return false;
            }
            Widget[] children = shop.getDynamicChildren();
            return children != null && children.length > 1;
        }).orElse(false));
    }

    private void closeBlockingInterfaces() {
        if (Rs2Shop.isOpen()) {
            Rs2Shop.closeShop();
        }
        if (Rs2DepositBox.isOpen()) {
            Rs2DepositBox.closeDepositBox();
        }
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
        }
    }

    private boolean isPlayerIdleForHop() {
        return !Rs2Player.isAnimating()
                && !Rs2Player.isMoving()
                && !Rs2Player.isInteracting()
                && !Rs2Shop.isOpen()
                && !Rs2DepositBox.isOpen()
                && !Rs2Bank.isOpen()
                && !Rs2Dialogue.isInDialogue();
    }

    private int pickSafeWorld(SandBuyerConfig config, int current) {
        WorldResult result = Microbot.getWorldService().getWorlds();
        if (result == null || result.getWorlds() == null) {
            return -1;
        }
        long now = System.currentTimeMillis();
        long cooldownMs = config.worldCooldownSeconds() * 1000L;
        List<World> candidates = new ArrayList<>();
        for (World world : result.getWorlds()) {
            if (world == null || world.getId() == current) {
                continue;
            }
            if (!isHopSafeWorld(typeNames(world.getTypes()), world.getActivity())) {
                continue;
            }
            int players = world.getPlayers();
            if (players < 0 || players >= 2000) {
                continue;
            }
            if (config.avoidEmptyWorlds() && players < 50) {
                continue;
            }
            if (config.avoidOvercrowdedWorlds() && players > 1800) {
                continue;
            }
            Long last = worldCooldowns.get(world.getId());
            if (last != null && now - last < cooldownMs) {
                continue;
            }
            candidates.add(world);
        }
        if (candidates.isEmpty()) {
            return -1;
        }
        World chosen = candidates.get(Rs2Random.betweenInclusive(0, candidates.size() - 1));
        log.info("[SandBuyer] selected members world {} activity='{}' types={}",
                chosen.getId(),
                chosen.getActivity() == null ? "" : chosen.getActivity(),
                typeNames(chosen.getTypes()));
        return chosen.getId();
    }

    static Set<String> typeNames(Collection<WorldType> types) {
        if (types == null || types.isEmpty()) {
            return Set.of();
        }
        return types.stream().map(Enum::name).collect(Collectors.toSet());
    }

    static boolean isHopSafeWorld(Collection<String> typeNames, String activity) {
        if (typeNames == null || !typeNames.contains("MEMBERS")) {
            return false;
        }
        for (String blocked : BLOCKED_WORLD_TYPES) {
            if (typeNames.contains(blocked)) {
                return false;
            }
        }
        return !hasRestrictedActivity(activity);
    }

    static boolean hasRestrictedActivity(String activity) {
        if (activity == null || activity.isBlank()) {
            return false;
        }
        String lower = activity.toLowerCase();
        for (String snippet : BLOCKED_ACTIVITY_SNIPPETS) {
            if (lower.contains(snippet)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> blockedWorldTypeNames() {
        Set<String> blocked = new HashSet<>();
        blocked.add("PVP");
        blocked.add("HIGH_RISK");
        blocked.add("BOUNTY");
        blocked.add("SKILL_TOTAL");
        blocked.add("LAST_MAN_STANDING");
        blocked.add("QUEST_SPEEDRUNNING");
        blocked.add("BETA_WORLD");
        blocked.add("DEADMAN");
        blocked.add("PVP_ARENA");
        blocked.add("TOURNAMENT");
        blocked.add("TOURNAMENT_WORLD");
        blocked.add("NOSAVE_MODE");
        blocked.add("LEGACY_ONLY");
        blocked.add("EOC_ONLY");
        blocked.add("FRESH_START_WORLD");
        blocked.add("SEASONAL");
        return Set.copyOf(blocked);
    }

    static List<Integer> purchasePlan(int desired) {
        List<Integer> plan = new ArrayList<>();
        int remaining = Math.max(0, desired);
        for (int qty : BUY_QUANTITIES) {
            while (remaining >= qty) {
                plan.add(qty);
                remaining -= qty;
            }
        }
        return plan;
    }

    static int nextBuyAmount(int target, int bought) {
        if (target <= 0) {
            return PER_ROUND;
        }
        return Math.min(PER_ROUND, Math.max(0, target - bought));
    }

    static boolean hopLanded(int beforeWorld, int currentWorld) {
        return beforeWorld > 0 && currentWorld > 0 && currentWorld != beforeWorld;
    }

    static boolean hopShouldWait(long elapsedMs, long timeoutMs) {
        return elapsedMs >= 0 && elapsedMs < timeoutMs;
    }

    static String formatGp(long gp) {
        if (gp >= 1_000_000) return String.format("%.1fm", gp / 1_000_000.0);
        if (gp >= 1_000) return (gp / 1_000) + "k";
        return String.valueOf(gp);
    }

    @Override
    public void shutdown() {
        hopPending = false;
        hopAwaitingLanding = false;
        targetWorld = 0;
        hopAttempts = 0;
        hopIdleSinceMs = 0;
        worldCooldowns.clear();
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }

    public enum State {
        OPEN_SHOP,
        BUY,
        DEPOSIT,
        HOP,
        DONE
    }
}
