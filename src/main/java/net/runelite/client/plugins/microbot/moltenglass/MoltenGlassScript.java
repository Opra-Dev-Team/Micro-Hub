package net.runelite.client.plugins.microbot.moltenglass;

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
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.concurrent.TimeUnit;

@Slf4j
public class MoltenGlassScript extends Script {

    static final WorldPoint BANK_TILE = new WorldPoint(3096, 3494, 0);
    static final WorldPoint FURNACE_TILE = new WorldPoint(3109, 3499, 0);

    static final int BATCH_SIZE = 14;
    static final int SAND_ID = ItemID.BUCKET_OF_SAND;
    static final int ASH_ID = ItemID.SODA_ASH;
    static final int MOLTEN_GLASS_ID = ItemID.MOLTEN_GLASS;

    private static final long SMELT_TIMEOUT_MS = 90_000L;
    private static final int FURNACE_LOOKUP_RANGE = 25;

    public volatile State state = State.BANK;
    public volatile int glassMade;
    public volatile int tripsCompleted;
    public volatile int glassPerHourSnapshot;

    public volatile long scriptStartEpochMs;

    private long smeltStartedMs;
    private long lastProgressMs;
    private int lastSandSeen;
    private boolean furnaceClicked;
    private boolean productionStarted;

    public boolean run(MoltenGlassConfig config) {
        shutdown();
        state = State.BANK;
        glassMade = 0;
        tripsCompleted = 0;
        glassPerHourSnapshot = 0;
        resetSmeltLatch();

        Microbot.status = "Starting Molten Glass";

        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyCraftingSetup();
        Rs2Antiban.setActivity(Activity.GENERAL_CRAFTING);
        Rs2Antiban.setActivityIntensity(ActivityIntensity.LOW);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    return;
                }
                if (!super.run()) {
                    return;
                }

                routeInventoryState();

