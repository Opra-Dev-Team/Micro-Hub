package net.runelite.client.plugins.microbot.oprahousethieving;

import java.awt.Rectangle;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.statemachine.StateMachineScript;
import net.runelite.client.plugins.microbot.statemachine.Transition;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import static net.runelite.client.plugins.microbot.oprahousethieving.OpraHouseThievingConstants.*;

@Slf4j
public class OpraHouseThievingScript extends StateMachineScript<OpraHouseThievingScript.State> {
    private static final long SEARCH_STUCK_MS = 70_000;

    private final OpraHouseThievingPlugin plugin;

    public OpraHouseThievingScript(final OpraHouseThievingPlugin plugin) {
        this.plugin = plugin;
    }

    enum State {
        PICKPOCKETING,
        FINDING_HOUSE,
        THIEVING_HOUSES,
        BANKING
    }

    private OpraHouseThievingConfig config;
    private String lastReason;
    private ThievingHouse currentThievingHouse = null;
    private Rs2NpcModel pickpocketNpc = null;
    private boolean clickedThisDistraction = false;
    // Furniture we last searched, and the piece the game told us is emptied ("nothing else worth taking").
    // We skip the emptied piece so we switch to another instead of spam-clicking it; it refills while we loot elsewhere.
    private volatile WorldPoint lastSearchedValuables = null;
    private volatile WorldPoint exhaustedValuables = null;
    // True once a search has started auto-looting a piece. One click loots a piece for ~50-60s with no further
    // input, so we leave it alone until the game reports the piece is emptied (onValuablesExhausted) - the same
    // "click once, the game auto-repeats" mechanic as a distracted pickpocket. Gating on animation is wrong here:
    // the auto-loot has long stretches with no animation, so any isAnimating() window goes false mid-loot and re-clicks.
    private volatile boolean searchingPiece = false;
    private volatile long searchingSinceMs = 0;
    private volatile boolean searchingStuckWarned = false;
    private boolean turnedCameraThisCall;
    private WorldPoint cameraTurnedToward;
    private int cameraTurnedTowardNpc = -1;
    private final static String HOUSE_KEYS = "House keys";
    private final static String DODGY_NECKLACE = "Dodgy necklace";
    private final static String COIN_POUCH = "Coin pouch";
    private final static String WEALTHY_CITIZEN = "Wealthy citizen";

