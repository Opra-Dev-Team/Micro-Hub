package net.runelite.client.plugins.microbot.oprahousethieving;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
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
import net.runelite.client.plugins.microbot.util.walker.Rs2MiniMap;
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
    /** Door or scout tile the current minimap step is heading for. One click, then the next tick looks again. */
    private WorldPoint scoutDestination;
    /** Last locked door we walked toward. One missed scan keeps this tile before the scout patrol. */
    private WorldPoint lastDoorTile;
    private int doorMisses;
    /** Door state per house, kept across visits. */
    private final Map<ThievingHouse, HouseWatch> watches = new EnumMap<>(ThievingHouse.class);
    private volatile boolean homeownerReturning;
    private volatile boolean kickedOut;
    private volatile boolean shineSeen;
    private int outsideSamples;
    /** Hint arrow tile we already clicked. Cleared when the arrow goes away. */
    private WorldPoint claimedHint;
    private WorldPoint missingHintLogged;
    private String lastDoorScan;
    private ThievingHouse waitingOwnerHouse;
    private int waitingOwnerDistance = -1;
    /** Every owner is home: we stand at this house's scout and keep scanning until a door locks. */
    private ThievingHouse waitingAt;
    /** When we reached the wait scout, 0 until we stand on it. */
    private long waitingSinceMs;
    private long lastWaitLogMs;
    private String lastNoDoorPick;
    private WorldPoint cachedPathDestination;
    private List<WorldPoint> cachedPath;
    private long lastStepMs;
    private static final int DOOR_CAMERA_DISTANCE = 10;
    private static final int MINIMAP_STEP_TILES = 12;
    static final int OWNER_TOO_CLOSE = 6;
    /** An owner this close to his door and outside the house has beaten us to it. */
    static final int OWNER_AT_DOOR = 3;
    /** A closed house whose unlocked door we never saw counts as cycled after this long. */
    static final long CLOSED_MAX_MS = 60_000;
    /** While every owner is home, how often the wait logs what it sees. */
    private static final long WAIT_LOG_MS = 20_000;
    /** A wait at one scout with nothing reopening moves on to the next scout after this long. */
    static final long WAIT_ROTATE_MS = 45_000;
    /** A new unlocked sighting after this gap is logged. */
    private static final long UNLOCKED_LOG_GAP_MS = 10_000;
    private final static String HOUSE_KEYS = "House keys";
    private final static String DODGY_NECKLACE = "Dodgy necklace";
    private final static String COIN_POUCH = "Coin pouch";
    private final static String VALUABLES = "Valuables";
    static final List<String> ROGUE_GEAR = List.of(
            "Rogue mask", "Rogue top", "Rogue trousers", "Rogue gloves", "Rogue boots");
    static final List<String> GRACEFUL_FALLBACK = List.of(
            "Graceful hood", "Graceful top", "Graceful legs", "Graceful gloves", "Graceful boots");
    static final String GRACEFUL_CAPE = "Graceful cape";
    /** Auto gear wants a bank trip to gear up and take out valuables and keys. */
    private boolean gearPending;
    private boolean gearChecked;
    /** The bank trip is for loot from a full inventory, so the run picks up again after it. */
    private boolean resumeRun;
    private boolean pinWarned;
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
                long now = System.currentTimeMillis();
                boolean urgent = homeownerReturning || kickedOut || shineSeen;
                if (!urgent && getCurrentState() != State.FINDING_HOUSE && now - lastStepMs < 1000) return;
                lastStepMs = now;
                step();
            } catch (Exception ex) {
                log.error("House thieving loop error", ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
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

        if (config.autoGear() && !gearChecked) {
            gearChecked = true;
            boolean best = wearingBestGear();
            int keys = houseKeyCount();
            if (needsGearBank(best, keys)) {
                gearPending = true;
                enter(State.BANKING, String.format("auto gear: bestGear=%s keys=%d, gearing up", best, keys));
                return;
            }
            log.info("Auto gear: already wearing best gear with {} keys, skipping the bank", keys);
        }

        if (currentThievingHouse == null) {
            currentThievingHouse = getThievingHouse();
            log.info("Initial house is {}", currentThievingHouse);
        }

        if (handleKickedOut()) {
            return;
        }

        if (handleHomeownerWarning()) {
            return;
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
            resumeRun = true;
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

        WorldPoint player = Rs2Player.getWorldLocation();
        watchDoors();
        List<ScoutDoor> sights = new ArrayList<>();
        Map<ThievingHouse, Rs2TileObjectModel> doors = new EnumMap<>(ThievingHouse.class);
        StringBuilder scan = new StringBuilder();
        StringBuilder scanKey = new StringBuilder();
        for (ThievingHouse house : ThievingHouse.values()) {
            Rs2TileObjectModel door = findLockedDoor(house);
            if (door == null) {
                continue;
            }
            WorldPoint doorTile = door.getWorldLocation() != null
                    ? door.getWorldLocation()
                    : house.lockedDoorEntrance;
            int ownerDistance = ownerDistance(house);
            boolean ownerHome = ownerDistance >= 0 && ownerDistance < OWNER_TOO_CLOSE;
            boolean visible = isClickboxOnScreen(door);
            int distance = player.distanceTo(doorTile);
            sights.add(new ScoutDoor(house, visible, distance, ownerHome));
            doors.put(house, door);
            scan.append(String.format("%s locked %s d=%d owner=%d%s; ",
                    house.npcName, visible ? "visible" : "offscreen",
                    distance, ownerDistance, isClosed(house) ? " closed" : ""));
            scanKey.append(house.name()).append(visible).append(ownerHome).append(isClosed(house));
        }
        logDoorScan(scanKey.toString(), scan.length() == 0 ? "none" : scan.toString().trim());

        ScoutChoice choice = chooseScoutDoor(currentThievingHouse, sights, closedHouses());
        if (choice.move == ScoutMove.CLICK || choice.move == ScoutMove.APPROACH_DOOR) {
            setWaitingAt(null);
            Rs2TileObjectModel door = doors.get(choice.house);
            WorldPoint doorTile = door != null && door.getWorldLocation() != null
                    ? door.getWorldLocation()
                    : choice.house.lockedDoorEntrance;
            lastDoorTile = doorTile;
            doorMisses = 0;
            if (choice.move == ScoutMove.CLICK) {
                openSeenDoor(choice.house, door);
            } else {
                approachLockedDoor(choice.house, door);
            }
            return;
        }
        if (sights.isEmpty() && lastDoorTile != null && doorMisses < 1) {
            doorMisses++;
            stepToward(lastDoorTile);
            return;
        }
        lastDoorTile = null;
        doorMisses = 0;

        if (player.distanceTo(currentThievingHouse.scoutPosition) <= 2) {
            if (waitingAt == currentThievingHouse) {
                waitHeartbeat();
                return;
            }
            ThievingHouse from = currentThievingHouse;
            scoutDestination = null;
            setNextThievingHouse();
            String pick = from.name() + ">" + currentThievingHouse.name();
            if (!pick.equals(lastNoDoorPick)) {
                lastNoDoorPick = pick;
                log.info("No locked door from {} scout, checking {}", from.npcName, currentThievingHouse.npcName);
            }
            return;
        }
        stepToward(currentThievingHouse.scoutPosition);
    }

    /** Logs the scan when a door appears, goes, comes on screen, or its owner crosses the click distance. */
    private void logDoorScan(String key, String scan) {
        if (key.equals(lastDoorScan)) {
            return;
        }
        lastDoorScan = key;
        log.info("Door scan from {}: {}", Rs2Player.getWorldLocation(), scan);
    }

    private void approachLockedDoor(ThievingHouse house, Rs2TileObjectModel door) {
        if (door == null) {
            return;
        }
        WorldPoint doorTile = door.getWorldLocation() != null
                ? door.getWorldLocation()
                : house.lockedDoorEntrance;
        int distance = Rs2Player.getWorldLocation().distanceTo(doorTile);
        if (isClickboxOnScreen(door)) {
            openSeenDoor(house, door);
            return;
        }
        if (distance <= DOOR_CAMERA_DISTANCE) {
            if (isClickboxOnScreen(door)) {
                stepToward(doorTile);
                return;
            }
            if (cameraTurnedToward == null || !cameraTurnedToward.equals(doorTile)) {
                Rs2Camera.turnTo(door);
                cameraTurnedToward = doorTile;
                turnedCameraThisCall = true;
                if (isClickboxOnScreen(door)) {
                    openSeenDoor(house, door);
                    return;
                }
                return;
            }
        }
        stepToward(doorTile);
    }

    private void openSeenDoor(ThievingHouse house, Rs2TileObjectModel door) {
        if (door == null) {
            return;
        }
        WorldPoint doorTile = door.getWorldLocation() != null
                ? door.getWorldLocation()
                : house.lockedDoorEntrance;
        WorldPoint ownerAt = ownerLocation(house);
        int ownerDistance = ownerAt == null ? -1 : house.lockedDoorEntrance.distanceTo(ownerAt);
        if (ownerDistance >= 0 && ownerDistance < OWNER_TOO_CLOSE) {
            if (waitingOwnerHouse == house
                    && ownerArriving(waitingOwnerDistance, ownerDistance, isInsideHouse(house, ownerAt))) {
                log.info("Owner {} is walking home ({} -> {} tiles), trying the next house",
                        house.npcName, waitingOwnerDistance, ownerDistance);
                waitingOwnerHouse = null;
                waitingOwnerDistance = -1;
                closeHouse(house);
                setNextThievingHouse();
                return;
            }
            if (waitingOwnerHouse != house) {
                log.info("Owner {} is {} tiles from the door, waiting by it", house.npcName, ownerDistance);
                waitingOwnerHouse = house;
            }
            waitingOwnerDistance = ownerDistance;
            currentThievingHouse = house;
            if (Rs2Player.getWorldLocation().distanceTo(doorTile) > 3) {
                stepToward(doorTile);
            }
            return;
        }
        waitingOwnerHouse = null;
        waitingOwnerDistance = -1;
        boolean midWalk = scoutDestination != null || Rs2Player.isMoving();
        scoutDestination = null;
        Rs2Walker.clearWalkingRoute("locked door visible");
        currentThievingHouse = house;
        if (!clickIfVisible(door, null)) {
            if (turnedCameraThisCall) {
                return;
            }
            log.info("Locked door for {} at {} is not clickable, moving on",
                    house.npcName, door.getWorldLocation());
            setNextThievingHouse();
            return;
        }

        int doorId = door.getId();
        log.info("Clicking {} door at {}{}. ownerDistance={}",
                house.npcName,
                doorTile,
                midWalk ? " mid-walk" : "",
                ownerDistance);
        EntryResult entry = waitForSettledInside(house, doorTile, ownerDistance, 14000);
        if (entry == EntryResult.ENTERED) {
            lastSearchedValuables = null;
            exhaustedValuables = null;
            clearSearching();
            watch(house).closedAt = 0;
            outsideSamples = 0;
            enter(State.THIEVING_HOUSES, String.format(
                    "entered %s door=%s objectId=%d inside=true ownerDistance=%d",
                    house.npcName,
                    doorTile,
                    doorId,
                    ownerDistance));
            return;
        }
        if (entry == EntryResult.OWNER_BEAT_US) {
            closeHouse(house);
            setNextThievingHouse();
            return;
        }
        log.warn("Failed to enter {} house from scout. doorTile={} objectId={} player={} inside=false",
                house.npcName,
                doorTile,
                doorId,
                Rs2Player.getWorldLocation());
        setNextThievingHouse();
    }

    enum EntryResult {
        ENTERED,
        OWNER_BEAT_US,
        TIMED_OUT
    }

    /**
     * Walking across the edge of the house box is not an entry. Two still samples a tick apart are. While we are
     * still outside, the owner walking up to his door or the door unlocking ends the run.
     */
    private EntryResult waitForSettledInside(ThievingHouse house, WorldPoint doorTile, int clickOwnerDistance,
                                             long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        boolean previous = false;
        int previousOwnerDistance = clickOwnerDistance;
        while (System.currentTimeMillis() < deadline) {
            WorldPoint player = Rs2Player.getWorldLocation();
            boolean inside = isInsideHouse(house, player);
            if (!inside && player != null) {
                // Next to the door the unlocked id may be our own pass-through, so only trust it from further off.
                boolean doorUnlocked = player.distanceTo(doorTile) > 2 && findUnlockedDoor(house) != null;
                WorldPoint ownerAt = ownerLocation(house);
                int ownerDistance = ownerAt == null ? -1 : house.lockedDoorEntrance.distanceTo(ownerAt);
                if (ownerBeatUs(doorUnlocked, previousOwnerDistance, ownerDistance, isInsideHouse(house, ownerAt))) {
                    if (doorUnlocked) {
                        watch(house).lastUnlockedAt = System.currentTimeMillis();
                    }
                    log.info("{} got home before us (doorUnlocked={} owner {} -> {}), trying the next house",
                            house.npcName, doorUnlocked, previousOwnerDistance, ownerDistance);
                    return EntryResult.OWNER_BEAT_US;
                }
                previousOwnerDistance = ownerDistance;
            }
            boolean current = inside && !Rs2Player.isMoving();
            if (isSettledEntry(previous, current)) {
                return EntryResult.ENTERED;
            }
            previous = current;
            sleep(600);
        }
        return EntryResult.TIMED_OUT;
    }

    /**
     * The door unlocking, or the owner closing in on it from outside and reaching it, means he got home first.
     * One sample proves nothing: an owner who just walked out is also outside and near the door.
     */
    static boolean ownerBeatUs(boolean doorUnlocked, int previousDistance, int ownerDistance,
                               boolean ownerInsideBox) {
        if (doorUnlocked) {
            return true;
        }
        return ownerArriving(previousDistance, ownerDistance, ownerInsideBox) && ownerDistance <= OWNER_AT_DOOR;
    }

    /** An owner outside his house getting closer to the door between two looks is arriving, not leaving. */
    static boolean ownerArriving(int previousDistance, int ownerDistance, boolean ownerInsideBox) {
        return !ownerInsideBox && previousDistance >= 0 && ownerDistance >= 0 && ownerDistance < previousDistance;
    }

    /**
     * A closed house is a new round once its owner cycle is done (door seen unlocked, or closed long enough) and
     * the door is locked again with the owner not within {@link #OWNER_TOO_CLOSE} tiles. After the cycle a
     * locked door means the owner went out, so an owner we cannot find is away.
     */
    static boolean reopensClosedHouse(boolean cycled, boolean doorLocked, int ownerDistance) {
        return cycled && doorLocked && (ownerDistance < 0 || ownerDistance >= OWNER_TOO_CLOSE);
    }

    static boolean isSettledEntry(boolean previousStillInside, boolean currentStillInside) {
        return previousStillInside && currentStillInside;
    }

    /** One minimap click that returns. The next tick can click a door that is now on screen. */
    private void stepToward(WorldPoint destination) {
        if (destination == null) {
            return;
        }
        if (Rs2Player.isMoving() && destination.equals(scoutDestination)) {
            return;
        }
        if (!destination.equals(scoutDestination)) {
            log.info("Stepping toward {} from {}", destination, Rs2Player.getWorldLocation());
            scoutDestination = destination;
            Rs2Walker.clearWalkingRoute("scout step");
        }
        clickMinimap(nearerMinimapStep(destination));
    }

    private void clickMinimap(WorldPoint step) {
        var point = Rs2MiniMap.worldToMinimap(step);
        if (point == null || !Rs2MiniMap.isPointInsideMinimap(point)) {
            return;
        }
        Microbot.getMouse().click(point);
    }

    private WorldPoint nearerMinimapStep(WorldPoint destination) {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player.distanceTo(destination) <= MINIMAP_STEP_TILES) {
            return destination;
        }
        List<WorldPoint> path = cachedPath;
        int closest = closestIndex(path, player);
        if (!destination.equals(cachedPathDestination) || closest < 0
                || player.distanceTo(path.get(closest)) > 3) {
            path = Rs2Walker.getWalkPath(destination);
            cachedPath = path;
            cachedPathDestination = destination;
            closest = closestIndex(path, player);
        }
        if (closest < 0) {
            return destination;
        }
        WorldPoint step = path.get(closest);
        for (int i = closest; i < path.size(); i++) {
            if (player.distanceTo(path.get(i)) > MINIMAP_STEP_TILES) {
                break;
            }
            step = path.get(i);
        }
        return step;
    }

    private static int closestIndex(List<WorldPoint> path, WorldPoint player) {
        if (path == null || path.isEmpty() || player == null) {
            return -1;
        }
        int best = 0;
        for (int i = 1; i < path.size(); i++) {
            if (player.distanceTo(path.get(i)) < player.distanceTo(path.get(best))) {
                best = i;
            }
        }
        return best;
    }

    private int ownerDistance(ThievingHouse house) {
        WorldPoint ownerAt = ownerLocation(house);
        return ownerAt == null ? -1 : house.lockedDoorEntrance.distanceTo(ownerAt);
    }

    @Nullable
    private static WorldPoint ownerLocation(ThievingHouse house) {
        var houseNpc = Microbot.getRs2NpcCache().query().withName(house.npcName).nearestOnClientThread();
        return houseNpc == null ? null : houseNpc.getWorldLocation();
    }

    /** True while the warning stands and we still need to get out. Finding a house continues. */
    private boolean handleHomeownerWarning() {
        if (!homeownerReturning || currentThievingHouse == null) {
            return false;
        }
        if (getCurrentState() == State.THIEVING_HOUSES) {
            if (exitHouse("homeowner coming back")) {
                homeownerReturning = false;
            }
            return true;
        }
        homeownerReturning = false;
        if (!isInsideHouse(currentThievingHouse, Rs2Player.getWorldLocation())) {
            return false;
        }
        log.info("Homeowner returning, leaving {} for this lap", currentThievingHouse.npcName);
        closeHouse(currentThievingHouse);
        setNextThievingHouse();
        return false;
    }

    private boolean handleKickedOut() {
        if (kickedOut) {
            kickedOut = false;
            recoverToScout("kicked out");
            return true;
        }
        if (homeownerReturning) {
            return false;
        }
        if (getCurrentState() == State.THIEVING_HOUSES
                && currentThievingHouse != null
                && !isInsideHouse(currentThievingHouse, Rs2Player.getWorldLocation())
                && !Rs2Player.isMoving()) {
            outsideSamples++;
            if (outsideSamples >= 2) {
                outsideSamples = 0;
                backToSameDoor();
                return true;
            }
            return false;
        }
        outsideSamples = 0;
        return false;
    }

    private void recoverToScout(String why) {
        log.info("Caught, heading to the next house ({})", why);
        clearSearching();
        homeownerReturning = false;
        scoutDestination = null;
        lastDoorTile = null;
        doorMisses = 0;
        if (currentThievingHouse != null) {
            closeHouse(currentThievingHouse);
            setNextThievingHouse();
        }
        enter(State.FINDING_HOUSE, String.format("caught, scouting the next house (%s)", why));
        if (currentThievingHouse != null) {
            stepToward(currentThievingHouse.scoutPosition);
        }
    }

    /** Outside without a kick: never got in. Keep the house so the next scan clicks its door again. */
    private void backToSameDoor() {
        log.info("Outside {} without a kick, back to its door", currentThievingHouse.npcName);
        clearSearching();
        scoutDestination = null;
        enter(State.FINDING_HOUSE, String.format("outside %s without a kick, retrying its door",
                currentThievingHouse.npcName));
    }

    private void closeHouse(ThievingHouse house) {
        watch(house).closedAt = System.currentTimeMillis();
    }

    /** What we know about one house's door between visits. */
    private static final class HouseWatch {
        /** When we closed the house, 0 while it is open to us. */
        long closedAt;
        /** Last time its door was seen unlocked, meaning the owner was home. */
        long lastUnlockedAt;
    }

    private HouseWatch watch(ThievingHouse house) {
        return watches.computeIfAbsent(house, h -> new HouseWatch());
    }

    private boolean isClosed(ThievingHouse house) {
        return watch(house).closedAt > 0;
    }

    private Set<ThievingHouse> closedHouses() {
        Set<ThievingHouse> closed = EnumSet.noneOf(ThievingHouse.class);
        for (ThievingHouse house : ThievingHouse.values()) {
            if (isClosed(house)) {
                closed.add(house);
            }
        }
        return closed;
    }

    /** Records unlocked doors and reopens closed houses whose owner has been home and gone out again. */
    private void watchDoors() {
        long now = System.currentTimeMillis();
        for (ThievingHouse house : ThievingHouse.values()) {
            HouseWatch watch = watch(house);
            if (findUnlockedDoor(house) != null) {
                if (now - watch.lastUnlockedAt > UNLOCKED_LOG_GAP_MS) {
                    log.info("{} door is unlocked, owner is home", house.npcName);
                }
                watch.lastUnlockedAt = now;
            }
            if (watch.closedAt == 0) {
                continue;
            }
            boolean cycled = watch.lastUnlockedAt >= watch.closedAt || now - watch.closedAt >= CLOSED_MAX_MS;
            boolean doorLocked = findLockedDoor(house) != null;
            int owner = ownerDistance(house);
            if (reopensClosedHouse(cycled, doorLocked, owner)) {
                log.info("{} door locked again after the owner was home, owner {} tiles away, it is open to us",
                        house.npcName, owner);
                watch.closedAt = 0;
                setWaitingAt(null);
            }
        }
    }

    private void setWaitingAt(ThievingHouse house) {
        if (waitingAt == house) {
            return;
        }
        waitingAt = house;
        waitingSinceMs = 0;
        lastWaitLogMs = 0;
    }

    /** Standing at the wait scout: log what we see now and then, and move to the next scout if nothing reopens. */
    private void waitHeartbeat() {
        long now = System.currentTimeMillis();
        if (waitingSinceMs == 0) {
            waitingSinceMs = now;
            lastWaitLogMs = now;
            return;
        }
        if (shouldRotateWait(waitingSinceMs, now)) {
            ThievingHouse next = nextScout(waitingAt);
            log.info("Waited {}s at {} scout, rotating to {} ({})",
                    (now - waitingSinceMs) / 1000, waitingAt.npcName, next.npcName, describeWaitState());
            setWaitingAt(next);
            currentThievingHouse = next;
            stepToward(next.scoutPosition);
            return;
        }
        if (now - lastWaitLogMs >= WAIT_LOG_MS) {
            lastWaitLogMs = now;
            log.info("Waiting {}s at {} scout: {}",
                    (now - waitingSinceMs) / 1000, waitingAt.npcName, describeWaitState());
        }
    }

    private String describeWaitState() {
        StringBuilder state = new StringBuilder();
        for (ThievingHouse house : ThievingHouse.values()) {
            HouseWatch watch = watch(house);
            String door = findLockedDoor(house) != null ? "locked"
                    : findUnlockedDoor(house) != null ? "unlocked" : "not found";
            state.append(String.format("%s door=%s owner=%d closed=%s cycled=%s; ",
                    house.npcName, door, ownerDistance(house), isClosed(house),
                    isClosed(house) && watch.lastUnlockedAt >= watch.closedAt));
        }
        return state.toString().trim();
    }

    static boolean shouldRotateWait(long waitingSince, long now) {
        return waitingSince > 0 && now - waitingSince >= WAIT_ROTATE_MS;
    }

    static ThievingHouse nextScout(ThievingHouse house) {
        ThievingHouse[] houses = ThievingHouse.values();
        return houses[(house.ordinal() + 1) % houses.length];
    }

    void onHomeownerReturning() {
        homeownerReturning = true;
    }

    void onKickedOut() {
        kickedOut = true;
    }

    private void handleThievingHouse(Rs2NpcModel houseNpc) {
        warnIfSearchStuck();
        if (Rs2Inventory.emptySlotCount() < 1) {
            if (!exitHouse("inventory full"))
                return;
            resumeRun = true;
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
        if (claimShine()) {
            return;
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

    private static WorldPoint hintArrowPoint() {
        return Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getClient().getHintArrowPoint())
                .orElse(null);
    }

    /** True while the shine needs this tick. The hint tile is claimed only once the click went through. */
    private boolean claimShine() {
        WorldPoint hintArrow = hintArrowPoint();
        if (hintArrow == null && shineSeen) {
            sleepUntil(() -> hintArrowPoint() != null, 1200);
            hintArrow = hintArrowPoint();
        }
        if (hintArrow == null) {
            if (shineSeen) {
                log.info("Shine in chat but no hint arrow");
            }
            shineSeen = false;
            claimedHint = null;
            return false;
        }
        shineSeen = false;
        if (hintArrow.equals(claimedHint)) {
            return false;
        }
        var bonusPiece = Microbot.getRs2TileObjectCache().query()
                .withIds(VALUABLES_OBJECT_IDS)
                .nearestOnClientThread(hintArrow, 2);
        if (bonusPiece == null || bonusPiece.getWorldLocation() == null) {
            if (!hintArrow.equals(missingHintLogged)) {
                log.info("Hint arrow at {} has no valuables within 2 tiles", hintArrow);
                missingHintLogged = hintArrow;
            }
            return false;
        }
        WorldPoint pieceTile = bonusPiece.getWorldLocation();
        WorldPoint before = Rs2Player.getWorldLocation();
        if (!clickIfVisible(bonusPiece, "Search")) {
            log.info("Bonus {} at {} is off screen, stepping closer", bonusPiece.getId(), pieceTile);
            if (!Rs2Player.isMoving()) {
                clickMinimap(pieceTile);
            }
            return true;
        }
        final WorldPoint arrow = hintArrow;
        boolean registered = sleepUntil(() -> Rs2Player.isMoving()
                || !arrow.equals(hintArrowPoint())
                || !Rs2Player.getWorldLocation().equals(before)
                || Rs2Player.getWorldLocation().distanceTo(pieceTile) <= 1, 1500);
        if (!registered) {
            log.info("Bonus click at {} did not register, retrying", pieceTile);
            return true;
        }
        log.info("Claimed shine at {} on piece {} id={}", hintArrow, pieceTile, bonusPiece.getId());
        claimedHint = hintArrow;
        lastSearchedValuables = pieceTile;
        exhaustedValuables = null;
        clearSearching();
        sleepUntil(() -> !Rs2Player.isMoving(), 5000);
        noteSearching(true, pieceTile);
        return true;
    }

    void onShine() {
        shineSeen = true;
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
        ThievingHouse house = currentThievingHouse;
        if (house == null) {
            return false;
        }
        if (!isInsideHouse(house, Rs2Player.getWorldLocation())
                && getCurrentState() != State.THIEVING_HOUSES) {
            log.info("Already outside {}, going to the next house ({})", house.npcName, why);
            noteLeftHouse(house);
            enter(State.FINDING_HOUSE, String.format("already outside %s (%s)", house.npcName, why));
            return true;
        }
        String houseName = house.npcName;
        var windowObject = findExitWindow(house);
        if (windowObject == null) {
            log.warn("Exit window not found house={} tile={} why={}", houseName, house.windowEntrance, why);
            return false;
        }
        log.debug("Exit window house={} tile={} objectId={} why={}",
                houseName, windowObject.getWorldLocation(), windowObject.getId(), why);
        if (!clickIfVisible(windowObject, "Exit-window")) {
            approachObject(windowObject);
            return false;
        }
        sleepUntil(() -> {
            WorldPoint at = Rs2Player.getWorldLocation();
            return at != null && (at.equals(house.windowEgress) || !isInsideHouse(house, at));
        }, 5200);
        noteLeftHouse(house);
        enter(State.FINDING_HOUSE, String.format(
                "exited %s window=%s objectId=%d (%s)",
                houseName, windowObject.getWorldLocation(), windowObject.getId(), why));
        return true;
    }

    private Rs2TileObjectModel findExitWindow(ThievingHouse house) {
        WorldPoint windowPoint = house.windowEntrance;
        var windowTile = Rs2Tile.getTile(windowPoint.getX(), windowPoint.getY());
        if (windowTile != null && windowTile.getWallObject() != null) {
            var fromWall = Microbot.getRs2TileObjectCache().query()
                    .withId(windowTile.getWallObject().getId())
                    .nearestOnClientThread(windowPoint, 3);
            if (fromWall != null) {
                return fromWall;
            }
        }
        log.warn("Exit window wall missing house={} tile={}, using object {}",
                house.npcName, windowPoint, ESCAPE_WINDOW_ID);
        return Microbot.getRs2TileObjectCache().query()
                .withId(ESCAPE_WINDOW_ID)
                .nearestOnClientThread(house.houseCenter, 15);
    }

    private void noteLeftHouse(ThievingHouse house) {
        homeownerReturning = false;
        closeHouse(house);
        clearSearching();
        scoutDestination = null;
        lastDoorTile = null;
        doorMisses = 0;
        setNextThievingHouse();
    }

    private boolean hasMaxHouseKeys(OpraHouseThievingConfig config) {
        return houseKeyCount() >= config.maxHouseKeys();
    }

    private int houseKeyCount() {
        var houseKeys = Rs2Inventory.get(HOUSE_KEYS);
        return houseKeys == null ? 0 : houseKeys.getQuantity();
    }

    private void handleBanking(OpraHouseThievingConfig config) {
        enterPinIfNeeded(config);
        boolean ownTrip = gearPending || resumeRun;
        if (ownTrip && openCoinPouches()) {
            return;
        }

        var hasFood = Rs2Inventory.getInventoryFood().size() >= config.pickpocketFoodAmount();
        var hasAnyFood = !Rs2Inventory.getInventoryFood().isEmpty();
        int foodItems = Rs2Inventory.getInventoryFood().size();
        var currentDodgyNecklace = getDodgyNecklaceAmount();
        var dodgyNecklaceReqsMet = !config.useDodgyNecklace() || (config.useDodgyNecklace() && currentDodgyNecklace >= config.dodgyNecklaceAmount());
        int keys = houseKeyCount();

        if (!ownTrip && hasMaxHouseKeys(config) && !hasAnyFood && currentDodgyNecklace < 1 && Rs2Inventory.emptySlotCount() > 5) {
            enter(State.FINDING_HOUSE, String.format(
                    "bank done keys=%d emptySlots=%d bankOpen=%s",
                    keys, Rs2Inventory.emptySlotCount(), Rs2Bank.isOpen()));
            return;
        }

        if (!ownTrip && !hasMaxHouseKeys(config) && ((hasFood && dodgyNecklaceReqsMet) || config.worldHopForDistractedCitizens() || config.onlyPickpocketDistracted())) {
            enter(State.PICKPOCKETING, String.format(
                    "supplies ready keys=%d foodItems=%d dodgy=%d bankOpen=%s",
                    keys, foodItems, currentDodgyNecklace, Rs2Bank.isOpen()));
            return;
        }

        if (!openBank(config)) {
            return;
        }
        if (gearPending) {
            finishGearTrip(config);
            return;
        }
        if (resumeRun) {
            finishResumeTrip(config);
            return;
        }
        bankForPickpocketing(config, hasFood, foodItems, currentDodgyNecklace, keys);
    }

    /** Opens the Civitas bank, answering the PIN screen. True once the bank is open. */
    private boolean openBank(OpraHouseThievingConfig config) {
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
                sleepUntil(() -> Rs2Bank.isOpen() || Rs2Bank.isBankPinWidgetVisible(), 5000);
                if (Rs2Bank.isBankPinWidgetVisible()) {
                    enterPinIfNeeded(config);
                    sleepUntil(Rs2Bank::isOpen, 5000);
                } else if (!Rs2Bank.isOpen()) {
                    log.warn("Bank click did not open the bank. objectId={} at {}", bankObject.getId(), bankObject.getWorldLocation());
                }
            } else if (turnedCameraThisCall) {
                return false;
            } else if (Rs2Player.getWorldLocation().distanceTo(BANKING_LOCATION) > 2) {
                log.debug("Bank is not visible, walking to {} from {}", BANKING_LOCATION, Rs2Player.getWorldLocation());
                Rs2Walker.walkTo(BANKING_LOCATION, 2);
                return false;
            } else if (bankObject == null) {
                log.warn("No bank object near {}, falling back to openBank()", BANKING_TILE_LOCATION);
                Rs2Bank.openBank();
                enterPinIfNeeded(config);
            }
        }
        return Rs2Bank.isOpen();
    }

    /** Pickpocket supplies: food, dodgy necklaces, and any banked house keys. */
    private void bankForPickpocketing(OpraHouseThievingConfig config, boolean hasFood, int foodItems,
                                      int currentDodgyNecklace, int keys) {
        log.debug("Bank deposit maxKeys={} keys={} foodItems={} dodgy={}",
                hasMaxHouseKeys(config), keys, foodItems, currentDodgyNecklace);
        if (!hasMaxHouseKeys(config))
            Rs2Bank.depositAllExcept("Coins", COIN_POUCH, HOUSE_KEYS, VALUABLES, config.foodSelection().getName(), DODGY_NECKLACE);
        else
            Rs2Bank.depositAllExcept("Coins", COIN_POUCH, HOUSE_KEYS, VALUABLES);
        Rs2Inventory.waitForInventoryChanges(600);

        if (hasMaxHouseKeys(config)) {
            return;
        }
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

    /** Auto gear: best owned gear on, everything else in, valuables and keys out. */
    private void finishGearTrip(OpraHouseThievingConfig config) {
        equipBestGear();
        depositAllButKeysAndValuables();
        if (Rs2Bank.hasItem(VALUABLES, true)) {
            Rs2Bank.withdrawAll(VALUABLES);
            Rs2Inventory.waitForInventoryChanges(1200);
        }
        if (Rs2Bank.hasItem(HOUSE_KEYS, true)) {
            Rs2Bank.withdrawAll(HOUSE_KEYS);
            Rs2Inventory.waitForInventoryChanges(1200);
        }
        gearPending = false;
        int keys = houseKeyCount();
        enter(stateAfterBank(keys, config.minHouseKeys()), String.format(
                "auto gear done keys=%d valuables=%d", keys, Rs2Inventory.count(VALUABLES)));
    }

    /** Full inventory mid-run: bank the loot, keep keys and valuables, and go straight back to the houses. */
    private void finishResumeTrip(OpraHouseThievingConfig config) {
        depositAllButKeysAndValuables();
        resumeRun = false;
        int keys = houseKeyCount();
        enter(stateAfterBank(keys, config.minHouseKeys()), String.format(
                "loot banked keys=%d emptySlots=%d", keys, Rs2Inventory.emptySlotCount()));
    }

    private void depositAllButKeysAndValuables() {
        Rs2Bank.depositAllExcept(item -> keepOnDeposit(item.getName()));
        Rs2Inventory.waitForInventoryChanges(1200);
    }

    static boolean keepOnDeposit(String name) {
        return HOUSE_KEYS.equalsIgnoreCase(name) || VALUABLES.equalsIgnoreCase(name);
    }

    static State stateAfterBank(int keys, int minKeys) {
        return keys >= minKeys ? State.FINDING_HOUSE : State.PICKPOCKETING;
    }

    /** Opens any coin pouches before a bank trip. True while it is still opening them. */
    private boolean openCoinPouches() {
        var pouches = Rs2Inventory.get(COIN_POUCH);
        if (pouches == null) {
            return false;
        }
        log.info("Opening {} coin pouches before banking", pouches.getQuantity());
        Rs2Inventory.interact(pouches, "Open-all");
        Rs2Inventory.waitForInventoryChanges(1200);
        return true;
    }

    private void enterPinIfNeeded(OpraHouseThievingConfig config) {
        if (!Rs2Bank.isBankPinWidgetVisible()) {
            return;
        }
        String pin = config.bankPin();
        if (pin == null || pin.isBlank()) {
            if (!pinWarned) {
                log.warn("Bank PIN screen is up but no PIN is set in the config");
                pinWarned = true;
            }
            return;
        }
        if (!isUsablePin(pin)) {
            if (!pinWarned) {
                log.warn("Bank PIN in the config is not a 4-digit number; skipping PIN entry");
                pinWarned = true;
            }
            return;
        }
        Microbot.status = "Entering bank PIN";
        Rs2Bank.handleBankPin(pin);
    }

    static boolean isUsablePin(String pin) {
        return pin != null && pin.matches("\\d{4}");
    }

    /** Best piece first in each slot: Rogue over Graceful, then the Graceful cape. */
    static List<List<String>> gearSlots() {
        List<List<String>> slots = new ArrayList<>();
        for (int i = 0; i < ROGUE_GEAR.size(); i++) {
            slots.add(List.of(ROGUE_GEAR.get(i), GRACEFUL_FALLBACK.get(i)));
        }
        slots.add(List.of(GRACEFUL_CAPE));
        return slots;
    }

    @Nullable
    static String bestOwned(List<String> options, Predicate<String> owned) {
        for (String option : options) {
            if (owned.test(option)) {
                return option;
            }
        }
        return null;
    }

    static boolean isBestGear(Predicate<String> worn) {
        return ROGUE_GEAR.stream().allMatch(worn) && worn.test(GRACEFUL_CAPE);
    }

    private static boolean isWorn(String name) {
        return Rs2Equipment.isWearing(name, true);
    }

    private boolean wearingBestGear() {
        return isBestGear(OpraHouseThievingScript::isWorn);
    }

    static boolean needsGearBank(boolean wearingBest, int keys) {
        return !wearingBest || keys < 1;
    }

    /** With the bank open, wears the best piece owned for each slot. Slots with nothing owned are skipped. */
    private void equipBestGear() {
        List<String> wearing = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (List<String> slot : gearSlots()) {
            String best = bestOwned(slot, name -> isWorn(name)
                    || Rs2Inventory.hasItem(name, true)
                    || Rs2Bank.hasItem(name, true));
            if (best == null) {
                missing.add(slot.get(0));
                continue;
            }
            if (!isWorn(best)) {
                if (Rs2Inventory.hasItem(best, true)) {
                    Rs2Inventory.interact(best, "Wear");
                } else {
                    Rs2Bank.withdrawAndEquip(best);
                }
                sleepUntil(() -> isWorn(best), 3000);
            }
            if (isWorn(best)) {
                wearing.add(best);
            } else {
                missing.add(best);
            }
        }
        log.info("Auto gear: wearing {}, missing {}", wearing, missing);
    }

    private static int getDodgyNecklaceAmount() {
        var dodgyNecklaces = Rs2Inventory.items(
                        (item) -> item.getName().equalsIgnoreCase(DODGY_NECKLACE))
                .collect(Collectors.toList());
        return dodgyNecklaces.size();
    }

    private void setNextThievingHouse() {
        ThievingHouse previous = currentThievingHouse;
        Set<ThievingHouse> closed = closedHouses();
        Set<ThievingHouse> locked = EnumSet.noneOf(ThievingHouse.class);
        Map<ThievingHouse, Long> lastUnlockedAt = new EnumMap<>(ThievingHouse.class);
        for (ThievingHouse house : ThievingHouse.values()) {
            lastUnlockedAt.put(house, watch(house).lastUnlockedAt);
            if (house != previous && !closed.contains(house) && hasLockedDoor(house)) {
                locked.add(house);
            }
        }
        ThievingHouse next = pickNextHouse(previous, locked, closed, lastUnlockedAt);
        boolean nothingElseOpen = next == previous || closed.contains(next);
        if (nothingElseOpen && locked.isEmpty() && (previous == null || !hasLockedDoor(previous))) {
            Set<ThievingHouse> cycled = EnumSet.noneOf(ThievingHouse.class);
            for (ThievingHouse house : closed) {
                HouseWatch watch = watch(house);
                if (watch.lastUnlockedAt >= watch.closedAt) {
                    cycled.add(house);
                }
            }
            ThievingHouse target = waitTarget(closed, cycled, lastUnlockedAt);
            if (waitingAt != target) {
                log.info("All owners home, waiting at {} scout (closed={} cycled={})",
                        target.npcName, closed, cycled);
            }
            setWaitingAt(target);
            currentThievingHouse = target;
            return;
        }
        setWaitingAt(null);
        currentThievingHouse = next;
        if (previous != next) {
            log.info("Next house {} -> {} (locked={} closed={})", previous, next, locked, closed);
        }
    }

    /**
     * With every owner home, wait where a door is likeliest to lock next: a closed house whose owner we saw come
     * home reopens the moment he leaves, and the longer an owner has been home the sooner he goes. Ties keep enum
     * order.
     */
    static ThievingHouse waitTarget(Set<ThievingHouse> closed, Set<ThievingHouse> cycled,
                                    Map<ThievingHouse, Long> lastUnlockedAt) {
        ThievingHouse best = null;
        for (ThievingHouse house : ThievingHouse.values()) {
            if (cycled.contains(house) && (best == null
                    || lastUnlockedAt.getOrDefault(house, 0L) < lastUnlockedAt.getOrDefault(best, 0L))) {
                best = house;
            }
        }
        if (best != null) {
            return best;
        }
        for (ThievingHouse house : ThievingHouse.values()) {
            if (best == null || lastUnlockedAt.getOrDefault(house, 0L) < lastUnlockedAt.getOrDefault(best, 0L)) {
                best = house;
            }
        }
        return best;
    }

    /**
     * A house with a locked door in sight wins. Otherwise the open house whose owner we saw home longest ago,
     * since he is the likeliest to be out again. Ties keep the patrol order.
     */
    static ThievingHouse pickNextHouse(ThievingHouse current, Set<ThievingHouse> lockedVisible,
                                       Set<ThievingHouse> closed, Map<ThievingHouse, Long> lastUnlockedAt) {
        ThievingHouse[] houses = ThievingHouse.values();
        int start = current == null ? 0 : current.ordinal() + 1;
        ThievingHouse best = null;
        for (int i = 0; i < houses.length; i++) {
            ThievingHouse house = houses[(start + i) % houses.length];
            if (house == current || closed.contains(house)) {
                continue;
            }
            if (best == null || ranksBefore(house, best, lockedVisible, lastUnlockedAt)) {
                best = house;
            }
        }
        if (best != null) {
            return best;
        }
        if (current != null && !closed.contains(current)) {
            return current;
        }
        return houses[start % houses.length];
    }

    private static boolean ranksBefore(ThievingHouse house, ThievingHouse best, Set<ThievingHouse> lockedVisible,
                                       Map<ThievingHouse, Long> lastUnlockedAt) {
        boolean houseLocked = lockedVisible.contains(house);
        boolean bestLocked = lockedVisible.contains(best);
        if (houseLocked != bestLocked) {
            return houseLocked;
        }
        return lastUnlockedAt.getOrDefault(house, 0L) < lastUnlockedAt.getOrDefault(best, 0L);
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
    /** The door the owner leaves unlocked while home, at the entrance or the inside tile. */
    private static Rs2TileObjectModel findUnlockedDoor(ThievingHouse house) {
        var atEntrance = Microbot.getRs2TileObjectCache().query()
                .withId(UNLOCKED_DOOR_ID)
                .nearestOnClientThread(house.lockedDoorEntrance, 2);
        if (atEntrance != null) {
            return atEntrance;
        }
        return Microbot.getRs2TileObjectCache().query()
                .withId(UNLOCKED_DOOR_ID)
                .nearestOnClientThread(house.lockedDoorEgress, 2);
    }

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

    enum ScoutMove {
        CLICK,
        APPROACH_DOOR,
        PATROL
    }

    static final class ScoutDoor {
        final ThievingHouse house;
        final boolean visible;
        final int distance;
        final boolean ownerHome;

        ScoutDoor(ThievingHouse house, boolean visible, int distance) {
            this(house, visible, distance, false);
        }

        ScoutDoor(ThievingHouse house, boolean visible, int distance, boolean ownerHome) {
            this.house = house;
            this.visible = visible;
            this.distance = distance;
            this.ownerHome = ownerHome;
        }
    }

    static final class ScoutChoice {
        final ScoutMove move;
        final ThievingHouse house;

        private ScoutChoice(ScoutMove move, ThievingHouse house) {
            this.move = move;
            this.house = house;
        }
    }

    /**
     * A visible door is clicked at any distance. An off-screen door is walked toward. Closed houses are
     * ignored. A door whose owner is still close is only walked toward, and only when no free door is loaded.
     * No remaining door keeps the patrol.
     */
    static ScoutChoice chooseScoutDoor(ThievingHouse current, List<ScoutDoor> doors) {
        return chooseScoutDoor(current, doors, EnumSet.noneOf(ThievingHouse.class));
    }

    static ScoutChoice chooseScoutDoor(ThievingHouse current, List<ScoutDoor> doors, ThievingHouse ignore) {
        return chooseScoutDoor(current, doors,
                ignore == null ? EnumSet.noneOf(ThievingHouse.class) : EnumSet.of(ignore));
    }

    static ScoutChoice chooseScoutDoor(ThievingHouse current, List<ScoutDoor> doors, Set<ThievingHouse> ignore) {
        ScoutDoor currentVisible = null;
        ScoutDoor nearestVisible = null;
        ScoutDoor nearestLoaded = null;
        ScoutDoor nearestOwnerHeld = null;
        if (doors != null) {
            for (ScoutDoor door : doors) {
                if (door == null || door.house == null || ignore.contains(door.house)) {
                    continue;
                }
                if (door.ownerHome) {
                    nearestOwnerHeld = nearerDoor(nearestOwnerHeld, door, current);
                    continue;
                }
                if (door.visible) {
                    if (door.house == current) {
                        currentVisible = door;
                    }
                    nearestVisible = nearerDoor(nearestVisible, door, current);
                }
                nearestLoaded = nearerDoor(nearestLoaded, door, current);
            }
        }
        ScoutDoor chosen = currentVisible != null ? currentVisible : nearestVisible;
        if (chosen == null) {
            chosen = nearestLoaded;
        }
        if (chosen == null && nearestOwnerHeld != null) {
            return new ScoutChoice(ScoutMove.APPROACH_DOOR, nearestOwnerHeld.house);
        }
        if (chosen == null) {
            return new ScoutChoice(ScoutMove.PATROL, null);
        }
        if (chosen.visible) {
            return new ScoutChoice(ScoutMove.CLICK, chosen.house);
        }
        return new ScoutChoice(ScoutMove.APPROACH_DOOR, chosen.house);
    }

    static boolean isInsideHouse(ThievingHouse house, WorldPoint player) {
        if (house == null || player == null) {
            return false;
        }
        int x = player.getX();
        int y = player.getY();
        switch (house) {
            case LAVINIA:
                return x >= 1666 && x <= 1676 && y >= 3080 && y <= 3094;
            case VICTOR:
                return x >= 1632 && x <= 1640 && y >= 3093 && y <= 3103;
            case CAIUS:
                return x >= 1631 && x <= 1640 && y >= 3117 && y <= 3124;
            default:
                return false;
        }
    }

    private static ScoutDoor nearerDoor(ScoutDoor chosen, ScoutDoor candidate, ThievingHouse preferred) {
        if (chosen == null || candidate.distance < chosen.distance) {
            return candidate;
        }
        if (candidate.distance == chosen.distance && candidate.house == preferred) {
            return candidate;
        }
        return chosen;
    }

    @Override
    public void shutdown() {
        log.info("Stopping Opra House Thieving script");
        scoutDestination = null;
        lastDoorTile = null;
        doorMisses = 0;
        watches.clear();
        homeownerReturning = false;
        kickedOut = false;
        shineSeen = false;
        outsideSamples = 0;
        claimedHint = null;
        missingHintLogged = null;
        lastDoorScan = null;
        waitingOwnerHouse = null;
        waitingOwnerDistance = -1;
        setWaitingAt(null);
        waitingSinceMs = 0;
        lastWaitLogMs = 0;
        lastNoDoorPick = null;
        gearPending = false;
        gearChecked = false;
        resumeRun = false;
        pinWarned = false;
        cachedPath = null;
        cachedPathDestination = null;
        lastStepMs = 0;
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
        log.info("Opra House Thieving script stopped");
    }

}