                switch (state) {
                    case BANK:
                        bank(config);
                        break;
                    case SMELT:
                        smelt();
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
        }, 0, 200, TimeUnit.MILLISECONDS);

        return true;
    }

    private void routeInventoryState() {
        if (Rs2Bank.isOpen() || state == State.BANK || state == State.SMELT) {
            return;
        }
    }

    private void bank(MoltenGlassConfig config) {
        enterPinIfNeeded(config);
        if (!Rs2Bank.isOpen()) {
            if (!Rs2Bank.walkToBankAndUseBank(BankLocation.EDGEVILLE)) {
                Microbot.status = "Opening Edgeville bank";
                return;
            }
        }
        enterPinIfNeeded(config);
        if (!Rs2Bank.isOpen()) {
            return;
        }

        int moltenBeforeDeposit = Rs2Inventory.count(MOLTEN_GLASS_ID);
        if (moltenBeforeDeposit > 0 || !Rs2Inventory.isEmpty()) {
            Microbot.status = "Depositing inventory";
            Rs2Bank.depositAll();
            sleepUntil(Rs2Inventory::isEmpty, 4000);
            if (moltenBeforeDeposit > 0) {
                glassMade += moltenBeforeDeposit;
                updateGlassPerHour();
                tripsCompleted++;
                if (reachedStopLimit(config)) {
                    stopAtGoal(config);
                    return;
                }
            }
        }

        if (!Rs2Bank.hasBankItem(SAND_ID, BATCH_SIZE) || !Rs2Bank.hasBankItem(ASH_ID, BATCH_SIZE)) {
            Microbot.status = "Need at least 14 sand and 14 soda ash in bank";
            log.warn("Bank missing sand or soda ash for a full batch");
            shutdown();
            return;
        }

        Microbot.status = "Withdrawing sand and soda ash";
        Rs2Bank.withdrawX(SAND_ID, BATCH_SIZE);
        sleepUntil(() -> Rs2Inventory.count(SAND_ID) >= BATCH_SIZE, 3000);
        Rs2Bank.withdrawX(ASH_ID, BATCH_SIZE);
        sleepUntil(() -> Rs2Inventory.count(ASH_ID) >= BATCH_SIZE, 3000);

        if (!hasSmeltBatchReady()) {
            Microbot.status = "Withdraw failed";
            log.warn("Withdraw failed: sand={} ash={}", Rs2Inventory.count(SAND_ID), Rs2Inventory.count(ASH_ID));
            return;
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3000);

        Rs2Antiban.takeMicroBreakByChance();
        if (Rs2Random.dicePercentage(12)) {
            Rs2Antiban.moveMouseRandomly();
        }

        resetSmeltLatch();
        lastSandSeen = BATCH_SIZE;
        lastProgressMs = System.currentTimeMillis();
        state = State.SMELT;
    }

    private void smelt() {
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            return;
        }

        int sandLeft = Rs2Inventory.count(SAND_ID);
        int ashLeft = Rs2Inventory.count(ASH_ID);
        noteSandProgress(sandLeft);

        if (sandLeft == 0 || ashLeft == 0) {
            Microbot.status = "Batch finished";
            Rs2Antiban.actionCooldown();
            resetSmeltLatch();
            state = State.BANK;
            return;
        }

        if (productionStarted) {
            Microbot.status = "Smelting molten glass";
            if (smeltStalled(sandLeft, ashLeft)) {
                log.warn("Smelt stalled with sand={} ash={}", sandLeft, ashLeft);
                resetSmeltLatch();
                state = State.BANK;
            }
            return;
        }

        if (isSmeltInterfaceOpen()) {
            Microbot.status = "Making molten glass";
            startMoltenGlassProduction();
            productionStarted = true;
            furnaceClicked = true;
            smeltStartedMs = System.currentTimeMillis();
            lastProgressMs = smeltStartedMs;
            lastSandSeen = sandLeft;
            log.info("Molten glass production started");
            return;
        }

        if (furnaceClicked) {
            Microbot.status = "Waiting on furnace";
            if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Player.isInteracting()) {
                lastProgressMs = System.currentTimeMillis();
                return;
            }
            if (System.currentTimeMillis() - lastProgressMs > 6_000L) {
                log.warn("Furnace click did not open the smelt interface");
                furnaceClicked = false;
            }
            return;
        }

        Microbot.status = "Clicking furnace from bank";
        Rs2TileObjectModel furnace = findEdgevilleFurnace();
        if (furnace == null) {
            log.warn("Edgeville furnace not found within {} tiles of bank anchor", FURNACE_LOOKUP_RANGE);
            Microbot.status = "Cannot see Edgeville furnace";
            return;
        }
        furnace.click("Smelt");
        furnaceClicked = true;
        lastProgressMs = System.currentTimeMillis();
        log.info("Clicked Edgeville furnace once");
    }

    private void noteSandProgress(int sandLeft) {
        if (sandLeft < lastSandSeen) {
            lastSandSeen = sandLeft;
            lastProgressMs = System.currentTimeMillis();
        }
    }

    private boolean smeltStalled(int sandLeft, int ashLeft) {
        if (Rs2Player.isAnimating() || Rs2Player.isInteracting()) {
            return false;
        }
        if (isSmeltInterfaceOpen() && System.currentTimeMillis() - lastProgressMs < 8_000L) {
            return false;
        }
        if (sandLeft == 0 || ashLeft == 0) {
            return false;
        }
        long idleMs = System.currentTimeMillis() - lastProgressMs;
        return idleMs > SMELT_TIMEOUT_MS;
    }

    private void resetSmeltLatch() {
        smeltStartedMs = 0;
        lastProgressMs = 0;
        lastSandSeen = 0;
        furnaceClicked = false;
        productionStarted = false;
    }

    private boolean isSmeltInterfaceOpen() {
        return Rs2Widget.isProductionWidgetOpen()
                || Rs2Widget.isSmithingWidgetOpen()
                || Rs2Widget.hasWidget("What would you like to smelt?");
    }

    private boolean startMoltenGlassProduction() {
        if (Rs2Widget.isProductionWidgetOpen()) {
            Rs2Widget.enableQuantityOption("All");
            return Rs2Widget.handleProcessingInterface("Molten glass");
        }
        if (Rs2Widget.hasWidget("What would you like to smelt?")
                || Rs2Widget.isSmithingWidgetOpen()) {
            return Rs2Widget.clickWidget("Molten glass", false);
        }
        log.warn("Smelt interface open but molten glass option was not found");
        return false;
    }

    private Rs2TileObjectModel findEdgevilleFurnace() {
        Rs2TileObjectModel furnace = Microbot.getRs2TileObjectCache().query()
                .withNameContains("furnace")
                .where(obj -> obj.getWorldLocation() != null
                        && obj.getWorldLocation().distanceTo(FURNACE_TILE) <= 4)
                .nearestOnClientThread(BANK_TILE, FURNACE_LOOKUP_RANGE);
        if (furnace != null) {
            return furnace;
        }
        return Microbot.getRs2TileObjectCache().query()
                .withNameContains("furnace")
                .where(obj -> obj.getWorldLocation() != null
                        && obj.getWorldLocation().distanceTo(FURNACE_TILE) <= 4)
                .nearestReachable(FURNACE_LOOKUP_RANGE);
    }

    private boolean hasSmeltBatchReady() {
        return Rs2Inventory.count(SAND_ID) == BATCH_SIZE && Rs2Inventory.count(ASH_ID) == BATCH_SIZE;
    }

    private void enterPinIfNeeded(MoltenGlassConfig config) {
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

    private boolean reachedStopLimit(MoltenGlassConfig config) {
        int limit = config.stopAfterGlass();
        return limit > 0 && glassMade >= limit;
    }

    private void stopAtGoal(MoltenGlassConfig config) {
        Microbot.status = "Smelted " + glassMade + "/" + config.stopAfterGlass() + " molten glass. Stopping.";
        shutdown();
    }

    private void updateGlassPerHour() {
        if (scriptStartEpochMs <= 0 || glassMade <= 0) {
            return;
        }
        long runtimeMs = System.currentTimeMillis() - scriptStartEpochMs;
        double hours = runtimeMs / 3_600_000.0;
        if (hours > 0) {
            glassPerHourSnapshot = (int) (glassMade / hours);
        }
    }

    @Override
    public void shutdown() {
        resetSmeltLatch();
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }

    public enum State {
        BANK,
        SMELT
    }
}
