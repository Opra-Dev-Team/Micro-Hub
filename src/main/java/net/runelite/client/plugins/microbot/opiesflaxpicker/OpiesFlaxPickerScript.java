package net.runelite.client.plugins.microbot.opiesflaxpicker;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.concurrent.TimeUnit;

@Slf4j
public class OpiesFlaxPickerScript extends Script {

    static final WorldPoint FLAX_TILE = new WorldPoint(1373, 3320, 0);
    static final WorldPoint SPIN_TILE = new WorldPoint(1372, 3314, 0);
    static final WorldPoint BANK_TILE = new WorldPoint(1386, 3309, 0);

    static final int FLAX_ID = ItemID.FLAX;
    static final int BOW_STRING_ID = ItemID.BOW_STRING;
    static final int LINEN_YARN_ID = ItemID.LINEN_YARN;
    static final int INVENTORY_SIZE = 28;

    private static final int FIELD_RANGE = 25;
    private static final int WHEEL_RANGE = 8;
    private static final long PICK_IDLE_MS = 3_500L;
    private static final long FAST_IDLE_MS = 2_200L;
    private static final long SPAM_STALL_MS = 1_800L;
    private static final long PLANT_GONE_GRACE_MS = 1_200L;
    private static final long SPAM_GONE_GRACE_MS = 250L;
    private static final long DEPLETED_SKIP_MS = 10_000L;
    private static final long SPIN_TIMEOUT_MS = 90_000L;
    private static final int SPIN_OPTION_MISSES = 5;

    public volatile State state = State.BANK;
    public volatile int banked;
    public volatile int tripsCompleted;
    public volatile int perHourSnapshot;

    public volatile long scriptStartEpochMs;

    private boolean routedStart;
    private WorldPoint currentPlant;
    private int flaxAtClick;
    private long lastFlaxGainMs;
    private boolean awaitingYield;
    private long lastPickClickMs;
    private long nextPickGapMs;
    private long lastSlowTickMs;
    private WorldPoint depletedTile;
    private long depletedUntilMs;

    private boolean wheelClicked;
    private boolean productionStarted;
    private int lastFlaxSeen;
    private long lastSpinProgressMs;
    private int spinOptionMisses;

