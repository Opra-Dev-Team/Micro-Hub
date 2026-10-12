package net.runelite.client.plugins.microbot.oprablastfurnace;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.oprablastfurnace.enums.Bars;
import net.runelite.client.plugins.microbot.oprablastfurnace.enums.State;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.runelite.api.gameval.ItemID.*;
import static net.runelite.api.gameval.ObjectID.*;
import static net.runelite.api.gameval.VarbitID.*;

@Slf4j
public class OpraBlastFurnaceScript extends Script {
    private static final Pattern ITEM_NAME_SUFFIX_PATTERN = Pattern.compile("^(.*?)(?:\\s*\\((\\d+)\\))?$");
    static final int coalBag = 12019;
    private static final int MAX_ORE_PER_INTERACTION = 27;
    private static final int MAX_ORE_PER_HYBRID_INTERACTION = 26;
    private static final int CONVEYOR_MAX_RETRIES = 2;
    private static final int FILL_MAX_RETRIES = 5;
    private static final long WAIT_BARS_ORE_GRACE_MS = 6000L;
    private static final long BAR_COUNT_STABLE_MS = 3000L;
    private static final long LOOT_REMAINING_WAIT_MS = 6000L;
    private static final long WAIT_BARS_MAX_MS = 30000L;
    private static final int COFFER_MAX_RETRIES = 3;
    private static final long TAKE_MENU_TRUST_MS = 4000L;
    private static final int LOOT_MAX_ATTEMPTS = 4;
    private static final int LOOT_SPACE_PRESSES = 2;
    private static final int PRODUCTION_GROUP = 270;
    private static final int WATCHDOG_IDLE_SECONDS = 45;
    private static final int WATCHDOG_MAX_RECOVERIES = 3;
    private static final long WATCHDOG_WINDOW_MS = 5 * 60_000L;
    private static final long WATCHDOG_LOOP_MS = 90_000L;
    private static final int FOREMAN_FEE = 2500;
    private static final long FOREMAN_REPAY_MS = 570_000L;
    private static final WorldPoint DISPENSER_TILE = new WorldPoint(1940, 4962, 0);
    private static final WorldPoint STAIRS_TILE = new WorldPoint(2931, 10197, 0);

    public static State state = State.BANKING;
    static volatile boolean coalBagFull;
    static volatile boolean coalBagKnown;
    static volatile long lastCoalBagChatMs;
    static boolean primaryOreEmpty;
    static boolean secondaryOreEmpty;

    static volatile boolean foremanPermissionNeeded;
    static volatile long foremanPermissionChatMs;
    private long foremanPaidAtMs;
    private boolean init = false;

    private enum TripMode {
        PRIMARY,
        DOUBLE_COAL,
        COAL_AND_PRIMARY,
        COAL_AND_GOLD,
        GOLD
    }

    private enum SmithPhase {
        DEPOSIT,
        WAIT_BARS,
        LOOT
    }

    private enum TaxStep {
        WITHDRAW,
        PAY,
        VERIFY
    }

    private enum CofferStep {
        WITHDRAW,
        USE,
        DEPOSIT,
        VERIFY
    }

    private TripMode tripMode = TripMode.COAL_AND_PRIMARY;
    private SmithPhase smithPhase = SmithPhase.DEPOSIT;
    private int conveyorRetries;
    private int fillRetries;
    private int emptyRetries;
    private String lastFillReason = "none";
    private int depositPass;
    private long moltenWaitUntilMs;
    private long waitBarsStartMs;
    private int expectedBars;
    private int lastBarCount;
    private long lootEmptySinceMs;
    private long lastTakeClickMs;
    private int lootAttempts;
    private boolean inputInterrupted;
    private final Deque<Long> recoveryTimes = new ArrayDeque<>();
    private volatile long lastProgressMs;
    private long lastBarChangeMs;
    private CofferStep cofferStep = CofferStep.WITHDRAW;
    private int cofferDeposit;
    private int cofferBefore;
    private int cofferRetries;
    private long cofferCooldownUntilMs;
    private boolean cofferWithdrawIssued;
    private int coinsBeforeWithdraw;
    private int coinsBeforeDeposit;
    private TaxStep taxStep = TaxStep.WITHDRAW;
    private int taxRetries;
    private int taxCoinsBefore;
    private long bankOpenBackoffUntilMs;
    private State resumeAfterTax = State.SMITHING;

    private final OpraBlastFurnacePlugin plugin;
    private final OpraBlastFurnaceConfig config;
    private final OpraBlastFurnaceDebug debug = new OpraBlastFurnaceDebug();

    OpraBlastFurnaceDebug getDebug() {
        return debug;
    }

    String buildDebugSnapshot() {
        Bars bar = config.getBars();
        int furnaceCoal = Microbot.getVarbitValue(
                bar == Bars.GOLD_BAR ? BLAST_FURNACE_GOLD_ORE : BLAST_FURNACE_COAL);
        WorldPoint tile = Rs2Player.getWorldLocation();
        return String.format(
                "state=%s phase=%s trip=%s status=%s bankOpen=%s oreInv=%d barsDisp=%d furnaceCoal=%d "
                        + "furnaceOre=%d expectedBars=%d coffer=%d cofferStep=%s foremanDueIn=%ds taxStep=%s "
                        + "bagFull=%s bagKnown=%s invCoal=%d bagChatAgeMs=%s lastFill=%s "
                        + "primaryEmpty=%s secondaryEmpty=%s energy=%d anim=%s move=%s tile=%s "
                        + "human=%s realInputAgoMs=%d lootAttempts=%d",
                state,
                smithPhase,
                tripMode,
                Microbot.status,
                Rs2Bank.isOpen(),
                getInventoryOreCount(),
                totalBarsInDispenser(),
                furnaceCoal,
                furnaceOreRemaining(),
                expectedBars,
                cofferCoins(),
                state == State.COFFER ? cofferStep : "-",
                foremanDueInSeconds(),
                state == State.TAX ? taxStep : "-",
                coalBagFull,
                coalBagKnown,
                Rs2Inventory.count(COAL),
                lastCoalBagChatMs > 0 ? String.valueOf(System.currentTimeMillis() - lastCoalBagChatMs) : "never",
                lastFillReason,
                primaryOreEmpty,
                secondaryOreEmpty,
                Microbot.getClient().getEnergy(),
                Rs2Player.isAnimating(),
                Rs2Player.isMoving(),
                tile != null ? tile.getX() + "," + tile.getY() + "," + tile.getPlane() : "?",
                userInputActive(),
                InputArbiter.millisSinceRealActivity(),
                lootAttempts);
    }

