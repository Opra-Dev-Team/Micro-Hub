package net.runelite.client.plugins.microbot.sweetcornchef;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SweetcornChefScript extends Script {

    static final WorldPoint OVEN_TILE = new WorldPoint(1379, 3054, 0);
    static final WorldPoint FIELD_TILE = new WorldPoint(1379, 3053, 0);

    static final int OVEN_ID = 57332;
    static final int PLANT_ID_A = 51829;
    static final int PLANT_ID_B = 51830;
    static final int[] PLANT_IDS = {PLANT_ID_A, PLANT_ID_B};
    static final int RAW_ID = ItemID.SWEETCORN;
    static final int COOKED_ID = ItemID.COOKED_SWEETCORN;
    static final int BURNT_ID = ItemID.BURNT_SWEETCORN;
    static final int SEED_ID = ItemID.SWEETCORN_SEED;
    static final int INVENTORY_SIZE = 28;
    static final int KEEP_SEED_TARGET = 27;

    private static final int FIELD_RANGE = 25;
    private static final long PICK_IDLE_MS = 3_500L;
    private static final long FAST_IDLE_MS = 2_200L;
    private static final long SPAM_STALL_MS = 1_800L;
    private static final long PLANT_GONE_GRACE_MS = 1_200L;
    private static final long SPAM_GONE_GRACE_MS = 250L;
    private static final long DEPLETED_SKIP_MS = 10_000L;
    private static final long COOK_TIMEOUT_MS = 90_000L;
    private static final int COOK_OPTION_MISSES = 5;

    private static final int[][] CARDINAL = {
            {1, 0},
            {-1, 0},
            {0, 1},
            {0, -1}
    };

    public volatile State state = State.PICK;
    public volatile int cooked;
    public volatile int tripsCompleted;
    public volatile int cookedPerHourSnapshot;

    public volatile long scriptStartEpochMs;

    private boolean routedStart;
    private WorldPoint currentPlant;
    private int rawAtClick;
    private long lastRawGainMs;
    private boolean awaitingYield;
    private long lastPickClickMs;
    private long nextPickGapMs;
    private long lastSlowTickMs;
    private WorldPoint depletedTile;
    private long depletedUntilMs;

    private boolean ovenClicked;
    private boolean productionStarted;
    private int lastRawSeen;
    private long lastCookProgressMs;
    private int cookOptionMisses;
    private boolean dropStarted;
    private int cookedAtDropStart;
    private SweetcornChefConfig config;

    public boolean run(SweetcornChefConfig config) {
        shutdown();
        this.config = config;
        state = State.PICK;
        cooked = 0;
        tripsCompleted = 0;
        cookedPerHourSnapshot = 0;
        routedStart = false;
        resetPickLatch();
        resetCookLatch();
        resetDropLatch();

        Microbot.status = "Starting Sweetcorn Chef";
        applyAntiban();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    return;
                }
                if (!super.run()) {
                    return;
                }
                routeStart();
                long now = System.currentTimeMillis();
                if (state != State.PICK && now - lastSlowTickMs < 200) {
                    return;
                }
                if (state != State.PICK) {
                    lastSlowTickMs = now;
                }
                switch (state) {
                    case PICK:
                        pick();
                        break;
                    case COOK:
                        cook();
                        break;
                    case DROP:
                        drop();
                        break;
                    default:
                        break;
                }
            } catch (Exception ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    return;
                }
                Microbot.logStackTrace(getClass().getSimpleName(), ex);
            }
        }, 0, 50, TimeUnit.MILLISECONDS);

        return true;
    }

    private void applyAntiban() {
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyHunterSetup();
        Rs2Antiban.setActivity(Activity.GENERAL_COLLECTING);
        Rs2Antiban.setActivityIntensity(ActivityIntensity.HIGH);
    }

    private void routeStart() {
        if (routedStart) {
            return;
        }
        routedStart = true;
        if (hasCookedOrBurnt()) {
            state = State.DROP;
            return;
        }
        if (inventoryReadyToCook()) {
            state = State.COOK;
            return;
        }
        state = State.PICK;
    }

    private void pick() {
        if (hasCookedOrBurnt()) {
            resetPickLatch();
            state = State.DROP;
            return;
        }
        if (inventoryReadyToCook()) {
            leaveField();
            return;
        }

        int raw = Rs2Inventory.count(RAW_ID);
        PickSpeed speed = config.pickSpeed();
        if (currentPlant != null) {
            if (raw > rawAtClick) {
                rawAtClick = raw;
                lastRawGainMs = System.currentTimeMillis();
                awaitingYield = false;
            }
            if (inventoryReadyToCook()) {
                leaveField();
                return;
            }
            Rs2TileObjectModel stillThere = findPlantAt(currentPlant);
            long now = System.currentTimeMillis();
            long sinceClick = now - lastPickClickMs;
            long sinceYield = now - lastRawGainMs;
            boolean busy = Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Player.isInteracting();
            long goneGrace = speed == PickSpeed.SPAM ? SPAM_GONE_GRACE_MS : PLANT_GONE_GRACE_MS;
            if (stillThere == null && !Rs2Player.isMoving() && sinceClick > goneGrace) {
                Microbot.status = "Sweetcorn depleted, next plant";
                markDepleted(currentPlant);
                currentPlant = null;
                return;
            }
            if (speed == PickSpeed.SPAM && sinceYield > SPAM_STALL_MS && lastPickClickMs > 0) {
                Microbot.status = "Sweetcorn depleted, next plant";
                markDepleted(currentPlant);
                currentPlant = null;
                return;
            }
            if (speed != PickSpeed.SPAM && busy) {
                Microbot.status = "Picking sweetcorn";
                return;
            }
            if (speed != PickSpeed.SPAM && awaitingYield && sinceClick > idleMs(speed)) {
                Microbot.status = "Sweetcorn depleted, next plant";
                markDepleted(currentPlant);
                currentPlant = null;
                return;
            }
            if (speed == PickSpeed.SPAM) {
                if (stillThere != null && !Rs2Player.isMoving() && pickClickReady()) {
                    clickPlant(stillThere, raw, speed);
                }
                return;
            }
            if (stillThere != null && !awaitingYield && pickClickReady()) {
                clickPlant(stillThere, raw, speed);
            }
            return;
        }

        Set<WorldPoint> plantTiles = loadPlantTiles();
        Rs2TileObjectModel next = closestOuterPlant(plantTiles);
        if (next == null) {
            Microbot.status = "Waiting for outer sweetcorn";
            return;
        }
        if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Player.isInteracting()) {
            return;
        }
        clickPlant(next, raw, speed);
    }

    private void clickPlant(Rs2TileObjectModel plant, int rawNow, PickSpeed speed) {
        Microbot.status = "Picking sweetcorn";
        if (speed == PickSpeed.NORMAL) {
            Rs2Antiban.actionCooldown();
        }
        Rs2Camera.turnTo(plant);
        plant.click("Pick");
        currentPlant = plant.getWorldLocation();
        rawAtClick = rawNow;
        lastPickClickMs = System.currentTimeMillis();
        if (lastRawGainMs == 0) {
            lastRawGainMs = lastPickClickMs;
        }
        awaitingYield = true;
        nextPickGapMs = gapFor(speed);
    }

    private boolean pickClickReady() {
        return System.currentTimeMillis() - lastPickClickMs >= nextPickGapMs;
    }

    private long gapFor(PickSpeed speed) {
        if (speed == PickSpeed.SPAM) {
            return 50;
        }
        if (speed == PickSpeed.FAST) {
            return Rs2Random.betweenInclusive(250, 400);
        }
        return Rs2Random.betweenInclusive(700, 1100);
    }

    private long idleMs(PickSpeed speed) {
        if (speed == PickSpeed.FAST) {
            return FAST_IDLE_MS;
        }
        return PICK_IDLE_MS;
    }

    private void leaveField() {
        resetPickLatch();
        Rs2Antiban.actionCooldown();
        state = State.COOK;
    }

    private boolean inventoryReadyToCook() {
        return Rs2Inventory.isFull() || Rs2Inventory.count(RAW_ID) >= targetRawCount();
    }

    private int targetRawCount() {
        if (config != null
                && config.seedHandling() == SeedHandling.KEEP
                && Rs2Inventory.contains(SEED_ID)) {
            return KEEP_SEED_TARGET;
        }
        return INVENTORY_SIZE;
    }

    private void cook() {
        int rawLeft = Rs2Inventory.count(RAW_ID);
        if (rawLeft == 0) {
            Microbot.status = "Cooking finished";
            Rs2Antiban.actionCooldown();
            resetCookLatch();
            state = State.DROP;
            return;
        }
        noteCookProgress(rawLeft);

        if (productionStarted) {
            Microbot.status = "Cooking sweetcorn";
            if (cookStalled(rawLeft)) {
                log.warn("Cook stalled with raw={}", rawLeft);
                resetCookLatch();
                state = State.DROP;
            }
            return;
        }

        if (isCookInterfaceOpen()) {
            Microbot.status = "Making cooked sweetcorn";
            if (startCookProduction()) {
                productionStarted = true;
                ovenClicked = true;
                cookOptionMisses = 0;
                lastRawSeen = rawLeft;
                lastCookProgressMs = System.currentTimeMillis();
                log.info("Sweetcorn cooking started");
            } else {
                cookOptionMisses++;
                if (cookOptionMisses >= COOK_OPTION_MISSES) {
                    Microbot.status = "Cannot find Cooked sweetcorn on the oven. Stopping.";
                    log.warn("Cook option missing");
                    shutdown();
                }
            }
            return;
        }

        if (ovenClicked) {
            Microbot.status = "Waiting on oven";
            if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Player.isInteracting()) {
                lastCookProgressMs = System.currentTimeMillis();
                return;
            }
            if (System.currentTimeMillis() - lastCookProgressMs > 6_000L) {
                log.warn("Oven click did not open the cook interface");
                ovenClicked = false;
            }
            return;
        }

        Rs2TileObjectModel oven = findOven();
        if (oven == null) {
            Microbot.status = "Waiting for oven";
            return;
        }

        Microbot.status = "Clicking oven";
        Rs2Antiban.actionCooldown();
        Rs2Camera.turnTo(oven);
        oven.click("Cook");
        ovenClicked = true;
        lastCookProgressMs = System.currentTimeMillis();
        lastRawSeen = rawLeft;
    }

    private void noteCookProgress(int rawLeft) {
        if (rawLeft < lastRawSeen) {
            lastRawSeen = rawLeft;
            lastCookProgressMs = System.currentTimeMillis();
        }
    }

    private boolean cookStalled(int rawLeft) {
        if (Rs2Player.isAnimating() || Rs2Player.isInteracting()) {
            return false;
        }
        if (isCookInterfaceOpen() && System.currentTimeMillis() - lastCookProgressMs < 8_000L) {
            return false;
        }
        if (rawLeft == 0) {
            return false;
        }
        return System.currentTimeMillis() - lastCookProgressMs > COOK_TIMEOUT_MS;
    }

    private boolean isCookInterfaceOpen() {
        return Rs2Widget.isProductionWidgetOpen()
                || Rs2Widget.hasWidget("What would you like to cook?");
    }

    private boolean startCookProduction() {
        if (Rs2Widget.isProductionWidgetOpen()) {
            Rs2Widget.enableQuantityOption("All");
            if (Rs2Widget.handleProcessingInterface("Cooked sweetcorn")) {
                return true;
            }
            return Rs2Widget.handleProcessingInterface("Sweetcorn");
        }
        if (Rs2Widget.hasWidget("Cooked sweetcorn")) {
            return Rs2Widget.clickWidget("Cooked sweetcorn", false);
        }
        if (Rs2Widget.hasWidget("Sweetcorn")) {
            return Rs2Widget.clickWidget("Sweetcorn", false);
        }
        return false;
    }

    private void drop() {
        if (!hasItemsToDrop()) {
            if (dropStarted) {
                cooked += Math.max(0, cookedAtDropStart);
                tripsCompleted++;
                updateCookedPerHour();
                if (reachedStopLimit()) {
                    stopAtGoal();
                    return;
                }
            }
            Microbot.status = "Inventory clear";
            Rs2Antiban.actionCooldown();
            if (Rs2Random.dicePercentage(12)) {
                Rs2Antiban.moveMouseRandomly();
            }
            resetDropLatch();
            resetPickLatch();
            resetCookLatch();
            state = State.PICK;
            return;
        }

        if (!dropStarted) {
            cookedAtDropStart = Rs2Inventory.count(COOKED_ID);
            dropStarted = true;
        }

        Microbot.status = "Dropping sweetcorn";
        if (shouldDropSeeds()) {
            Rs2Inventory.dropAll(COOKED_ID, BURNT_ID, RAW_ID, SEED_ID);
        } else {
            Rs2Inventory.dropAll(COOKED_ID, BURNT_ID, RAW_ID);
        }
    }

    private boolean shouldDropSeeds() {
        return config == null || config.seedHandling() != SeedHandling.KEEP;
    }

    private boolean hasItemsToDrop() {
        if (Rs2Inventory.contains(RAW_ID)
                || Rs2Inventory.contains(COOKED_ID)
                || Rs2Inventory.contains(BURNT_ID)) {
            return true;
        }
        return shouldDropSeeds() && Rs2Inventory.contains(SEED_ID);
    }

    private Rs2TileObjectModel closestOuterPlant(Set<WorldPoint> plantTiles) {
        WorldPoint playerTile = Rs2Player.getWorldLocation();
        final WorldPoint from = playerTile != null ? playerTile : FIELD_TILE;
        return Microbot.getRs2TileObjectCache().query()
                .withIds(PLANT_IDS)
                .where(obj -> isOuterFieldPlant(obj, plantTiles))
                .nearestOnClientThread(from, FIELD_RANGE);
    }

    private Set<WorldPoint> loadPlantTiles() {
        Set<WorldPoint> tiles = new HashSet<>();
        List<Rs2TileObjectModel> plants = Microbot.getRs2TileObjectCache().query()
                .withIds(PLANT_IDS)
                .where(this::isNamedPlant)
                .toListOnClientThread();
        if (plants == null) {
            return tiles;
        }
        for (Rs2TileObjectModel plant : plants) {
            if (plant != null && plant.getWorldLocation() != null) {
                tiles.add(plant.getWorldLocation());
            }
        }
        return tiles;
    }

    private Rs2TileObjectModel findPlantAt(WorldPoint tile) {
        if (tile == null) {
            return null;
        }
        return Microbot.getRs2TileObjectCache().query()
                .withIds(PLANT_IDS)
                .where(obj -> isNamedPlant(obj) && tile.equals(obj.getWorldLocation()))
                .nearestOnClientThread(tile, 2);
    }

    private boolean isOuterFieldPlant(Rs2TileObjectModel obj, Set<WorldPoint> plantTiles) {
        if (!isNamedPlant(obj)) {
            return false;
        }
        WorldPoint tile = obj.getWorldLocation();
        if (tile.distanceTo(FIELD_TILE) > FIELD_RANGE) {
            return false;
        }
        if (isDepleted(tile)) {
            return false;
        }
        return hasPathSideNeighbor(tile, plantTiles);
    }

    private boolean hasPathSideNeighbor(WorldPoint tile, Set<WorldPoint> plantTiles) {
        for (int[] delta : CARDINAL) {
            WorldPoint neighbor = new WorldPoint(
                    tile.getX() + delta[0],
                    tile.getY() + delta[1],
                    tile.getPlane());
            if (plantTiles.contains(neighbor)) {
                continue;
            }
            if (Rs2Tile.isTileReachable(neighbor)) {
                return true;
            }
        }
        return false;
    }

    private boolean isNamedPlant(Rs2TileObjectModel obj) {
        if (obj == null || obj.getWorldLocation() == null) {
            return false;
        }
        int id = obj.getId();
        if (id == PLANT_ID_A || id == PLANT_ID_B) {
            return true;
        }
        return obj.getName() != null && obj.getName().equalsIgnoreCase("Sweetcorn");
    }

    private Rs2TileObjectModel findOven() {
        WorldPoint from = Rs2Player.getWorldLocation();
        if (from == null) {
            from = FIELD_TILE;
        }
        Rs2TileObjectModel oven = Microbot.getRs2TileObjectCache().query()
                .withId(OVEN_ID)
                .nearestOnClientThread(from, FIELD_RANGE);
        if (oven != null) {
            return oven;
        }
        return Microbot.getRs2TileObjectCache().query()
                .withName("Oven")
                .nearestOnClientThread(from, FIELD_RANGE);
    }

    private void markDepleted(WorldPoint tile) {
        depletedTile = tile;
        depletedUntilMs = System.currentTimeMillis() + DEPLETED_SKIP_MS;
    }

    private boolean isDepleted(WorldPoint tile) {
        return depletedTile != null
                && depletedTile.equals(tile)
                && System.currentTimeMillis() < depletedUntilMs;
    }

    private int distanceTo(WorldPoint tile) {
        WorldPoint loc = Rs2Player.getWorldLocation();
        if (loc == null || tile == null) {
            return Integer.MAX_VALUE;
        }
        return loc.distanceTo(tile);
    }

    private boolean hasCookedOrBurnt() {
        return Rs2Inventory.contains(COOKED_ID) || Rs2Inventory.contains(BURNT_ID);
    }

    private boolean reachedStopLimit() {
        int limit = config == null ? 0 : config.stopAfter();
        return limit > 0 && tripsCompleted >= limit;
    }

    private void stopAtGoal() {
        int limit = config == null ? 0 : config.stopAfter();
        Microbot.status = "Completed " + tripsCompleted + "/" + limit + " trips. Stopping.";
        shutdown();
    }

    private void updateCookedPerHour() {
        if (scriptStartEpochMs <= 0 || cooked <= 0) {
            return;
        }
        long runtimeMs = System.currentTimeMillis() - scriptStartEpochMs;
        double hours = runtimeMs / 3_600_000.0;
        if (hours > 0) {
            cookedPerHourSnapshot = (int) (cooked / hours);
        }
    }

    private void resetPickLatch() {
        currentPlant = null;
        rawAtClick = 0;
        lastRawGainMs = 0;
        awaitingYield = false;
        lastPickClickMs = 0;
        nextPickGapMs = 0;
        depletedTile = null;
        depletedUntilMs = 0;
    }

    private void resetCookLatch() {
        ovenClicked = false;
        productionStarted = false;
        lastRawSeen = 0;
        lastCookProgressMs = 0;
        cookOptionMisses = 0;
    }

    private void resetDropLatch() {
        dropStarted = false;
        cookedAtDropStart = 0;
    }

    @Override
    public void shutdown() {
        resetPickLatch();
        resetCookLatch();
        resetDropLatch();
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }

    public enum State {
        PICK,
        COOK,
        DROP
    }
}