    public boolean run(OpraHouseThievingConfig config) {
        log.info("Starting Opra House Thieving script");
        this.config = config;
        Microbot.pauseAllScripts.compareAndSet(true, false);
        Microbot.enableAutoRunOn = false;
        initialPlayerLocation = null;
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.naturalMouse = true;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                step();
            } catch (Exception ex) {
                log.error("House thieving loop error", ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    protected State initialState() {
        return State.PICKPOCKETING;
    }

    @Override
    protected List<Transition<State>> defineTransitions() {
        return List.of();
    }

    @Override
    protected void onState(State state) {
        turnedCameraThisCall = false;
        if (Rs2AntibanSettings.actionCooldownActive) return;

        if (state != State.THIEVING_HOUSES) {
            if (BreakHandlerScript.isLockState()) {
                BreakHandlerScript.setLockState(false);
            }
        } else if (!BreakHandlerScript.isLockState()) {
            BreakHandlerScript.setLockState(true);
        }

        if (currentThievingHouse == null) {
            currentThievingHouse = getThievingHouse();
            log.info("Initial house is {}", currentThievingHouse);
        }

        switch (state) {
            case PICKPOCKETING:
                handlePickPocketing(config);
                break;
            case FINDING_HOUSE:
                handleFindingHouse(config);
                break;
            case THIEVING_HOUSES:
                var houseNpc = Microbot.getRs2NpcCache().query().withName(currentThievingHouse.npcName).nearestOnClientThread();
                handleThievingHouse(houseNpc);
                break;
            case BANKING:
                handleBanking(config);
                break;
        }
    }

    private void enter(State next, String reason) {
        Microbot.status = reason;
        State current = getCurrentState();
        if (next == current && reason.equals(lastReason)) {
            return;
        }
        if (current == State.BANKING && next != State.BANKING && Rs2Bank.isOpen()) {
            log.warn("Leaving the bank while it is still open: {} ({})", next, reason);
        }
        lastReason = reason;
        log.info("{} -> {}: {}", current, next, reason);
        forceState(next, reason);
    }

    private void handlePickPocketing(OpraHouseThievingConfig config) {
        if (Rs2Player.isAnimating()) return;
        var hasFood = !Rs2Inventory.getInventoryFood().isEmpty();
        int foodItems = Rs2Inventory.getInventoryFood().size();
        var hasDodgyNecklaceInv = getDodgyNecklaceAmount() > 0;
        var hasDodgyNecklaceEquipped = Rs2Equipment.isWearing(DODGY_NECKLACE);
        int keys = houseKeyCount();
        if (hasMaxHouseKeys(config) && (hasFood || hasDodgyNecklaceInv)) {
            enter(State.BANKING, String.format(
                    "enough keys (%d), depositing foodItems=%d dodgyInv=%d",
                    keys, foodItems, getDodgyNecklaceAmount()));
            return;
        } else if (hasMaxHouseKeys(config) && !hasFood && !hasDodgyNecklaceInv) {
            enter(State.FINDING_HOUSE, String.format("enough keys (%d), thieving houses", keys));
            return;
        } else if (!hasMaxHouseKeys(config) && (!hasFood || (!hasDodgyNecklaceInv && !hasDodgyNecklaceEquipped && config.useDodgyNecklace())) && !config.worldHopForDistractedCitizens() && !config.onlyPickpocketDistracted() && config.pickpocketFoodAmount() > 0) {
            enter(State.BANKING, String.format(
                    "need supplies keys=%d food=%s dodgyInv=%s dodgyEquipped=%s",
                    keys, hasFood, hasDodgyNecklaceInv, hasDodgyNecklaceEquipped));
            return;
        }

        if (!hasDodgyNecklaceEquipped && hasDodgyNecklaceInv) {
            var dodgyNecklace = Rs2Inventory.get(DODGY_NECKLACE);
            if (dodgyNecklace != null) {
                log.debug("Wearing dodgy necklace");
                Rs2Inventory.interact(dodgyNecklace, "Wear");
            }
        }

        if (Rs2Inventory.hasItem(COIN_POUCH)) {
            var coinPouches = Rs2Inventory.get(COIN_POUCH);
            if (coinPouches.getQuantity() > 27) {
                log.debug("Opening coin pouches qty={}", coinPouches.getQuantity());
                Rs2Inventory.interact(coinPouches, "Open-all");
                Rs2Random.waitEx(200.0, 200.0);
                clickedThisDistraction = false; // opening pouches interrupts auto-pickpocket; re-click needed
            }
        }

        var aureliaNpc = Microbot.getRs2NpcCache().query().withName("Aurelia").nearestOnClientThread();
        Rs2NpcModel distractedWealthyCitizen = null;
        int aureliaAnim = -1;
        if (aureliaNpc != null) {
            aureliaAnim = aureliaNpc.getAnimation();
            if (aureliaAnim == 866 || aureliaAnim == 860) {
                distractedWealthyCitizen = getDistractedWealthyCitizen(aureliaNpc);
                if (distractedWealthyCitizen != null)
                    pickpocketNpc = null;
            } else {
                clickedThisDistraction = false; // distraction ended; allow a click when the next one starts
                if (pickpocketNpc == null) {
                    var nearbyWealthyCitizens = Microbot.getRs2NpcCache().query().withName(WEALTHY_CITIZEN).toListOnClientThread().stream();
                    var aureliaLocation = aureliaNpc.getWorldLocation();
                    var closestWealthyCitizen = nearbyWealthyCitizens.min(Comparator.comparingInt(a -> a.getWorldLocation().distanceTo(aureliaLocation)));
                    closestWealthyCitizen.ifPresent(rs2NpcModel -> pickpocketNpc = rs2NpcModel);
                    if (pickpocketNpc != null) {
                        log.debug("Locked pickpocket target {} near Aurelia at {}", pickpocketNpc.getName(), aureliaLocation);
                    }
                }
            }
            if (config.worldHopForDistractedCitizens()) {
                log.debug("Waiting {}s for Aurelia distraction, anim={} keys={} foodItems={}",
                        config.worldHopWaitTime(), aureliaAnim, keys, foodItems);
                var waitForPickpocket = sleepUntil(() -> aureliaNpc.getAnimation() == 866 || aureliaNpc.getAnimation() == 860, config.worldHopWaitTime() * 1000);
                if (!waitForPickpocket) {
                    log.info("No Aurelia distraction after {}s, hopping", config.worldHopWaitTime());
                    doWorldHop();
                    return;
                } else
                    distractedWealthyCitizen = getDistractedWealthyCitizen(aureliaNpc);
            }
        }

        if (config.onlyPickpocketDistracted() && distractedWealthyCitizen == null)
            return;

        if (distractedWealthyCitizen != null) {
            if (clickedThisDistraction)
                return; // already pickpocketing this distraction; the game auto-repeats for ~24s
            log.debug("Distracted pickpocket npc={} aureliaAnim={} keys={} foodItems={}",
                    distractedWealthyCitizen.getName(), aureliaAnim, keys, foodItems);
            if (clickNpcIfVisible(distractedWealthyCitizen, "Pickpocket")) {
                clickedThisDistraction = true;
            } else {
                approachNpc(distractedWealthyCitizen);
            }
            return;
        }

        if (pickpocketNpc != null) {
            if (Rs2Inventory.getInventoryFood().isEmpty()) {
                enter(State.BANKING, String.format("pickpocket out of food keys=%d", houseKeyCount()));
                return;
            }
            if (Rs2Player.getHealthPercentage() <= config.foodEatPercentage()) {
                log.debug("Eating at {}% hp", Rs2Player.getHealthPercentage());
                Rs2Player.useFood();
                Rs2Inventory.waitForInventoryChanges(600);
            }
            if (Rs2Player.isStunned())
                return;
            log.debug("Pickpocket npc={} keys={} foodItems={} hp={}%",
                    pickpocketNpc.getName(), keys, foodItems, Rs2Player.getHealthPercentage());
            if (!clickNpcIfVisible(pickpocketNpc, "Pickpocket")) {
                approachNpc(pickpocketNpc);
                return;
            }
            Rs2Random.waitEx(600.0, 200.0);
            sleepUntil(() -> !Rs2Player.isAnimating(), 10000);
            return;
        }

        if (Rs2Player.getWorldLocation().distanceTo(PICKPOCKET_LOCATION) > 8) {
            log.debug("No pickpocket target on screen, walking to {} from {}",
                    PICKPOCKET_LOCATION, Rs2Player.getWorldLocation());
            Rs2Walker.walkTo(PICKPOCKET_LOCATION, 5);
        }

        if (distractedWealthyCitizen != null && config.worldHopForDistractedCitizens()) {
            if (distractedWealthyCitizen.getOverheadText().contains("strange")) {
                doWorldHop();
            }
        }
    }

    private void doWorldHop() {
        log.info("World hop for a distracted citizen");
        boolean hoppedWorlds = Microbot.hopToWorld(Login.getRandomWorld(Rs2Player.isMember()));
        if (hoppedWorlds) {
            sleepUntil(() -> Microbot.getClient().getGameState() == GameState.HOPPING);
            sleepUntil(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN);
            pickpocketNpc = null;
            log.info("World hop finished on world {}", Microbot.getClient().getWorld());
        } else {
            log.warn("World hop did not start");
        }
    }

    @Nullable
    private static Rs2NpcModel getDistractedWealthyCitizen(Rs2NpcModel aureliaNpc) {
        var aureliaLoc = aureliaNpc.getWorldLocation();
        return Microbot.getRs2NpcCache().query()
                .withName(WEALTHY_CITIZEN)
                .nearestOnClientThread(aureliaLoc, 5);
    }

    private void handleFindingHouse(OpraHouseThievingConfig config) {
        if (Rs2Inventory.emptySlotCount() < 5) {
            enter(State.BANKING, String.format(
                    "only %d empty slots, banking before the house", Rs2Inventory.emptySlotCount()));
            return;
        }

        var houseKeys = Rs2Inventory.get(HOUSE_KEYS);
        if (houseKeys == null || houseKeys.getQuantity() < config.minHouseKeys()) {
            enter(State.PICKPOCKETING, String.format(
                    "keys %d below min %d", houseKeys == null ? 0 : houseKeys.getQuantity(), config.minHouseKeys()));
            return;
        }

        if (Rs2Player.getWorldLocation().distanceTo(currentThievingHouse.scoutPosition) > 2) {
            log.info("Walking to {} scout {} from {}",
                    currentThievingHouse.npcName,
                    currentThievingHouse.scoutPosition,
                    Rs2Player.getWorldLocation());
            Rs2Walker.walkTo(currentThievingHouse.scoutPosition, 2);
            return;
        }

        log.info("Scouting {} at {} player={}",
                currentThievingHouse.npcName,
                currentThievingHouse.scoutPosition,
                Rs2Player.getWorldLocation());
        ThievingHouse openHouse = null;
        Rs2TileObjectModel openDoor = null;
        var seen = new StringBuilder();
        for (ThievingHouse house : ThievingHouse.values()) {
            if (seen.length() > 0) {
                seen.append(", ");
            }
            Rs2TileObjectModel door = findLockedDoor(house);
            if (door == null) {
                seen.append(house.npcName).append("=closed");
                continue;
            }
            boolean onScreen = isClickboxOnScreen(door);
            seen.append(house.npcName).append("=locked@").append(door.getWorldLocation())
                    .append(onScreen ? " visible" : " offscreen");
            boolean chosenIsVisible = openDoor != null && isClickboxOnScreen(openDoor);
            if (onScreen && (!chosenIsVisible || house == currentThievingHouse)) {
                openHouse = house;
                openDoor = door;
            } else if (openDoor == null && house == currentThievingHouse) {
                openHouse = house;
                openDoor = door;
            }
        }
        log.info("Scout {} doors: {}", currentThievingHouse.npcName, seen);

        if (openDoor == null) {
            log.info("No locked door from {} scout, checking the next house", currentThievingHouse.npcName);
            setNextThievingHouse();
            return;
        }

        currentThievingHouse = openHouse;
        var houseNpc = Microbot.getRs2NpcCache().query().withName(currentThievingHouse.npcName).nearestOnClientThread();
        int ownerDistance = houseNpc == null
                ? -1
                : currentThievingHouse.lockedDoorEntrance.distanceTo(houseNpc.getWorldLocation());
        attemptWaitForHouseNpc(houseNpc);
        if (!clickIfVisible(openDoor, null)) {
            if (turnedCameraThisCall) {
                return;
            }
            log.info("Locked door for {} at {} is not clickable from this scout, moving on",
                    openHouse.npcName, openDoor.getWorldLocation());
            setNextThievingHouse();
            return;
        }

        log.info("Clicking {} door at {} from scout. ownerDistance={}",
                openHouse.npcName, openDoor.getWorldLocation(), ownerDistance);
        Rs2Random.waitEx(2000.0, 100.0);
        sleepUntil(() -> !Rs2Player.isAnimating(600));
        Rs2Random.waitEx(2000.0, 100.0);
        sleepUntil(() -> Rs2Tile.isTileReachable(currentThievingHouse.houseCenter), 10000);
        if (Rs2Tile.isTileReachable(currentThievingHouse.houseCenter)) {
            lastSearchedValuables = null;
            exhaustedValuables = null;
            clearSearching();
            enter(State.THIEVING_HOUSES, String.format(
                    "entered %s door=%s objectId=%d centerReachable=true ownerDistance=%d",
                    currentThievingHouse.npcName,
                    openDoor.getWorldLocation(),
                    openDoor.getId(),
                    ownerDistance));
            return;
        }
        log.warn("Failed to enter {} house from scout. doorTile={} objectId={} player={} centerReachable=false",
                currentThievingHouse.npcName,
                openDoor.getWorldLocation(),
                openDoor.getId(),
                Rs2Player.getWorldLocation());
        setNextThievingHouse();
    }

    private void attemptWaitForHouseNpc(Rs2NpcModel houseNpc) {
        // Try to determine if NPC is leaving or not - could maybe look at overhead text?
        if (houseNpc != null) {
            var overheadText = houseNpc.getOverheadText();
            if (overheadText != null && !overheadText.isEmpty()) {
                log.debug("{} overhead='{}', waiting for them to leave", currentThievingHouse.npcName, overheadText);
                Rs2Random.waitEx(5000.0, 200.0);
            }
        }
        var distanceToHouseNpc = houseNpc != null ? currentThievingHouse.lockedDoorEntrance.distanceTo(houseNpc.getWorldLocation()) : 100;
        log.debug("{} is {} tiles from the door", currentThievingHouse.npcName, distanceToHouseNpc);
        if (distanceToHouseNpc < 6) {
            log.debug("Waiting for {} to move away from the door", currentThievingHouse.npcName);
            sleepUntil(() -> (houseNpc != null ? currentThievingHouse.lockedDoorEntrance.distanceTo(houseNpc.getWorldLocation()) : 100) > 6);
        }
    }

    private void handleThievingHouse(Rs2NpcModel houseNpc) {
        warnIfSearchStuck();
        if (Rs2Inventory.emptySlotCount() < 1) {
            if (!exitHouse("inventory full"))
                return;
            enter(State.BANKING, "inventory full after exit, banking");
            return;
        }

        if (houseNpc != null) {
            var ownerLoc = houseNpc.getWorldLocation();
            int ownerDistance = ownerLoc.distanceTo(currentThievingHouse.lockedDoorEntrance);
            if (ownerDistance < 5) {
                log.debug("Owner {} is {} tiles from the door at {}", houseNpc.getName(), ownerDistance, ownerLoc);
                if (exitHouse("owner near door"))
                    return;
            }
        }

        // A flashing hint arrow ("You notice something shine somewhere else in the house") marks a piece that
        // grants a one-time bonus (+14 valuables, +630 xp) for switching to it. Claim it even mid-loot - the
        // game rewards the switch. The arrow clears ~7s after we search it, and we skip it once we're already on
        // that piece, so this fires once per highlight rather than spam-clicking.
        var hintArrow = Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getHintArrowPoint()).orElse(null);
        if (hintArrow != null && (lastSearchedValuables == null || lastSearchedValuables.distanceTo(hintArrow) > 2)) {
            if (Rs2Player.isMoving())
                return; // en route to the bonus piece
            var bonusPiece = Microbot.getRs2TileObjectCache().query()
                    .withIds(VALUABLES_OBJECT_IDS)
                    .nearestOnClientThread(hintArrow, 2);
            if (bonusPiece != null) {
                lastSearchedValuables = bonusPiece.getWorldLocation();
                exhaustedValuables = null; // the arrow can re-highlight a refilled piece we'd otherwise skip
                log.debug("Hint arrow at {}, searching bonus {} id={}",
                        hintArrow, bonusPiece.getWorldLocation(), bonusPiece.getId());
                if (!clickIfVisible(bonusPiece, "Search")) {
                    approachObject(bonusPiece);
                    return;
                }
                noteSearching(sleepUntil(() -> Rs2Player.isAnimating(), 5000), bonusPiece.getWorldLocation());
                return;
            }
            log.debug("Hint arrow at {} had no valuables within 2 tiles", hintArrow);
        }

        // Already auto-looting a piece: one click keeps looting for ~50-60s, so don't touch it. We only act
        // again when the game tells us the piece is emptied (onValuablesExhausted clears searchingPiece below).
        if (searchingPiece)
            return;

        // Walking to the piece we just clicked - let the click register instead of firing another.
        if (Rs2Player.isMoving())
            return;

        // Search the nearest piece of furniture, skipping the one the game just told us is emptied so we move
        // on instead of spam-clicking it. It refills while we loot another piece.
        var valuables = Microbot.getRs2TileObjectCache().query()
                .withIds(VALUABLES_OBJECT_IDS)
                .where(o -> exhaustedValuables == null || !exhaustedValuables.equals(o.getWorldLocation()))
                .nearestOnClientThread();
        if (valuables == null) {
            if (exhaustedValuables != null) {
                log.debug("No other valuables in reach, retrying exhausted {}", exhaustedValuables);
            }
            exhaustedValuables = null; // nothing else within reach; let the emptied piece refill and retry it
            return;
        }
        lastSearchedValuables = valuables.getWorldLocation();
        log.debug("Searching valuables at {} id={} exhausted={}",
                valuables.getWorldLocation(), valuables.getId(), exhaustedValuables);
        if (!clickIfVisible(valuables, "Search")) {
            approachObject(valuables);
            return;
        }
        // Confirm the search actually started (covers the walk to the piece) before marking it active; if it
        // never starts we retry next loop instead of standing idle. This is a timeout, not a state-modeling cooldown.
        noteSearching(sleepUntil(() -> Rs2Player.isAnimating(), 5000), valuables.getWorldLocation());
    }

    /** True if the tile object exposes a "Bank" menu action (mirrors how Rs2TileObjectModel.click resolves it). */
    private static boolean hasBankAction(Rs2TileObjectModel object) {
        var comp = object.getObjectComposition();
        if (comp == null)
            return false;
        var actions = (comp.getImpostorIds() != null && comp.getImpostor() != null)
                ? comp.getImpostor().getActions() : comp.getActions();
        if (actions == null)
            return false;
        for (var action : actions)
            if ("Bank".equalsIgnoreCase(action))
                return true;
        return false;
    }

    /** Called when the game reports the current piece of furniture is emptied ("nothing else worth taking"). */
    void onValuablesExhausted() {
        log.info("Valuables exhausted at {} (searchingPiece={})", lastSearchedValuables, searchingPiece);
        exhaustedValuables = lastSearchedValuables;
        clearSearching();
    }

    private void noteSearching(boolean started, WorldPoint tile) {
        if (!started) {
            clearSearching();
            log.debug("Search did not start at {}", tile);
            return;
        }
        if (!searchingPiece) {
            searchingSinceMs = System.currentTimeMillis();
            searchingStuckWarned = false;
        }
        searchingPiece = true;
        log.debug("Search started at {} searchingPiece=true", tile);
    }

    private void clearSearching() {
        searchingPiece = false;
        searchingSinceMs = 0;
        searchingStuckWarned = false;
    }

    private void warnIfSearchStuck() {
        if (!searchingPiece || searchingStuckWarned || searchingSinceMs == 0) {
            return;
        }
        long elapsed = System.currentTimeMillis() - searchingSinceMs;
        if (elapsed < SEARCH_STUCK_MS) {
            return;
        }
        searchingStuckWarned = true;
        log.warn("Still searching after {}ms with no 'worth taking' chat. lastSearched={} exhausted={}",
                elapsed, lastSearchedValuables, exhaustedValuables);
    }

    private boolean exitHouse(String why) {
        String houseName = currentThievingHouse.npcName;
        var windowPoint = currentThievingHouse.windowEntrance;
        var windowTile = Rs2Tile.getTile(windowPoint.getX(), windowPoint.getY());
        if (windowTile == null) {
            log.warn("Exit window tile missing house={} tile={} why={}", houseName, windowPoint, why);
            return false;
        }
        var windowTileWallObject = windowTile.getWallObject();
        if (windowTileWallObject == null) {
            log.warn("Exit window has no wall object house={} tile={} why={}", houseName, windowPoint, why);
            return false;
        }
        var windowObject = Microbot.getRs2TileObjectCache().query()
                .withId(windowTileWallObject.getId())
                .nearestOnClientThread(windowPoint, 3);
        if (windowObject == null) {
            log.warn("Exit window not in the object cache house={} tile={} id={} why={}",
                    houseName, windowPoint, windowTileWallObject.getId(), why);
            return false;
        }
        log.debug("Exit window house={} tile={} objectId={} why={}",
                houseName, windowPoint, windowObject.getId(), why);
        if (!clickIfVisible(windowObject, "Exit-window")) {
            approachObject(windowObject);
            return false;
        }
        Rs2Random.waitEx(5000.0, 200.0);
        clearSearching();
        setNextThievingHouse();
        enter(State.FINDING_HOUSE, String.format(
                "exited %s window=%s objectId=%d (%s)",
                houseName, windowPoint, windowTileWallObject.getId(), why));
        return true;
    }

    private boolean hasMaxHouseKeys(OpraHouseThievingConfig config) {
        return houseKeyCount() >= config.maxHouseKeys();
    }

    private int houseKeyCount() {
        var houseKeys = Rs2Inventory.get(HOUSE_KEYS);
        return houseKeys == null ? 0 : houseKeys.getQuantity();
    }

    private void handleBanking(OpraHouseThievingConfig config) {
        var hasFood = Rs2Inventory.getInventoryFood().size() >= config.pickpocketFoodAmount();
        var hasAnyFood = !Rs2Inventory.getInventoryFood().isEmpty();
        int foodItems = Rs2Inventory.getInventoryFood().size();
        var currentDodgyNecklace = getDodgyNecklaceAmount();
        var dodgyNecklaceReqsMet = !config.useDodgyNecklace() || (config.useDodgyNecklace() && currentDodgyNecklace >= config.dodgyNecklaceAmount());
        int keys = houseKeyCount();

        if (hasMaxHouseKeys(config) && !hasAnyFood && currentDodgyNecklace < 1 && Rs2Inventory.emptySlotCount() > 5) {
            enter(State.FINDING_HOUSE, String.format(
                    "bank done keys=%d emptySlots=%d bankOpen=%s",
                    keys, Rs2Inventory.emptySlotCount(), Rs2Bank.isOpen()));
            return;
        }

        if (!hasMaxHouseKeys(config) && ((hasFood && dodgyNecklaceReqsMet) || config.worldHopForDistractedCitizens() || config.onlyPickpocketDistracted())) {
            enter(State.PICKPOCKETING, String.format(
                    "supplies ready keys=%d foodItems=%d dodgy=%d bankOpen=%s",
                    keys, foodItems, currentDodgyNecklace, Rs2Bank.isOpen()));
            return;
        }

        if (!Rs2Bank.isOpen()) {
            // The cache returns an Rs2TileObjectModel wrapper. Passing that wrapper to the legacy
            // Rs2Bank.openBank(TileObject) path loses its underlying GameObject type, which produces incorrect
            // scene parameters for this multi-tile table. Invoke through the model so it uses the wrapped
            // GameObject's size and location, then retain openBank() as a fallback when the cache has no match.
            var bankObject = Microbot.getRs2TileObjectCache().query()
                    .where(OpraHouseThievingScript::hasBankAction)
                    .nearestOnClientThread(BANKING_TILE_LOCATION, 8);
            if (bankObject != null && clickIfVisible(bankObject, "Bank")) {
                log.debug("Opening bank object id={} at {}", bankObject.getId(), bankObject.getWorldLocation());
                sleepUntil(Rs2Bank::isOpen, 5000);
                if (!Rs2Bank.isOpen()) {
                    log.warn("Bank click did not open the bank. objectId={} at {}", bankObject.getId(), bankObject.getWorldLocation());
                }
            } else if (turnedCameraThisCall) {
                return;
            } else if (Rs2Player.getWorldLocation().distanceTo(BANKING_LOCATION) > 2) {
                log.debug("Bank is not visible, walking to {} from {}", BANKING_LOCATION, Rs2Player.getWorldLocation());
                Rs2Walker.walkTo(BANKING_LOCATION, 2);
                return;
            } else if (bankObject == null) {
                log.warn("No bank object near {}, falling back to openBank()", BANKING_TILE_LOCATION);
                Rs2Bank.openBank();
            }
        }

        if (Rs2Bank.isOpen()) {
            log.debug("Bank deposit maxKeys={} keys={} foodItems={} dodgy={}",
                    hasMaxHouseKeys(config), keys, foodItems, currentDodgyNecklace);
            if (!hasMaxHouseKeys(config))
                Rs2Bank.depositAllExcept("Coins", COIN_POUCH, HOUSE_KEYS, config.foodSelection().getName(), DODGY_NECKLACE);
            else
                Rs2Bank.depositAllExcept("Coins", COIN_POUCH, HOUSE_KEYS);
            Rs2Inventory.waitForInventoryChanges(600);

            if (!hasMaxHouseKeys(config)) {
                if (!hasFood && !config.worldHopForDistractedCitizens() && !config.onlyPickpocketDistracted()) {
                    if (!Rs2Bank.hasItem(config.foodSelection().getId())) {
                        log.warn("No {} in the bank, stopping", config.foodSelection().getName());
                        Microbot.showMessage("No food found in bank!");
                        shutdown();
                        return;
                    }
                    log.debug("Withdrawing {} x{}", config.foodSelection().getName(), config.pickpocketFoodAmount());
                    Rs2Bank.withdrawX(config.foodSelection().getId(), config.pickpocketFoodAmount());
                    Rs2Inventory.waitForInventoryChanges(600);
                }
                if (config.useDodgyNecklace() && !config.worldHopForDistractedCitizens() && !config.onlyPickpocketDistracted()) {
                    if (!Rs2Bank.hasItem(DODGY_NECKLACE)) {
                        log.warn("No dodgy necklace in the bank, stopping");
                        Microbot.showMessage("No dodgy necklace found in bank!");
                        shutdown();
                        return;
                    }
                    if (currentDodgyNecklace < config.dodgyNecklaceAmount()) {
                        log.debug("Withdrawing dodgy necklace x{}", config.dodgyNecklaceAmount());
                        Rs2Bank.withdrawX(DODGY_NECKLACE, config.dodgyNecklaceAmount());
                        Rs2Inventory.waitForInventoryChanges(600);
                    }
                }
                if (Rs2Bank.hasItem(HOUSE_KEYS)) {
                    log.debug("Withdrawing house keys");
                    Rs2Bank.withdrawAll(HOUSE_KEYS);
                    Rs2Inventory.waitForInventoryChanges(600);
                }
            }
        }
    }

    private static int getDodgyNecklaceAmount() {
        var dodgyNecklaces = Rs2Inventory.items(
                        (item) -> item.getName().equalsIgnoreCase(DODGY_NECKLACE))
                .collect(Collectors.toList());
        return dodgyNecklaces.size();
    }

    private void setNextThievingHouse() {
        ThievingHouse previous = currentThievingHouse;
        if (currentThievingHouse == ThievingHouse.LAVINIA) {
            currentThievingHouse = ThievingHouse.VICTOR;
            if (hasLockedDoor(ThievingHouse.CAIUS))
                currentThievingHouse = ThievingHouse.CAIUS;
        } else if (currentThievingHouse == ThievingHouse.VICTOR) {
            currentThievingHouse = ThievingHouse.CAIUS;
            if (hasLockedDoor(ThievingHouse.LAVINIA))
                currentThievingHouse = ThievingHouse.LAVINIA;
        } else if (currentThievingHouse == ThievingHouse.CAIUS) {
            currentThievingHouse = ThievingHouse.LAVINIA;
            if (hasLockedDoor(ThievingHouse.VICTOR))
                currentThievingHouse = ThievingHouse.VICTOR;
        }
        log.debug("Next house {} -> {}", previous, currentThievingHouse);
    }

    private static ThievingHouse getThievingHouse() {
        ThievingHouse closestHouse = null;
        for (var house : ThievingHouse.values()) {
            if (hasLockedDoor(house))
                return house;
            if (closestHouse == null)
                closestHouse = house;
            var houseDistance = Rs2Player.getWorldLocation().distanceTo(house.lockedDoorEntrance);
            var shortestHouseDistance = Rs2Player.getWorldLocation().distanceTo(closestHouse.lockedDoorEntrance);
            if (houseDistance < shortestHouseDistance) {
                closestHouse = house;
            }
        }
        return closestHouse;
    }

    private static boolean hasLockedDoor(ThievingHouse thievingHouse) {
        return tileHasLockedDoor(thievingHouse.lockedDoorEntrance)
                || tileHasLockedDoor(thievingHouse.lockedDoorEgress)
                || findLockedDoor(thievingHouse) != null;
    }

    private static boolean tileHasLockedDoor(WorldPoint point) {
        var tile = Rs2Tile.getTile(point.getX(), point.getY());
        if (tile == null) {
            return false;
        }
        var wallObject = tile.getWallObject();
        return wallObject != null && wallObject.getId() == LOCKED_DOOR_ID;
    }

    private boolean clickIfVisible(Rs2TileObjectModel object, String action) {
        turnedCameraThisCall = false;
        if (object == null) {
            return false;
        }
        if (isClickboxOnScreen(object)) {
            cameraTurnedToward = null;
            return action == null ? object.click() : object.click(action);
        }
        WorldPoint loc = object.getWorldLocation();
        if (cameraTurnedToward == null || loc == null || !cameraTurnedToward.equals(loc)) {
            Rs2Camera.turnTo(object);
            cameraTurnedToward = loc;
            turnedCameraThisCall = true;
            if (isClickboxOnScreen(object)) {
                cameraTurnedToward = null;
                return action == null ? object.click() : object.click(action);
            }
        }
        return false;
    }

    private boolean clickNpcIfVisible(Rs2NpcModel npc, String action) {
        turnedCameraThisCall = false;
        if (npc == null) {
            return false;
        }
        if (isClickboxOnScreen(npc)) {
            cameraTurnedTowardNpc = -1;
            return npc.click(action);
        }
        if (cameraTurnedTowardNpc != npc.getIndex()) {
            Rs2Camera.turnTo(npc);
            cameraTurnedTowardNpc = npc.getIndex();
            turnedCameraThisCall = true;
            if (isClickboxOnScreen(npc)) {
                cameraTurnedTowardNpc = -1;
                return npc.click(action);
            }
        }
        return false;
    }

    private void approachObject(Rs2TileObjectModel object) {
        if (turnedCameraThisCall || object == null || object.getWorldLocation() == null) {
            return;
        }
        if (Rs2Player.getWorldLocation().distanceTo(object.getWorldLocation()) <= 2) {
            return;
        }
        log.debug("Target {} at {} is off screen, walking closer from {}",
                object.getId(), object.getWorldLocation(), Rs2Player.getWorldLocation());
        Rs2Walker.walkTo(object.getWorldLocation(), 1);
    }

    private void approachNpc(Rs2NpcModel npc) {
        if (turnedCameraThisCall || npc == null || npc.getWorldLocation() == null) {
            return;
        }
        if (Rs2Player.getWorldLocation().distanceTo(npc.getWorldLocation()) <= 2) {
            return;
        }
        log.debug("Pickpocket target {} is off screen, walking closer from {}",
                npc.getName(), Rs2Player.getWorldLocation());
        Rs2Walker.walkTo(npc.getWorldLocation(), 2);
    }

    private static boolean isClickboxOnScreen(Rs2TileObjectModel object) {
        Rectangle bounds = Rs2UiHelper.getObjectClickbox(object);
        return bounds != null && Rs2UiHelper.isRectangleWithinViewport(bounds);
    }

    private static boolean isClickboxOnScreen(Rs2NpcModel npc) {
        Rectangle bounds = Rs2UiHelper.getActorClickbox(npc);
        return bounds != null && Rs2UiHelper.isRectangleWithinViewport(bounds);
    }

    /** The locked door for this house, limited to the entrance and the inside tile. */
    private static Rs2TileObjectModel findLockedDoor(ThievingHouse house) {
        var atEntrance = Microbot.getRs2TileObjectCache().query()
                .withId(LOCKED_DOOR_ID)
                .nearestOnClientThread(house.lockedDoorEntrance, 2);
        if (atEntrance != null) {
            return atEntrance;
        }
        if (!tileHasLockedDoor(house.lockedDoorEntrance) && !tileHasLockedDoor(house.lockedDoorEgress)) {
            return null;
        }
        return Microbot.getRs2TileObjectCache().query()
                .withId(LOCKED_DOOR_ID)
                .nearestOnClientThread(house.lockedDoorEgress, 2);
    }

    @Override
    public void shutdown() {
        log.info("Stopping Opra House Thieving script");
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
        log.info("Opra House Thieving script stopped");
    }

}