    public boolean run(OpiesFlaxPickerConfig config) {
        shutdown();
        state = State.BANK;
        banked = 0;
        tripsCompleted = 0;
        perHourSnapshot = 0;
        routedStart = false;
        resetPickLatch();
        resetSpinLatch();

        Microbot.status = "Starting Flax Picker";
        applyAntiban(config);

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
                    case BANK:
                        bank(config);
                        break;
                    case PICK:
                        pick(config);
                        break;
                    case SPIN:
                        spin(config);
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

    private void applyAntiban(OpiesFlaxPickerConfig config) {
        Rs2Antiban.resetAntibanSettings();
        if (config.mode() == FlaxMode.PICK_ONLY) {
            Rs2Antiban.antibanSetupTemplates.applyHunterSetup();
            Rs2Antiban.setActivity(Activity.GENERAL_COLLECTING);
            Rs2Antiban.setActivityIntensity(ActivityIntensity.HIGH);
        } else {
            Rs2Antiban.antibanSetupTemplates.applyCraftingSetup();
            Rs2Antiban.setActivity(Activity.GENERAL_CRAFTING);
            Rs2Antiban.setActivityIntensity(ActivityIntensity.LOW);
        }
    }

    private void routeStart() {
        if (routedStart) {
            return;
        }
        routedStart = true;
        state = Rs2Inventory.isEmpty() ? State.PICK : State.BANK;
    }

    private void bank(OpiesFlaxPickerConfig config) {
        enterPinIfNeeded(config);
        if (!Rs2Bank.isOpen()) {
            if (distanceTo(BANK_TILE) > 10) {
                Microbot.status = "Walking to Nemus Retreat bank";
            }
            if (!Rs2Bank.walkToBankAndUseBank(BankLocation.NEMUS_RETREAT)) {
                Microbot.status = "Opening Nemus Retreat bank";
                return;
            }
        }
        enterPinIfNeeded(config);
        if (!Rs2Bank.isOpen()) {
            return;
        }

        int productBefore = Rs2Inventory.count(productId(config.mode()));
        if (!Rs2Inventory.isEmpty()) {
            Microbot.status = "Depositing inventory";
            Rs2Bank.depositAll();
            sleepUntil(Rs2Inventory::isEmpty, 4000);
            if (!Rs2Inventory.isEmpty()) {
                return;
            }
            if (productBefore > 0) {
                banked += productBefore;
                updatePerHour();
                tripsCompleted++;
                if (reachedStopLimit(config)) {
                    stopAtGoal(config);
                    return;
                }
            }
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
        Rs2Antiban.takeMicroBreakByChance();
        if (Rs2Random.dicePercentage(12)) {
            Rs2Antiban.moveMouseRandomly();
        }
        resetPickLatch();
        resetSpinLatch();
        state = State.PICK;
    }

    private void pick(OpiesFlaxPickerConfig config) {
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            return;
        }
        if (inventoryReadyToLeave()) {
            leaveField(config);
            return;
        }
        if (distanceTo(FLAX_TILE) > 12) {
            Microbot.status = "Walking to the flax field";
            if (!Rs2Player.isMoving()) {
                Rs2Walker.walkTo(FLAX_TILE, 4);
            }
            return;
        }

        int flax = Rs2Inventory.count(FLAX_ID);
        PickSpeed speed = config.pickSpeed();
        if (currentPlant != null) {
            if (flax > flaxAtClick) {
                flaxAtClick = flax;
                lastFlaxGainMs = System.currentTimeMillis();
                awaitingYield = false;
            }
            if (inventoryReadyToLeave()) {
                leaveField(config);
                return;
            }
            Rs2TileObjectModel stillThere = findFlaxAt(currentPlant);
            long now = System.currentTimeMillis();
            long sinceClick = now - lastPickClickMs;
            long sinceYield = now - lastFlaxGainMs;
            boolean busy = Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Player.isInteracting();
            long goneGrace = speed == PickSpeed.SPAM ? SPAM_GONE_GRACE_MS : PLANT_GONE_GRACE_MS;
            if (stillThere == null && !Rs2Player.isMoving() && sinceClick > goneGrace) {
                Microbot.status = "Flax depleted, next plant";
                markDepleted(currentPlant);
                currentPlant = null;
                return;
            }
            if (speed == PickSpeed.SPAM && sinceYield > SPAM_STALL_MS && lastPickClickMs > 0) {
                Microbot.status = "Flax depleted, next plant";
                markDepleted(currentPlant);
                currentPlant = null;
                return;
            }
            if (speed != PickSpeed.SPAM && busy) {
                Microbot.status = "Picking flax";
                return;
            }
            if (speed != PickSpeed.SPAM && awaitingYield && sinceClick > idleMs(speed)) {
                Microbot.status = "Flax depleted, next plant";
                markDepleted(currentPlant);
                currentPlant = null;
                return;
            }
            if (stillThere != null && pickClickReady()) {
                clickFlax(stillThere, flax, speed);
            }
            return;
        }

        Rs2TileObjectModel next = closestFlax();
        if (next == null) {
            Microbot.status = "Waiting for flax";
            return;
        }
        if (Rs2Player.isMoving()) {
            return;
        }
        clickFlax(next, flax, config.pickSpeed());
    }

    private void clickFlax(Rs2TileObjectModel plant, int flaxNow, PickSpeed speed) {
        Microbot.status = "Picking flax";
        if (speed == PickSpeed.NORMAL) {
            Rs2Antiban.actionCooldown();
        }
        plant.click("Pick");
        currentPlant = plant.getWorldLocation();
        flaxAtClick = flaxNow;
        lastPickClickMs = System.currentTimeMillis();
        if (lastFlaxGainMs == 0) {
            lastFlaxGainMs = lastPickClickMs;
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

    private void leaveField(OpiesFlaxPickerConfig config) {
        resetPickLatch();
        Rs2Antiban.actionCooldown();
        if (config.mode() == FlaxMode.PICK_ONLY || Rs2Inventory.count(FLAX_ID) == 0) {
            state = State.BANK;
        } else {
            state = State.SPIN;
        }
    }

    private boolean inventoryReadyToLeave() {
        return Rs2Inventory.isFull() || Rs2Inventory.count(FLAX_ID) >= INVENTORY_SIZE;
    }

    private void spin(OpiesFlaxPickerConfig config) {
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            return;
        }
        int flaxLeft = Rs2Inventory.count(FLAX_ID);
        if (flaxLeft == 0) {
            Microbot.status = "Spinning finished";
            Rs2Antiban.actionCooldown();
            resetSpinLatch();
            state = State.BANK;
            return;
        }
        noteSpinProgress(flaxLeft);

        if (productionStarted) {
            Microbot.status = "Spinning " + productLabel(config.mode());
            if (spinStalled(flaxLeft)) {
                log.warn("Spin stalled with flax={}", flaxLeft);
                resetSpinLatch();
                state = State.BANK;
            }
            return;
        }

        if (isSpinInterfaceOpen()) {
            Microbot.status = "Making " + productLabel(config.mode());
            if (startSpin(config.mode())) {
                productionStarted = true;
                wheelClicked = true;
                spinOptionMisses = 0;
                lastFlaxSeen = flaxLeft;
                lastSpinProgressMs = System.currentTimeMillis();
                log.info("Spinning {} started", productLabel(config.mode()));
            } else {
                spinOptionMisses++;
                if (spinOptionMisses >= SPIN_OPTION_MISSES) {
                    Microbot.status = "Cannot find " + productLabel(config.mode()) + " on the spinning wheel. Stopping.";
                    log.warn("Spinning option missing for {}", config.mode());
                    shutdown();
                }
            }
            return;
        }

        if (wheelClicked) {
            Microbot.status = "Waiting on spinning wheel";
            if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Player.isInteracting()) {
                lastSpinProgressMs = System.currentTimeMillis();
                return;
            }
            if (System.currentTimeMillis() - lastSpinProgressMs > 6_000L) {
                log.warn("Spinning wheel click did not open the interface");
                wheelClicked = false;
            }
            return;
        }

        if (distanceTo(SPIN_TILE) > 5) {
            Microbot.status = "Walking to the spinning wheel";
            if (!Rs2Player.isMoving()) {
                Rs2Walker.walkTo(SPIN_TILE, 2);
            }
            return;
        }

        Rs2TileObjectModel wheel = findWheel();
        if (wheel == null) {
            Microbot.status = "Cannot see the spinning wheel";
            return;
        }
        Microbot.status = "Clicking spinning wheel";
        Rs2Antiban.actionCooldown();
        wheel.click("Spin");
        wheelClicked = true;
        lastSpinProgressMs = System.currentTimeMillis();
        lastFlaxSeen = flaxLeft;
    }

    private void noteSpinProgress(int flaxLeft) {
        if (flaxLeft < lastFlaxSeen) {
            lastFlaxSeen = flaxLeft;
            lastSpinProgressMs = System.currentTimeMillis();
        }
    }

    private boolean spinStalled(int flaxLeft) {
        if (Rs2Player.isAnimating() || Rs2Player.isInteracting()) {
            return false;
        }
        if (isSpinInterfaceOpen() && System.currentTimeMillis() - lastSpinProgressMs < 8_000L) {
            return false;
        }
        if (flaxLeft == 0) {
            return false;
        }
        return System.currentTimeMillis() - lastSpinProgressMs > SPIN_TIMEOUT_MS;
    }

    private boolean isSpinInterfaceOpen() {
        return Rs2Widget.isProductionWidgetOpen()
                || Rs2Widget.hasWidget("What would you like to spin?");
    }

    private boolean startSpin(FlaxMode mode) {
        String product = productLabel(mode);
        if (Rs2Widget.isProductionWidgetOpen()) {
            Rs2Widget.enableQuantityOption("All");
            return Rs2Widget.handleProcessingInterface(product);
        }
        if (Rs2Widget.hasWidget(product)) {
            return Rs2Widget.clickWidget(product, false);
        }
        return false;
    }

    private Rs2TileObjectModel closestFlax() {
        WorldPoint from = Rs2Player.getWorldLocation();
        if (from == null) {
            from = FLAX_TILE;
        }
        return Microbot.getRs2TileObjectCache().query()
                .withNameContains("flax")
                .where(this::isFieldFlax)
                .nearestOnClientThread(from, FIELD_RANGE);
    }

    private Rs2TileObjectModel findFlaxAt(WorldPoint tile) {
        if (tile == null) {
            return null;
        }
        return Microbot.getRs2TileObjectCache().query()
                .withNameContains("flax")
                .where(obj -> isNamedFlax(obj) && tile.equals(obj.getWorldLocation()))
                .nearestOnClientThread(tile, 2);
    }

    private boolean isFieldFlax(Rs2TileObjectModel obj) {
        if (!isNamedFlax(obj)) {
            return false;
        }
        if (obj.getWorldLocation().distanceTo(FLAX_TILE) > FIELD_RANGE) {
            return false;
        }
        return !isDepleted(obj.getWorldLocation());
    }

    private boolean isNamedFlax(Rs2TileObjectModel obj) {
        return obj != null
                && obj.getName() != null
                && obj.getWorldLocation() != null
                && obj.getName().equalsIgnoreCase("flax");
    }

    private Rs2TileObjectModel findWheel() {
        return Microbot.getRs2TileObjectCache().query()
                .withNameContains("spinning wheel")
                .where(obj -> obj.getWorldLocation() != null
                        && obj.getWorldLocation().distanceTo(SPIN_TILE) <= WHEEL_RANGE)
                .nearestOnClientThread(SPIN_TILE, 15);
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

    static int productId(FlaxMode mode) {
        if (mode == FlaxMode.BOW_STRING) {
            return BOW_STRING_ID;
        }
        if (mode == FlaxMode.LINEN_YARN) {
            return LINEN_YARN_ID;
        }
        return FLAX_ID;
    }

    static String productLabel(FlaxMode mode) {
        if (mode == FlaxMode.BOW_STRING) {
            return "Bow string";
        }
        if (mode == FlaxMode.LINEN_YARN) {
            return "Linen yarn";
        }
        return "Flax";
    }

    private void enterPinIfNeeded(OpiesFlaxPickerConfig config) {
        if (!Rs2Bank.isBankPinWidgetVisible()) {
            return;
        }
        String pin = config.bankPin();
        if (pin == null || pin.isBlank()) {
            return;
        }
        if (pin.length() != 4 || !pin.matches("\\d{4}")) {
            log.warn("Bank PIN is not a valid 4-digit number; skipping PIN entry.");
            return;
        }
        Microbot.status = "Entering bank PIN";
        Rs2Bank.handleBankPin(pin);
    }

    private boolean reachedStopLimit(OpiesFlaxPickerConfig config) {
        int limit = config.stopAfter();
        return limit > 0 && banked >= limit;
    }

    private void stopAtGoal(OpiesFlaxPickerConfig config) {
        Microbot.status = "Banked " + banked + "/" + config.stopAfter() + " " + productLabel(config.mode()) + ". Stopping.";
        shutdown();
    }

    private void updatePerHour() {
        if (scriptStartEpochMs <= 0 || banked <= 0) {
            return;
        }
        long runtimeMs = System.currentTimeMillis() - scriptStartEpochMs;
        double hours = runtimeMs / 3_600_000.0;
        if (hours > 0) {
            perHourSnapshot = (int) (banked / hours);
        }
    }

    private void resetPickLatch() {
        currentPlant = null;
        flaxAtClick = 0;
        lastFlaxGainMs = 0;
        awaitingYield = false;
        lastPickClickMs = 0;
        nextPickGapMs = 0;
        depletedTile = null;
        depletedUntilMs = 0;
    }

    private void resetSpinLatch() {
        wheelClicked = false;
        productionStarted = false;
        lastFlaxSeen = 0;
        lastSpinProgressMs = 0;
        spinOptionMisses = 0;
    }

    @Override
    public void shutdown() {
        resetPickLatch();
        resetSpinLatch();
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }

    public enum State {
        BANK,
        PICK,
        SPIN
    }
}