    @Inject
    public OpraBlastFurnaceScript(OpraBlastFurnacePlugin plugin, OpraBlastFurnaceConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean run() {
        Microbot.enableAutoRunOn = false;
        state = State.BANKING;
        smithPhase = SmithPhase.DEPOSIT;
        depositPass = 0;
        conveyorRetries = 0;
        fillRetries = 0;
        primaryOreEmpty = !Rs2Inventory.hasItem(config.getBars().getPrimaryOre());
        secondaryOreEmpty = !Rs2Inventory.hasItem(config.getBars().getSecondaryOre());
        coalBagFull = false;
        coalBagKnown = false;
        lastCoalBagChatMs = 0;
        emptyRetries = 0;
        foremanPaidAtMs = 0;
        foremanPermissionNeeded = false;
        foremanPermissionChatMs = 0;
        cofferCooldownUntilMs = 0;
        recoveryTimes.clear();
        lastProgressMs = 0;
        Rs2Antiban.resetAntibanSettings();
        applyAntiBanSettings();
        debug.mark(config, "run started");

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                debug.warnIfStalled(config, buildDebugSnapshot());

                if (!Microbot.isLoggedIn()) {
                    debug.logEarlyReturn(config, "not logged in");
                    lastProgressMs = System.currentTimeMillis();
                    return;
                }

                if (!super.run()) {
                    debug.logEarlyReturn(config, "super.run false");
                    lastProgressMs = System.currentTimeMillis();
                    return;
                }

                if (state != State.TAX && state != State.COFFER && Rs2Dialogue.hasContinue()) {
                    debug.mark(config, "popup: continue (text=" + dialogueSnapshot() + ")");
                    Rs2Dialogue.clickContinue();
                    sleep(300, 600);
                    return;
                }

                if (recoverIfStuck()) {
                    return;
                }

                if (!Rs2GameObject.exists(BLAST_FURNACE_DISPENSER)) {
                    lastProgressMs = System.currentTimeMillis();
                    debug.mark(config, "travel:blast furnace");
                    Microbot.status = "Walking to Blast Furnace";
                    if (Rs2Player.isAnimating() || Rs2Player.isMoving()) {
                        return;
                    }
                    Rs2Walker.walkTo(STAIRS_TILE);
                    Microbot.getRs2TileObjectCache().query().interact(DWARF_KELDAGRIM_FACTORY_STAIRS);
                    return;
                }

                if (!init) {
                    handleInitCoffer();
                    return;
                }

                switch (state) {
                    case BANKING:
                        handleBanking();
                        break;
                    case RETRIEVING:
                        handleRetrieving();
                        break;
                    case SMITHING:
                        handleSmithing();
                        break;
                    case TAX:
                        handleTax();
                        break;
                    case COFFER:
                        handleCoffer();
                        break;
                    default:
                        transitionState(State.BANKING);
                        break;
                }
            } catch (Exception ex) {
                if (config.debugLogging()) {
                    log.warn("[BF] Error in main loop: {}", ex.getMessage(), ex);
                } else {
                    log.trace("Error in main loop: {} - ", ex.getMessage(), ex);
                }
            }
        }, 0, 200, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleInitCoffer() {
        debug.mark(config, "init:coffer");
        init = true;
        transitionState(State.BANKING);
    }

    private void handleBanking() {
        debug.mark(config, "banking");
        Microbot.status = "Banking";

        if (!ensureBankOpen()) {
            return;
        }

        if (config.getBars().isRequiresCoalBag() && !Rs2Inventory.contains(coalBag)) {
            if (!Rs2Bank.hasItem(coalBag)) {
                Microbot.showMessage("No coal bag found in inventory and bank.");
                Microbot.stopPlugin(plugin);
                return;
            }
            debug.mark(config, "banking:withdraw coal bag");
            Rs2Bank.withdrawItem(coalBag);
            return;
        }

        if (config.getBars().isRequiresGoldsmithGloves()) {
            boolean hasGauntlets = Rs2Inventory.contains(GAUNTLETS_OF_GOLDSMITHING)
                    || Rs2Equipment.isWearing(GAUNTLETS_OF_GOLDSMITHING);
            if (!hasGauntlets) {
                if (!Rs2Bank.hasItem(GAUNTLETS_OF_GOLDSMITHING)) {
                    Microbot.showMessage("No goldsmith gauntlets found.");
                    Microbot.stopPlugin(plugin);
                    return;
                }
                debug.mark(config, "banking:withdraw goldsmith gauntlets");
                Rs2Bank.withdrawItem(GAUNTLETS_OF_GOLDSMITHING);
                return;
            }
        } else if (Rs2Inventory.contains(GAUNTLETS_OF_GOLDSMITHING)) {
            debug.mark(config, "banking:deposit goldsmith gauntlets");
            Rs2Bank.depositAll(GAUNTLETS_OF_GOLDSMITHING);
            return;
        }

        if (Rs2Inventory.hasItem("bar")) {
            debug.mark(config, "banking:deposit bars");
            Rs2Bank.depositAllExcept(coalBag, GAUNTLETS_OF_GOLDSMITHING, ICE_GLOVES, SMITHING_UNIFORM_GLOVES_ICE);
            return;
        }

        if (Rs2Inventory.hasItem(COINS)) {
            debug.mark(config, "banking:deposit coins");
            Rs2Bank.depositAll(COINS);
            return;
        }

        if (foremanFeeDue()) {
            startTax(State.BANKING);
            return;
        }

        if (needsCofferTopUp()) {
            startCofferTopUp();
            return;
        }

        if (!hasRequiredOresForSmithing()) {
            log.warn("[BF] Out of ores. Walking out for coffer safety");
            debug.mark(config, "banking:out of ores");
            Rs2Walker.walkTo(new WorldPoint(2930, 10196, 0));
            Rs2Player.logout();
            Microbot.stopPlugin(plugin);
            return;
        }

        if (Microbot.getClient().getEnergy() < 8100) {
            if (useStaminaPotions()) {
                debug.mark(config, "banking:drank energy potion");
                return;
            }
            debug.logDebug(config, "banking: low energy, no potions available, continuing");
        }

        if (dispenserContainsBars()) {
            debug.mark(config, "banking:dispenser has bars -> smithing loot");
            beginLootPhase();
            transitionState(State.SMITHING);
            return;
        }

        tripMode = chooseTripMode();
        debug.mark(config, "banking -> retrieving trip=" + tripMode);
        transitionState(State.RETRIEVING);
    }

    private void handleRetrieving() {
        debug.mark(config, "retrieve:" + tripMode.name().toLowerCase());
        Microbot.status = "Retrieving ores";

        if (inventoryReadyForDeposit()) {
            if (Rs2Bank.isOpen()) {
                debug.mark(config, "retrieve:close bank");
                Rs2Bank.closeBank();
                return;
            }
            debug.mark(config, "retrieve complete -> smithing deposit");
            beginDepositPhase();
            transitionState(State.SMITHING);
            return;
        }

        if (!ensureBankOpen()) {
            return;
        }

        switch (tripMode) {
            case GOLD:
                if (!Rs2Inventory.hasItem(GOLD_ORE)) {
                    debug.mark(config, "retrieve:withdraw gold ore");
                    Rs2Bank.withdrawAll(GOLD_ORE);
                    return;
                }
                break;
            case PRIMARY:
                if (!hasPrimaryOre()) {
                    debug.mark(config, "retrieve:withdraw primary ore");
                    Rs2Bank.withdrawAll(config.getBars().getPrimaryOre());
                    return;
                }
                break;
            case DOUBLE_COAL:
                if (!fillCoalBag()) {
                    return;
                }
                if (!withdrawLoad(COAL, "coal")) {
                    return;
                }
                break;
            case COAL_AND_PRIMARY:
                if (!fillCoalBag()) {
                    return;
                }
                if (!withdrawLoad(config.getBars().getPrimaryOre(), "primary ore")) {
                    return;
                }
                break;
            case COAL_AND_GOLD:
                if (!fillCoalBag()) {
                    return;
                }
                if (!withdrawLoad(GOLD_ORE, "gold ore")) {
                    return;
                }
                break;
            default:
                break;
        }

        if (!inventoryReadyForDeposit()) {
            debug.logDebug(config, "retrieve: inventory not ready yet");
            return;
        }

        if (Rs2Bank.isOpen()) {
            debug.mark(config, "retrieve:close bank");
            Rs2Bank.closeBank();
            return;
        }

        debug.mark(config, "retrieve complete -> smithing deposit");
        beginDepositPhase();
        transitionState(State.SMITHING);
    }

    private void handleSmithing() {
        switch (smithPhase) {
            case DEPOSIT:
                handleDepositPhase();
                break;
            case WAIT_BARS:
                handleWaitBarsPhase();
                break;
            case LOOT:
                handleLootPhase();
                break;
            default:
                beginDepositPhase();
                break;
        }
    }

    private void beginDepositPhase() {
        smithPhase = SmithPhase.DEPOSIT;
        lastProgressMs = System.currentTimeMillis();
        depositPass = 0;
        conveyorRetries = 0;
        int loadOre = Rs2Inventory.count(tripUsesGold() ? GOLD_ORE : config.getBars().getPrimaryOre());
        if (loadOre > 0) {
            expectedBars = loadOre;
        }
    }

    private void beginWaitBarsPhase() {
        smithPhase = SmithPhase.WAIT_BARS;
        conveyorRetries = 0;
        lastProgressMs = System.currentTimeMillis();
        waitBarsStartMs = System.currentTimeMillis();
        lastBarCount = totalBarsInDispenser();
        lastBarChangeMs = waitBarsStartMs;
        debug.mark(config, "oreRun:wait bars expected=" + expectedBars);
        Microbot.status = "Waiting for bars";
    }

    private boolean tripUsesGold() {
        return tripMode == TripMode.GOLD || tripMode == TripMode.COAL_AND_GOLD;
    }

    private int furnaceOreRemaining() {
        return Microbot.getVarbitValue(tripUsesGold() ? BLAST_FURNACE_GOLD_ORE : config.getBars().getBFPrimaryOreID());
    }

    private int totalBarsInDispenser() {
        return Arrays.stream(new int[]{
                BLAST_FURNACE_IRON_BARS,
                BLAST_FURNACE_STEEL_BARS,
                BLAST_FURNACE_GOLD_BARS,
                BLAST_FURNACE_MITHRIL_BARS,
                BLAST_FURNACE_ADAMANTITE_BARS,
                BLAST_FURNACE_RUNITE_BARS
        }).map(Microbot::getVarbitValue).sum();
    }

    private int barItemCount() {
        int count = Rs2Inventory.count(config.getBars().getBarID());
        if (config.getBars() != Bars.GOLD_BAR && (isHybrid(config.getBars()) || tripUsesGold())) {
            count += Rs2Inventory.count(ItemID.GOLD_BAR);
        }
        return count;
    }

    private void beginLootPhase() {
        smithPhase = SmithPhase.LOOT;
        lastProgressMs = System.currentTimeMillis();
        moltenWaitUntilMs = 0;
        lootEmptySinceMs = 0;
        lootAttempts = 0;
        lastTakeClickMs = 0;
        debug.mark(config, "dispenser:loot");
        Microbot.status = "Looting dispenser";
    }

    private void handleDepositPhase() {
        debug.mark(config, "deposit:ore pass=" + depositPass);
        Microbot.status = "Depositing ore";

        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            return;
        }

        if (getInventoryOreCount() <= 0) {
            if (config.getBars().isRequiresCoalBag() && coalBagFull) {
                emptyCoalBag();
                return;
            }
            if (tripMode == TripMode.DOUBLE_COAL) {
                debug.logDebug(config, "deposit: coal-only trip -> banking");
                transitionState(State.BANKING);
                return;
            }
            beginWaitBarsPhase();
            return;
        }

        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            return;
        }

        boolean ok = putOreOnConveyorBeltOnce();
        if (!ok) {
            conveyorRetries++;
            debug.logDebug(config, "conveyor failed retries={}", conveyorRetries);
            if (conveyorRetries > CONVEYOR_MAX_RETRIES) {
                log.warn("[BF] Conveyor failed after retries; returning to bank");
                transitionState(State.BANKING);
            }
            return;
        }

        conveyorRetries = 0;
        depositPass++;
    }

    private void handleWaitBarsPhase() {
        debug.mark(config, "oreRun:wait bars");
        Microbot.status = "Waiting for bars";

        if (getInventoryOreCount() > 0) {
            beginDepositPhase();
            return;
        }

        long now = System.currentTimeMillis();
        int bars = totalBarsInDispenser();
        if (bars != lastBarCount) {
            lastBarCount = bars;
            lastBarChangeMs = now;
        }
        int oreLeft = furnaceOreRemaining();

        if (bars > 0) {
            boolean smeltDone = oreLeft <= 0;
            boolean batchReady = expectedBars > 0 && bars >= expectedBars;
            boolean stable = now - lastBarChangeMs >= BAR_COUNT_STABLE_MS;
            if (smeltDone || batchReady || stable) {
                debug.mark(config, "wait bars: ready bars=" + bars + " oreLeft=" + oreLeft
                        + (smeltDone ? " (smelt done)" : batchReady ? " (batch ready)" : " (stable)"));
                beginLootPhase();
                return;
            }
        } else if (now - waitBarsStartMs > WAIT_BARS_ORE_GRACE_MS && oreLeft <= 0) {
            debug.logDebug(config, "wait bars: no primary ore in furnace -> banking");
            transitionState(State.BANKING);
            return;
        } else if (now - waitBarsStartMs > WAIT_BARS_MAX_MS) {
            log.warn("[BF] No bars after {}s with oreLeft={}; returning to bank", WAIT_BARS_MAX_MS / 1000, oreLeft);
            transitionState(State.BANKING);
            return;
        }

        WorldPoint here = Rs2Player.getWorldLocation();
        if (!Rs2Player.isMoving() && (here == null || here.distanceTo(DISPENSER_TILE) > 1)) {
            Rs2Walker.walkFastCanvas(DISPENSER_TILE);
        }
    }

    private void handleLootPhase() {
        debug.mark(config, "dispenser:loot");
        Microbot.status = "Looting dispenser";

        if (totalBarsInDispenser() <= 0) {
            long now = System.currentTimeMillis();
            if (lootEmptySinceMs == 0) {
                lootEmptySinceMs = now;
            }
            if (furnaceOreRemaining() > 0 && !Rs2Inventory.isFull()
                    && now - lootEmptySinceMs < LOOT_REMAINING_WAIT_MS) {
                Microbot.status = "Waiting for remaining bars";
                return;
            }
            debug.mark(config, "loot: dispenser empty -> banking");
            equipGoldSmithGauntlets();
            transitionState(State.BANKING);
            return;
        }

        lootEmptySinceMs = 0;

        if (Rs2Inventory.isFull()) {
            if (!ensureBankOpen()) {
                return;
            }
            Rs2Bank.depositAllExcept(coalBag, GAUNTLETS_OF_GOLDSMITHING, ICE_GLOVES, SMITHING_UNIFORM_GLOVES_ICE);
            return;
        }

        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            return;
        }

        if (System.currentTimeMillis() < moltenWaitUntilMs) {
            return;
        }

        if (!equipIceGloves()) {
            return;
        }

        final int dispenserBefore = totalBarsInDispenser();
        final int invBarsBefore = barItemCount();
        boolean recentClick = System.currentTimeMillis() - lastTakeClickMs < TAKE_MENU_TRUST_MS;
        inputInterrupted = false;

        if (pausedForUserInput()) {
            return;
        }

        if (!(recentClick && takeMenuOpen())) {
            if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
                return;
            }
            debug.mark(config, "loot:take dispenser=" + dispenserBefore + " attempt=" + (lootAttempts + 1));
            if (!Microbot.getRs2TileObjectCache().query().interact(BLAST_FURNACE_DISPENSER, "Take")) {
                inputInterrupted |= userInputActive();
                lootNoProgress("take interact failed");
                return;
            }
            lastTakeClickMs = System.currentTimeMillis();
            sleepUntil(() -> takeMenuOpen()
                    || Rs2Widget.hasWidget("still molten")
                    || barsMoved(invBarsBefore, dispenserBefore)
                    || userInputActive(), 5000);

            if (Rs2Widget.hasWidget("still molten")) {
                debug.mark(config, "loot:molten");
                moltenWaitUntilMs = System.currentTimeMillis() + 1200L;
                Rs2Dialogue.clickContinue();
                return;
            }
            if (pausedForUserInput()) {
                return;
            }
        }

        if (!barsMoved(invBarsBefore, dispenserBefore) && takeMenuOpen()) {
            for (int press = 1; press <= LOOT_SPACE_PRESSES; press++) {
                if (pausedForUserInput()) {
                    return;
                }
                debug.mark(config, "loot:menu open -> space " + press);
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                if (waitForBars(invBarsBefore, dispenserBefore, 1200) || !takeMenuOpen()) {
                    break;
                }
            }

            if (!barsMoved(invBarsBefore, dispenserBefore) && takeMenuOpen()) {
                if (pausedForUserInput()) {
                    return;
                }
                boolean clicked = clickTakeButton();
                debug.mark(config, "loot:space did nothing -> click take button (clicked=" + clicked + ")");
                if (clicked) {
                    waitForBars(invBarsBefore, dispenserBefore, 2000);
                }
            }

            if (!barsMoved(invBarsBefore, dispenserBefore)) {
                if (pausedForUserInput()) {
                    return;
                }
                debug.mark(config, "loot:take button did nothing -> escape");
                Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
                lastTakeClickMs = 0;
                lootNoProgress("menu confirm failed");
                return;
            }
        }

        if (barsMoved(invBarsBefore, dispenserBefore)) {
            sleepUntil(() -> barItemCount() > invBarsBefore, 1200);
            lootAttempts = 0;
            lastTakeClickMs = 0;
            debug.mark(config, "loot:got " + (barItemCount() - invBarsBefore) + " bars dispenserLeft="
                    + totalBarsInDispenser() + " oreLeft=" + furnaceOreRemaining());
            return;
        }

        lastTakeClickMs = 0;
        lootNoProgress("no menu (dialogue=" + dialogueSnapshot() + ")");
    }

    private boolean barsMoved(int invBarsBefore, int dispenserBefore) {
        return barItemCount() > invBarsBefore || totalBarsInDispenser() < dispenserBefore;
    }

    private boolean userInputActive() {
        return InputArbiter.isHuman();
    }

    private boolean pausedForUserInput() {
        if (!userInputActive()) {
            return false;
        }
        inputInterrupted = true;
        Microbot.status = "Paused: mouse/keyboard in use";
        debug.mark(config, "loot:paused user input (" + InputArbiter.millisSinceRealActivity() + "ms ago)");
        return true;
    }

    private boolean waitForBars(int invBarsBefore, int dispenserBefore, int timeoutMs) {
        sleepUntil(() -> barsMoved(invBarsBefore, dispenserBefore) || userInputActive(), timeoutMs);
        if (userInputActive()) {
            inputInterrupted = true;
        }
        return barsMoved(invBarsBefore, dispenserBefore);
    }

    private boolean clickTakeButton() {
        String barName = barDisplayName().toLowerCase();
        Widget fallback = null;
        for (int child = 13; child <= 32; child++) {
            Widget w = Rs2Widget.getWidget(PRODUCTION_GROUP, child);
            if (w == null || w.isHidden() || w.getActions() == null) {
                continue;
            }
            boolean hasTake = Arrays.stream(w.getActions()).anyMatch(a -> a != null && a.equalsIgnoreCase("Take"));
            if (!hasTake) {
                continue;
            }
            String name = w.getName() == null ? "" : w.getName().toLowerCase();
            if (name.contains(barName)) {
                return Rs2Widget.clickWidget(w);
            }
            if (fallback == null) {
                fallback = w;
            }
        }
        return fallback != null && Rs2Widget.clickWidget(fallback);
    }

    private String barDisplayName() {
        String name = config.getBars().name().replace("HYBRID_", "").replace('_', ' ').toLowerCase();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private void lootNoProgress(String reason) {
        boolean human = inputInterrupted || userInputActive();
        moltenWaitUntilMs = System.currentTimeMillis() + 600L;
        if (human) {
            debug.mark(config, "loot:" + reason + " human=true (not counted)");
            return;
        }
        lootAttempts++;
        debug.mark(config, "loot:" + reason + " human=false attempt=" + lootAttempts);
        if (lootAttempts >= LOOT_MAX_ATTEMPTS) {
            log.warn("[BF] Dispenser take failed {} times; resetting via bank", lootAttempts);
            lootAttempts = 0;
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            transitionState(State.BANKING);
        }
    }

    private boolean takeMenuOpen() {
        return Rs2Widget.isProductionWidgetOpen()
                || Rs2Widget.hasWidget("How many would you like to take")
                || Rs2Widget.hasWidget("How many bars")
                || Rs2Widget.hasWidget("would you like to take");
    }

    private TripMode chooseTripMode() {
        Bars bar = config.getBars();
        int coal = Microbot.getVarbitValue(
                bar == Bars.GOLD_BAR ? BLAST_FURNACE_GOLD_ORE : BLAST_FURNACE_COAL);
        int divisor = isHybrid(bar) ? MAX_ORE_PER_HYBRID_INTERACTION : MAX_ORE_PER_INTERACTION;
        int batch = coal / divisor;
        debug.logDebug(config, "dispatch bar={} furnaceCoal={} batch={}", bar.name(), coal, batch);

        switch (bar) {
            case GOLD_BAR:
                return TripMode.GOLD;
            case STEEL_BAR:
            case MITHRIL_BAR:
                return batch <= 0 ? TripMode.DOUBLE_COAL
                        : batch <= 6 ? TripMode.COAL_AND_PRIMARY
                        : TripMode.PRIMARY;
            case ADAMANTITE_BAR:
            case RUNITE_BAR:
                return batch <= 2 ? TripMode.DOUBLE_COAL
                        : batch <= 6 ? TripMode.COAL_AND_PRIMARY
                        : TripMode.PRIMARY;
            case HYBRID_MITHRIL_BAR:
                return batch <= 0 ? TripMode.COAL_AND_GOLD : TripMode.COAL_AND_PRIMARY;
            case HYBRID_ADAMANTITE_BAR:
                return batch <= 2 ? TripMode.COAL_AND_GOLD : TripMode.COAL_AND_PRIMARY;
            case HYBRID_RUNITE_BAR:
                return batch <= 3 ? TripMode.COAL_AND_GOLD : TripMode.COAL_AND_PRIMARY;
            default:
                return TripMode.COAL_AND_PRIMARY;
        }
    }

    private boolean inventoryReadyForDeposit() {
        switch (tripMode) {
            case GOLD:
                return Rs2Inventory.hasItem(GOLD_ORE);
            case PRIMARY:
                return hasPrimaryOre();
            case DOUBLE_COAL:
                return coalBagFull && Rs2Inventory.hasItem(COAL);
            case COAL_AND_PRIMARY:
                return coalBagFull && hasPrimaryOre();
            case COAL_AND_GOLD:
                return coalBagFull && Rs2Inventory.hasItem(GOLD_ORE);
            default:
                return getInventoryOreCount() > 0;
        }
    }

    private boolean hasPrimaryOre() {
        return Rs2Inventory.hasItem(config.getBars().getPrimaryOre());
    }

    private boolean fillCoalBag() {
        if (!config.getBars().isRequiresCoalBag()) {
            return true;
        }
        if (!Rs2Inventory.contains(coalBag)) {
            debug.logEarlyReturn(config, "retrieve: missing coal bag");
            transitionState(State.BANKING);
            return false;
        }
        if (coalBagFull) {
            fillRetries = 0;
            return true;
        }

        int bankCoalBefore = Rs2Bank.count(COAL);
        int invCoalBefore = Rs2Inventory.count(COAL);
        if (bankCoalBefore <= 0 && invCoalBefore <= 0) {
            log.warn("[BF] Out of coal in bank; cannot fill coal bag");
            transitionState(State.BANKING);
            return false;
        }

        long chatBefore = lastCoalBagChatMs;
        debug.mark(config, "retrieve:fill coal bag");
        if (!Rs2Inventory.interact(coalBag, "Fill")) {
            fillRetries++;
            debug.logEarlyReturn(config, "retrieve: fill coal bag interact failed");
            if (fillRetries > FILL_MAX_RETRIES) {
                log.warn("[BF] Coal bag Fill interaction failed after {} retries", fillRetries);
                fillRetries = 0;
                transitionState(State.BANKING);
            }
            return false;
        }

        sleepUntil(() -> lastCoalBagChatMs != chatBefore
                || Rs2Inventory.count(COAL) != invCoalBefore
                || Rs2Bank.count(COAL) < bankCoalBefore, 3000);

        if (lastCoalBagChatMs != chatBefore) {
            lastFillReason = "chat";
            if (!coalBagFull) {
                fillRetries++;
                debug.logDebug(config, "fill result: chat reports bag not full, retries={}", fillRetries);
                if (fillRetries > FILL_MAX_RETRIES) {
                    log.warn("[BF] Coal bag still not full after {} Fill attempts", fillRetries);
                    fillRetries = 0;
                    transitionState(State.BANKING);
                }
                return false;
            }
        } else if (Rs2Inventory.count(COAL) < invCoalBefore) {
            lastFillReason = "inventory change";
        } else if (Rs2Bank.count(COAL) < bankCoalBefore) {
            lastFillReason = "bank change";
        } else {
            lastFillReason = "assumed full";
        }

        coalBagFull = true;
        coalBagKnown = true;
        fillRetries = 0;
        debug.logDebug(config, "fill result: full via {} invCoal={} bankCoal={}",
                lastFillReason, Rs2Inventory.count(COAL), Rs2Bank.count(COAL));
        debug.mark(config, "retrieve:coal bag ready");
        return true;
    }

    private boolean withdrawLoad(int itemId, String label) {
        if (Rs2Inventory.hasItem(itemId)) {
            return true;
        }
        if (itemId != COAL && Rs2Inventory.hasItem(COAL)) {
            debug.mark(config, "retrieve:deposit stray coal");
            Rs2Bank.depositAll(COAL);
            return false;
        }
        if (Rs2Inventory.isFull()) {
            debug.mark(config, "retrieve:deposit stray items");
            Rs2Bank.depositAllExcept(coalBag, GAUNTLETS_OF_GOLDSMITHING, ICE_GLOVES, SMITHING_UNIFORM_GLOVES_ICE);
            return false;
        }
        if (!Rs2Bank.hasItem(itemId)) {
            log.warn("[BF] Bank has no {} left", label);
            transitionState(State.BANKING);
            return false;
        }
        debug.mark(config, "retrieve:withdraw " + label);
        Rs2Bank.withdrawAll(itemId);
        sleepUntil(() -> Rs2Inventory.hasItem(itemId), 2000);
        return Rs2Inventory.hasItem(itemId);
    }

    private boolean emptyCoalBag() {
        if (!Rs2Inventory.contains(coalBag) || !coalBagFull) {
            return true;
        }
        int invCoalBefore = Rs2Inventory.count(COAL);
        long chatBefore = lastCoalBagChatMs;
        debug.mark(config, "deposit:empty coal bag");
        if (Rs2Inventory.interact(coalBag, "Empty")) {
            sleepUntil(() -> Rs2Inventory.count(COAL) > invCoalBefore
                    || (lastCoalBagChatMs != chatBefore && !coalBagFull), 3000);
            if (Rs2Inventory.count(COAL) > invCoalBefore || !coalBagFull) {
                coalBagFull = false;
                coalBagKnown = true;
                emptyRetries = 0;
                return true;
            }
        }
        emptyRetries++;
        debug.logDebug(config, "deposit: empty coal bag unconfirmed, retries={}", emptyRetries);
        if (emptyRetries > FILL_MAX_RETRIES) {
            log.warn("[BF] Coal bag Empty unconfirmed after {} retries; returning to bank", emptyRetries);
            emptyRetries = 0;
            coalBagFull = false;
            coalBagKnown = false;
            transitionState(State.BANKING);
        }
        return false;
    }

    private boolean equipIceGloves() {
        if (Rs2Equipment.isWearing(ICE_GLOVES) || Rs2Equipment.isWearing(SMITHING_UNIFORM_GLOVES_ICE)) {
            return true;
        }
        boolean equipped = Rs2Inventory.interact(ICE_GLOVES, "Wear")
                || Rs2Inventory.interact(SMITHING_UNIFORM_GLOVES_ICE, "Wear");
        if (!equipped) {
            Microbot.showMessage("Ice gloves or smith gloves required to loot the hot bars.");
            Rs2Player.logout();
            Microbot.stopPlugin(plugin);
            return false;
        }
        return true;
    }

    private boolean ensureBankOpen() {
        if (Rs2Bank.isOpen()) {
            return true;
        }
        if (System.currentTimeMillis() < bankOpenBackoffUntilMs) {
            return false;
        }
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            return false;
        }
        debug.mark(config, "wait:bank open");
        Rs2Bank.openBank();
        sleepUntil(Rs2Bank::isOpen, 5000);
        if (Rs2Bank.isOpen()) {
            return true;
        }
        bankOpenBackoffUntilMs = System.currentTimeMillis() + 800L;
        debug.logEarlyReturn(config, "bank failed to open");
        Microbot.status = "Waiting for bank";
        return false;
    }

    private boolean putOreOnConveyorBeltOnce() {
        debug.mark(config, "conveyor:put ore");
        final int oreCount = getInventoryOreCount();
        if (oreCount <= 0) {
            debug.logEarlyReturn(config, "conveyor:no ore");
            return false;
        }
        debug.logDebug(config, "conveyor: oreCount before={}", oreCount);

        if (!Microbot.getRs2TileObjectCache().query().interact(BLAST_FURNACE_CONVEYER_BELT_CLICKABLE, "Put-ore-on")) {
            debug.logEarlyReturn(config, "conveyor:interact failed");
            return false;
        }

        sleepUntil(() -> Rs2Dialogue.isInDialogue()
                || getInventoryOreCount() < oreCount
                || Rs2Widget.hasWidget("You must ask the foreman's"), 8000);

        if (Rs2Widget.hasWidget("You must ask the foreman's")
                || (Rs2Dialogue.isInDialogue() && Rs2Dialogue.hasDialogueOption("Pay", false))) {
            debug.mark(config, "conveyor: foreman permission required");
            if (foremanPaidAtMs != 0 && System.currentTimeMillis() - foremanPaidAtMs < 60_000L) {
                return false;
            }
            foremanPermissionNeeded = true;
            startTax(State.SMITHING);
            return false;
        }

        int oreAfter = getInventoryOreCount();
        debug.logDebug(config, "conveyor: oreCount after={}", oreAfter);
        return oreAfter < oreCount;
    }

    private void transitionState(State newState) {
        if (state == newState) {
            return;
        }
        debug.logDebug(config, "state {} -> {}", state, newState);
        if (config.debugLogging()) {
            Microbot.log(OpraBlastFurnaceDebug.PREFIX + "state " + state + " -> " + newState);
        }
        state = newState;
        lastProgressMs = System.currentTimeMillis();
    }

    private boolean recoverIfStuck() {
        long now = System.currentTimeMillis();
        if (lastProgressMs == 0 || userInputActive()) {
            lastProgressMs = now;
            return false;
        }
        boolean frozen = debug.getIdleSeconds() >= WATCHDOG_IDLE_SECONDS
                && now - lastProgressMs >= WATCHDOG_IDLE_SECONDS * 1000L;
        boolean looping = now - lastProgressMs >= WATCHDOG_LOOP_MS;
        if (!frozen && !looping) {
            return false;
        }

        String step = debug.getContext();
        log.warn("[BF] WATCHDOG recover from {} (state={} phase={} idle={}s noProgress={}s)",
                step, state, smithPhase, debug.getIdleSeconds(), (now - lastProgressMs) / 1000);
        Microbot.log("[BF] WATCHDOG recover from " + step);

        while (!recoveryTimes.isEmpty() && now - recoveryTimes.peekFirst() > WATCHDOG_WINDOW_MS) {
            recoveryTimes.pollFirst();
        }
        recoveryTimes.addLast(now);
        if (recoveryTimes.size() >= WATCHDOG_MAX_RECOVERIES) {
            log.warn("[BF] {} watchdog recoveries in 5 minutes; stopping", recoveryTimes.size());
            Microbot.showMessage("Blast Furnace stuck, stopped for safety");
            Rs2Player.logout();
            Microbot.stopPlugin(plugin);
            return true;
        }

        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
        }
        smithPhase = SmithPhase.DEPOSIT;
        depositPass = 0;
        conveyorRetries = 0;
        fillRetries = 0;
        emptyRetries = 0;
        lootAttempts = 0;
        lastTakeClickMs = 0;
        moltenWaitUntilMs = 0;
        cofferStep = CofferStep.WITHDRAW;
        cofferRetries = 0;
        cofferWithdrawIssued = false;
        taxStep = TaxStep.WITHDRAW;
        taxRetries = 0;
        state = State.BANKING;
        lastProgressMs = now;
        debug.mark(config, "watchdog:recovered -> banking");
        return true;
    }

    private boolean hasRequiredOresForSmithing() {
        int primaryOre = config.getBars().getPrimaryOre();
        int secondaryOre = config.getBars().getSecondaryOre() == null ? -1 : config.getBars().getSecondaryOre();
        boolean hasPrimaryOre = Rs2Bank.hasItem(primaryOre);
        boolean hasSecondaryOre = secondaryOre == -1 || Rs2Bank.hasItem(secondaryOre);
        return hasPrimaryOre && hasSecondaryOre;
    }

    private boolean isHybrid(Bars bar) {
        return bar.name().startsWith("HYBRID");
    }

    private boolean useStaminaPotions() {
        if (!Rs2Bank.isOpen()) {
            return false;
        }

        boolean hasStaminaPotion = Rs2Bank.hasItem(Rs2Potion.getStaminaPotion());
        boolean hasEnergyPotion = Rs2Bank.hasItem(Rs2Potion.getRestoreEnergyPotionsVariants());

        if (!Rs2Player.hasStaminaBuffActive() && hasStaminaPotion) {
            String potionName = getLowestDosePotionName(List.of(Rs2Potion.getStaminaPotion()));
            if (potionName != null) {
                return withdrawAndDrink(potionName);
            }
        }
        if (hasEnergyPotion) {
            String potionName = getLowestDosePotionName(Rs2Potion.getRestoreEnergyPotionsVariants());
            if (potionName != null) {
                return withdrawAndDrink(potionName);
            }
        }
        return false;
    }

    private String getLowestDosePotionName(List<String> variants) {
        return Rs2Bank.getAll(item -> variants.stream().anyMatch(variant ->
                        item.getName().toLowerCase().contains(variant.toLowerCase())))
                .min(Comparator.comparingInt(item -> getDoseFromName(item.getName())))
                .map(Rs2ItemModel::getName)
                .orElse(null);
    }

    private int getDoseFromName(String potionItemName) {
        Matcher matcher = ITEM_NAME_SUFFIX_PATTERN.matcher(potionItemName);
        if (matcher.find() && matcher.group(2) != null) {
            return Integer.parseInt(matcher.group(2));
        }
        return 0;
    }

    private String getBaseName(String itemName) {
        Matcher matcher = ITEM_NAME_SUFFIX_PATTERN.matcher(itemName);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return itemName;
    }

    private boolean withdrawAndDrink(String potionItemName) {
        String baseName = getBaseName(potionItemName);
        boolean withdrewPotion = Rs2Bank.withdrawOne(potionItemName);
        if (!withdrewPotion) {
            if (Rs2Inventory.isFull()) {
                if (!Rs2Inventory.waitForInventoryChanges(600) && Rs2Inventory.isFull()) {
                    debug.logEarlyReturn(config, "stamina:inventory full for " + potionItemName);
                    return false;
                }
                withdrewPotion = Rs2Bank.withdrawOne(potionItemName);
            }
            if (!withdrewPotion) {
                debug.logEarlyReturn(config, "stamina:withdraw failed " + potionItemName);
                return false;
            }
        }
        Rs2Inventory.waitForInventoryChanges(1800);
        Rs2Inventory.interact(potionItemName, "drink");
        Rs2Inventory.waitForInventoryChanges(1800);
        if (Rs2Inventory.hasItem(baseName)) {
            Rs2Bank.depositOne(baseName);
            Rs2Inventory.waitForInventoryChanges(1800);
        }
        if (Rs2Inventory.hasItem(ItemID.VIAL_EMPTY)) {
            Rs2Bank.depositOne(ItemID.VIAL_EMPTY);
            Rs2Inventory.waitForInventoryChanges(1800);
        }
        return true;
    }

    private int getInventoryOreCount() {
        Set<Integer> ids = new HashSet<>(Arrays.asList(COAL, GOLD_ORE, config.getBars().getPrimaryOre()));
        if (config.getBars().getSecondaryOre() != null) {
            ids.add(config.getBars().getSecondaryOre());
        }
        int total = 0;
        for (int id : ids) {
            total += Rs2Inventory.count(id);
        }
        return total;
    }

    public int barsInDispenser(Bars bar) {
        switch (bar) {
            case GOLD_BAR:
                return getBars(BLAST_FURNACE_GOLD_BARS);
            case STEEL_BAR:
                return getBars(BLAST_FURNACE_STEEL_BARS);
            case MITHRIL_BAR:
                return getBars(BLAST_FURNACE_MITHRIL_BARS);
            case ADAMANTITE_BAR:
                return getBars(BLAST_FURNACE_ADAMANTITE_BARS);
            case RUNITE_BAR:
                return getBars(BLAST_FURNACE_RUNITE_BARS);
            case HYBRID_MITHRIL_BAR:
                return fallbackBar(BLAST_FURNACE_MITHRIL_BARS);
            case HYBRID_ADAMANTITE_BAR:
                return fallbackBar(BLAST_FURNACE_ADAMANTITE_BARS);
            case HYBRID_RUNITE_BAR:
                return fallbackBar(BLAST_FURNACE_RUNITE_BARS);
            default:
                return -1;
        }
    }

    private int fallbackBar(int primary) {
        int value = Microbot.getVarbitValue(primary);
        return value > 0 ? value : Microbot.getVarbitValue(BLAST_FURNACE_GOLD_BARS);
    }

    private int getBars(int varbitId) {
        return Microbot.getVarbitValue(varbitId);
    }

    public boolean dispenserContainsBars() {
        return Arrays.stream(new int[]{
                BLAST_FURNACE_IRON_BARS,
                BLAST_FURNACE_STEEL_BARS,
                BLAST_FURNACE_GOLD_BARS,
                BLAST_FURNACE_MITHRIL_BARS,
                BLAST_FURNACE_ADAMANTITE_BARS,
                BLAST_FURNACE_RUNITE_BARS
        }).anyMatch(id -> Microbot.getVarbitValue(id) > 0);
    }

    private void equipGoldSmithGauntlets() {
        if (config.getBars().isRequiresGoldsmithGloves()) {
            Rs2Inventory.interact(GAUNTLETS_OF_GOLDSMITHING, "Wear");
        }
    }

    private void applyAntiBanSettings() {
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.devDebug = true;
    }

    public void shutdown() {
        debug.reset();
        init = false;
        state = State.BANKING;
        smithPhase = SmithPhase.DEPOSIT;
        primaryOreEmpty = false;
        secondaryOreEmpty = false;
        super.shutdown();
    }

    int cofferCoins() {
        return Microbot.getVarbitValue(BLAST_FURNACE_COFFER);
    }

    private boolean needsCofferTopUp() {
        if (System.currentTimeMillis() < cofferCooldownUntilMs) {
            return false;
        }
        return config.cofferTopUpAmount() > 0 && cofferCoins() < config.cofferTopUpAt();
    }

    private void startCofferTopUp() {
        cofferDeposit = config.cofferTopUpAmount();
        cofferWithdrawIssued = false;
        cofferStep = CofferStep.WITHDRAW;
        cofferRetries = 0;
        debug.mark(config, "coffer: top up coffer=" + cofferCoins() + " below " + config.cofferTopUpAt()
                + ", depositing " + cofferDeposit);
        transitionState(State.COFFER);
    }

    private String dialogueSnapshot() {
        try {
            String text = Rs2Dialogue.getDialogueText();
            return text == null || text.isBlank() ? "-" : text.replaceAll("<[^>]*>", "").trim();
        } catch (Exception ex) {
            return "?";
        }
    }

    private void cofferRetry(String reason) {
        cofferRetries++;
        debug.mark(config, "coffer:" + cofferStep.name().toLowerCase() + " retry " + cofferRetries + " (" + reason + ")");
        if (cofferRetries > COFFER_MAX_RETRIES) {
            log.warn("[BF] Coffer top up failed at {} ({}); retrying in 60s", cofferStep, reason);
            cofferRetries = 0;
            cofferCooldownUntilMs = System.currentTimeMillis() + 60_000L;
            transitionState(State.BANKING);
        }
    }

    private void handleCoffer() {
        Microbot.status = "Restocking coffer";
        switch (cofferStep) {
            case WITHDRAW: {
                debug.mark(config, "coffer:withdraw");
                int invCoins = Rs2Inventory.itemQuantity(COINS);
                if (invCoins >= cofferDeposit) {
                    cofferWithdrawIssued = false;
                    cofferStep = CofferStep.USE;
                    return;
                }
                if (!ensureBankOpen()) {
                    return;
                }
                if (Rs2Inventory.isFull()) {
                    Rs2Bank.depositAllExcept(coalBag, GAUNTLETS_OF_GOLDSMITHING, ICE_GLOVES, SMITHING_UNIFORM_GLOVES_ICE);
                    return;
                }
                int available = Rs2Bank.count(COINS) + invCoins;
                if (available <= 0) {
                    Microbot.showMessage("Out of coins for coffer");
                    Microbot.stopPlugin(plugin);
                    return;
                }
                cofferDeposit = Math.min(cofferDeposit, available);
                final int wanted = cofferDeposit;
                if (!cofferWithdrawIssued) {
                    coinsBeforeWithdraw = invCoins;
                    debug.mark(config, "coffer:withdraw " + (wanted - invCoins) + " coins");
                    Rs2Bank.withdrawX(COINS, wanted - invCoins);
                    cofferWithdrawIssued = true;
                }
                sleepUntil(() -> Rs2Inventory.itemQuantity(COINS) >= wanted, 3000);
                int nowCoins = Rs2Inventory.itemQuantity(COINS);
                if (nowCoins >= wanted) {
                    cofferWithdrawIssued = false;
                    cofferStep = CofferStep.USE;
                } else if (nowCoins > coinsBeforeWithdraw) {
                    debug.mark(config, "coffer: partial withdraw " + nowCoins + "/" + wanted + ", depositing what we have");
                    cofferDeposit = nowCoins;
                    cofferWithdrawIssued = false;
                    cofferStep = CofferStep.USE;
                } else {
                    cofferWithdrawIssued = false;
                    cofferRetry("coins not withdrawn");
                }
                return;
            }
            case USE: {
                debug.mark(config, "coffer:use");
                if (Rs2Bank.isOpen()) {
                    Rs2Bank.closeBank();
                    return;
                }
                if (Rs2Dialogue.hasDialogueOption("deposit", false)) {
                    cofferStep = CofferStep.DEPOSIT;
                    return;
                }
                if (Rs2Player.isMoving()) {
                    return;
                }
                final long permissionBefore = foremanPermissionChatMs;
                if (!Microbot.getRs2TileObjectCache().query().interact(BLAST_FURNACE_AUTOMATA_COFFER, "use")) {
                    cofferRetry("coffer interact failed");
                    return;
                }
                sleepUntil(() -> Rs2Dialogue.hasDialogueOption("deposit", false)
                        || foremanPermissionChatMs != permissionBefore, 6000);
                if (foremanPermissionChatMs != permissionBefore) {
                    debug.mark(config, "coffer: foreman permission needed -> tax");
                    startTax(State.COFFER);
                    return;
                }
                if (Rs2Dialogue.hasDialogueOption("deposit", false)) {
                    cofferStep = CofferStep.DEPOSIT;
                } else {
                    cofferRetry("no deposit option, dialogue=" + dialogueSnapshot());
                }
                return;
            }
            case DEPOSIT: {
                int amount = Math.min(cofferDeposit, Rs2Inventory.itemQuantity(COINS));
                if (amount <= 0) {
                    debug.mark(config, "coffer:deposit no coins in inventory -> withdraw");
                    cofferStep = CofferStep.WITHDRAW;
                    return;
                }
                debug.mark(config, "coffer:deposit " + amount);
                cofferBefore = cofferCoins();
                coinsBeforeDeposit = Rs2Inventory.itemQuantity(COINS);
                if (!Rs2Dialogue.keyPressForDialogueOption("Deposit", false)) {
                    Rs2Dialogue.clickOption("Deposit", false);
                }
                if (!sleepUntil(this::amountPromptVisible, 3000)) {
                    cofferStep = CofferStep.USE;
                    cofferRetry("no amount prompt, dialogue=" + dialogueSnapshot());
                    return;
                }
                Rs2Keyboard.typeString(String.valueOf(amount));
                Rs2Keyboard.enter();
                cofferStep = CofferStep.VERIFY;
                return;
            }
            case VERIFY: {
                debug.mark(config, "coffer:verify");
                if (sleepUntil(() -> cofferCoins() > cofferBefore
                        && Rs2Inventory.itemQuantity(COINS) < coinsBeforeDeposit, 4000)) {
                    debug.mark(config, "coffer: topped up to " + cofferCoins());
                    cofferRetries = 0;
                    transitionState(State.BANKING);
                } else {
                    cofferStep = CofferStep.USE;
                    cofferRetry("coffer value unchanged, dialogue=" + dialogueSnapshot());
                }
                return;
            }
            default:
                cofferStep = CofferStep.WITHDRAW;
        }
    }

    private boolean amountPromptVisible() {
        return Rs2Widget.hasWidget("how much")
                || Rs2Widget.hasWidget("Enter amount")
                || Rs2Dialogue.hasQuestion("how much", false);
    }

    private boolean foremanFeeDue() {
        if (Microbot.getClient().getRealSkillLevel(Skill.SMITHING) >= 60) {
            return false;
        }
        if (foremanPermissionNeeded || foremanPaidAtMs == 0) {
            return true;
        }
        return System.currentTimeMillis() - foremanPaidAtMs >= FOREMAN_REPAY_MS;
    }

    private long foremanDueInSeconds() {
        if (foremanPaidAtMs == 0) {
            return 0;
        }
        return Math.max(0, (FOREMAN_REPAY_MS - (System.currentTimeMillis() - foremanPaidAtMs)) / 1000);
    }

    String foremanStatus() {
        if (Microbot.getClient().getRealSkillLevel(Skill.SMITHING) >= 60) {
            return null;
        }
        if (foremanPaidAtMs == 0) {
            return "not paid";
        }
        return "paid " + ((System.currentTimeMillis() - foremanPaidAtMs) / 60_000) + "m ago";
    }

    private void startTax(State resume) {
        resumeAfterTax = resume;
        taxStep = TaxStep.WITHDRAW;
        taxRetries = 0;
        debug.mark(config, "tax: foreman fee due, resume=" + resume);
        transitionState(State.TAX);
    }

    private void taxRetry(String reason) {
        taxRetries++;
        debug.mark(config, "tax:" + taxStep.name().toLowerCase() + " retry " + taxRetries + " (" + reason + ")");
        if (taxRetries > COFFER_MAX_RETRIES) {
            log.warn("[BF] Could not pay the Blast Furnace foreman ({})", reason);
            Microbot.showMessage("Could not pay the Blast Furnace foreman");
            Microbot.stopPlugin(plugin);
        }
    }

    private void handleTax() {
        Microbot.status = "Paying foreman";
        switch (taxStep) {
            case WITHDRAW: {
                debug.mark(config, "tax:withdraw");
                int have = Rs2Inventory.itemQuantity(COINS);
                if (have >= FOREMAN_FEE) {
                    taxStep = TaxStep.PAY;
                    return;
                }
                if (!ensureBankOpen()) {
                    return;
                }
                if (Rs2Bank.count(COINS) + have < FOREMAN_FEE) {
                    Microbot.showMessage("Not enough coins to pay the Blast Furnace foreman");
                    Microbot.stopPlugin(plugin);
                    return;
                }
                if (have == 0 && Rs2Inventory.isFull()) {
                    if (Rs2Inventory.hasItem(config.getBars().getPrimaryOre())) {
                        Rs2Bank.depositOne(config.getBars().getPrimaryOre());
                    } else if (Rs2Inventory.hasItem(COAL)) {
                        Rs2Bank.depositOne(COAL);
                    } else {
                        Rs2Bank.depositAllExcept(coalBag, GAUNTLETS_OF_GOLDSMITHING, ICE_GLOVES, SMITHING_UNIFORM_GLOVES_ICE);
                    }
                    return;
                }
                Rs2Bank.withdrawX(COINS, FOREMAN_FEE - have);
                if (sleepUntil(() -> Rs2Inventory.itemQuantity(COINS) >= FOREMAN_FEE, 3000)) {
                    taxStep = TaxStep.PAY;
                } else {
                    taxRetry("coins not withdrawn");
                }
                return;
            }
            case PAY: {
                debug.mark(config, "tax:pay");
                if (Rs2Bank.isOpen()) {
                    Rs2Bank.closeBank();
                    return;
                }
                if (Rs2Player.isMoving()) {
                    return;
                }
                var foreman = Microbot.getRs2NpcCache().query().withName("Blast Furnace Foreman").nearestOnClientThread();
                if (foreman == null) {
                    taxRetry("foreman not found");
                    return;
                }
                taxCoinsBefore = Rs2Inventory.itemQuantity(COINS);
                foreman.click("Pay");
                sleepUntil(Rs2Dialogue::isInDialogue, 8000);
                debug.mark(config, "tax:dialogue " + dialogueSnapshot());
                if (Rs2Dialogue.hasDialogueOption("Yes", false)) {
                    if (!Rs2Dialogue.keyPressForDialogueOption("Yes", false)) {
                        Rs2Dialogue.clickOption("Yes", false);
                    }
                }
                taxStep = TaxStep.VERIFY;
                return;
            }
            case VERIFY: {
                debug.mark(config, "tax:verify");
                final int before = taxCoinsBefore;
                boolean paid = false;
                for (int i = 0; i < 3 && !paid; i++) {
                    paid = sleepUntil(() -> Rs2Inventory.itemQuantity(COINS) <= before - FOREMAN_FEE, 1500);
                    if (!paid && Rs2Dialogue.hasContinue()) {
                        Rs2Dialogue.clickContinue();
                    }
                }
                String text = dialogueSnapshot();
                if (!paid && text.toLowerCase().contains("already")) {
                    debug.mark(config, "tax: foreman says already paid");
                    paid = true;
                }
                if (!paid) {
                    taxStep = TaxStep.PAY;
                    taxRetry("coins not spent, dialogue=" + text);
                    return;
                }
                for (int i = 0; i < 5 && Rs2Dialogue.hasContinue(); i++) {
                    Rs2Dialogue.clickContinue();
                    sleep(300, 600);
                }
                foremanPaidAtMs = System.currentTimeMillis();
                foremanPermissionNeeded = false;
                debug.mark(config, "tax: foreman paid");
                if (resumeAfterTax == State.SMITHING) {
                    smithPhase = SmithPhase.DEPOSIT;
                    conveyorRetries = 0;
                }
                transitionState(resumeAfterTax);
                return;
            }
            default:
                taxStep = TaxStep.WITHDRAW;
        }
    }
}
