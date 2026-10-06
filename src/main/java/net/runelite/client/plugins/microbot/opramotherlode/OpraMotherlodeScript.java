package net.runelite.client.plugins.microbot.opramotherlode;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.AnimationID;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Perspective;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.player.Rs2PlayerCache;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.opramotherlode.enums.MLMMiningSpot;
import net.runelite.client.plugins.microbot.opramotherlode.enums.MLMMiningSpotList;
import net.runelite.client.plugins.microbot.opramotherlode.enums.MLMStatus;
import net.runelite.client.plugins.microbot.opramotherlode.enums.Pickaxe;
import net.runelite.client.plugins.microbot.util.antiban.AntibanPlugin;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.depositbox.DepositBoxLocation;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Gembag;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

@Slf4j
public class OpraMotherlodeScript extends Script
{

    private static final int SPOT_VEIN_RADIUS = 2;
    private static final int SPINNING_WATER_WHEEL = 26671;
    private static final int STOPPED_WATER_WHEEL = 26672;
    private static final int OCCUPIED_VEIN_RADIUS = 4;
    private static final int VEIN_CLICK_TIMEOUT_MS = 2_000;
    private static final int STUCK_VEIN_IDLE_MS = 4_000;
    private static final int GEM_BAG_OPEN_TIMEOUT_MS = 2_000;
    private static final int ROCKFALL_RANGE = 12;
    private static final int ROCKFALL_A = 26679;
    private static final int ROCKFALL_B = 26680;

	private static final WorldPoint HOPPER_DEPOSIT_DOWN = new WorldPoint(3748, 5672, 0);
	private static final WorldPoint HOPPER_DEPOSIT_UP = new WorldPoint(3755, 5677, 0);
	private static final WorldPoint SACK_TILE = new WorldPoint(3748, 5659, 0);
	private static final WorldPoint DEPOSIT_BOX_TILE = DepositBoxLocation.MOTHERLODE_MINE.getWorldPoint();

	private static final WorldArea CRATE_AREA = new WorldArea(new WorldPoint(3750, 5659, 0), 10, 16);

	private static final WorldPoint[] CRATE_WALKPOINTS = new WorldPoint[]
	{
		new WorldPoint(3755, 5671, 0),
		new WorldPoint(3756, 5662, 0),
		new WorldPoint(3751, 5662, 0),
	};

    private static final int UPPER_FLOOR_HEIGHT = -490;
    static final int SACK_SIZE = 108;
    volatile MLMStatus status = MLMStatus.IDLE;
    volatile MLMMiningSpot miningSpot = MLMMiningSpot.IDLE;
    private WorldPoint activeStandTile;
    private WorldPoint miningVeinTile;
    private long idleAtVeinSince;
	private List<String> itemsToKeep;

	private final OpraMotherlodePlugin plugin;
    private final OpraMotherlodeConfig config;
    private final Rs2TileObjectCache rs2TileObjectCache;
    private final Rs2PlayerCache rs2PlayerCache;


	private boolean shouldRepairWaterwheel = false;
	private int wheelRepairMisses = 0;
	private boolean emptySackWorkflowActive = false;
	private int cameraTurnedTowardId;
	private boolean turnedCameraThisCall;
	private boolean pickedUpHammer = false;
	private List<MinerSnapshot> nearbyMiners = new ArrayList<>();
    private MLMStatus lastLoggedStatus = null;

	@Inject
	public OpraMotherlodeScript(OpraMotherlodePlugin plugin, OpraMotherlodeConfig config, Rs2TileObjectCache rs2TileObjectCache, Rs2PlayerCache rs2PlayerCache)
	{
		this.plugin = plugin;
		this.config = config;
        this.rs2TileObjectCache = rs2TileObjectCache;
        this.rs2PlayerCache = rs2PlayerCache;
    }

    public boolean run()
    {
        log.info("Starting OpraMotherlode script");
        initialize();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::executeTaskSafely, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void initialize()
    {
        log.debug("Initializing MLM runtime state");
        Rs2Antiban.antibanSetupTemplates.applyMiningSetup();
        resetRuntime();
    }

    private void resetRuntime()
    {
        status = MLMStatus.IDLE;
        miningSpot = MLMMiningSpot.IDLE;
        activeStandTile = null;
        miningVeinTile = null;
        idleAtVeinSince = 0;
        lastLoggedStatus = null;
        shouldRepairWaterwheel = false;
        wheelRepairMisses = 0;
        nearbyMiners = new ArrayList<>();
        emptySackWorkflowActive = false;
        cameraTurnedTowardId = 0;
        turnedCameraThisCall = false;
        pickedUpHammer = false;
        itemsToKeep = null;
    }

    private void executeTaskSafely()
    {
        try
        {
            executeTask();
        }
        catch (Exception ex)
        {
            if (ex instanceof InterruptedException || ex.getCause() instanceof InterruptedException)
            {
                Thread.currentThread().interrupt();
                return;
            }
            log.error("Unhandled error in MLM main loop; resetting runtime state", ex);
            abortCurrentWorkflow();
        }
    }

    private void executeTask()
    {
        if (!super.run() || !isWorkflowRunnable())
        {
            abortCurrentWorkflow();
            return;
        }

        determineStatusFromInventory();
        logStatusTransitionIfChanged();

        switch (status)
        {
            case IDLE:
                break;
            case MINING:
                Rs2Antiban.setActivityIntensity(Rs2Antiban.getActivity().getActivityIntensity());
                handleMining();
                break;
            case EMPTY_SACK:
                if (Rs2Player.isAnimating()) return;
                Rs2Antiban.setActivityIntensity(ActivityIntensity.EXTREME);
                emptySack();
                break;
            case FIXING_WATERWHEEL:
                fixWaterwheel();
                break;
            case DEPOSIT_HOPPER:
                if (Rs2Player.isAnimating()) return;
                depositHopper();
                break;
            case DROP_GEMS:
                if (Rs2Player.isAnimating()) return;
                dropGems();
                break;
        }
    }
    private String[] SPEC_PICKAXES = {"dragon pickaxe", "crystal pickaxe", "infernal pickaxe"};

    private void handlePickaxeSpec() {
        if (Rs2Equipment.isWearing(SPEC_PICKAXES)) {
            Rs2Combat.setSpecState(true, 1000);
        }
    }

    private void determineStatusFromInventory()
    {
        if (!hasRequiredTools())
        {
            log.info("Missing pickaxe, withdrawing one from the bank");
            setupInventory();
            return;
        }

        if (config.fixWaterwheel() && shouldRepairWaterwheel) {
            status = MLMStatus.FIXING_WATERWHEEL;
            return;
        }

        if (config.dropGems() && hasGemsInInventory()) {
            status = MLMStatus.DROP_GEMS;
            return;
        }

        if (sackNeedsEmpty()) {
            resetMiningState();
            status = MLMStatus.EMPTY_SACK;
            return;
        }

        if (payDirtCount() > 0 && Rs2Inventory.isFull()) {
            resetMiningState();
            status = MLMStatus.DEPOSIT_HOPPER;
            return;
        }
        status = MLMStatus.MINING;
    }

    private boolean sackNeedsEmpty()
    {
        if (emptySackWorkflowActive) {
            return currentSackCount() > 0 || hasOreInInventory();
        }
        return currentSackCount() >= SACK_SIZE;
    }

    private boolean hasRequiredTools()
    {
		return Pickaxe.hasItem();
    }

	private void handleMining()
	{
		if (Rs2Player.getAnimation() != AnimationID.IDLE || AntibanPlugin.isMining()) {
			idleAtVeinSince = 0;
			return;
		}
		if (Rs2Player.isMoving()) {
			return;
		}
		if (stillMiningTrackedVein()) {
			if (idleAtVeinSince == 0) {
				idleAtVeinSince = System.currentTimeMillis();
			}
			if (System.currentTimeMillis() - idleAtVeinSince < STUCK_VEIN_IDLE_MS) {
				return;
			}
			miningVeinTile = null;
			idleAtVeinSince = 0;
		} else if (miningVeinTile != null) {
			miningVeinTile = null;
			idleAtVeinSince = 0;
		}

		if (Rs2Gembag.isUnknown()) {
			Rs2Gembag.checkGemBag();
		}

		if (miningSpot == MLMMiningSpot.IDLE)
		{
			selectMiningSpotFromConfig();
		}

		if (!isOnSelectedMiningFloor())
		{
			walkToMiningSpot();
			return;
		}

		refreshNearbyMiners();

		if (findClosestVein() != null)
		{
			attemptToMineVein();
			return;
		}

		if (spotHasOnlyOccupiedVeins())
		{
			handleOccupiedSpot();
			return;
		}

		if (!walkToMiningSpot()) return;

		attemptToMineVein();
	}

	private boolean isOnSelectedMiningFloor()
	{
		if (miningSpot.isUpstairs()) return isUpperFloor();
		if (miningSpot.isDownstairs()) return !isUpperFloor();
		return true;
	}


    private void emptySack()
	{
		if (!emptySackWorkflowActive)
		{
			emptySackWorkflowActive = true;
			log.info("Emptying sack workflow started, sack={}/{}", currentSackCount(), SACK_SIZE);
		}

		if (!isWorkflowRunnable())
		{
			abortCurrentWorkflow();
			return;
		}

		ensureLowerFloor();
		if (!isWorkflowRunnable())
		{
			abortCurrentWorkflow();
			return;
		}

		int sack = currentSackCount();
		if (sack <= 0 && !hasOreInInventory())
		{
			if (payDirtCount() > 0)
			{
				depositHopper();
			}
			if (payDirtCount() == 0)
			{
				completeEmptySackWorkflow();
			}
			return;
		}

		if (hasOreInInventory())
		{
			useDepositBox();
			return;
		}

		if (Rs2Inventory.isFull() && payDirtCount() > 0)
		{
			depositHopper();
			return;
		}

		if (sack > 0)
		{
			searchSack(sack);
		}
	}

	private void searchSack(int sackBefore)
	{
		Rs2TileObjectModel sack = findSack();
		if (clickOnScreen(sack, "Search"))
		{
			sleepUntil(() -> !isWorkflowRunnable() || currentSackCount() < sackBefore || hasOreInInventory(), 10_000);
			return;
		}
		if (stillOffScreen(sack))
		{
			walkToward(objectTile(sack, SACK_TILE), 2);
		}
	}

	private Rs2TileObjectModel findSack()
	{
		return rs2TileObjectCache.query().where(this::isSack).nearest();
	}

	private boolean isSack(Rs2TileObjectModel object)
	{
		int id = object.getId();
		return id == ObjectID.MOTHERLODE_SACK
			|| id == ObjectID.MOTHERLODE_SACK_FULL
			|| id == ObjectID.MOTHERLODE_SACK_EMPTY;
	}

	private void completeEmptySackWorkflow()
	{
		if (!waterwheelNeedsRepair())
		{
			shouldRepairWaterwheel = false;
		}
		emptySackWorkflowActive = false;
		Rs2Antiban.takeMicroBreakByChance();
		status = MLMStatus.IDLE;
        log.info("Emptying sack workflow complete");
	}

	private boolean isWorkflowRunnable()
	{
		if (!Microbot.isLoggedIn() || Microbot.pauseAllScripts.get() || Thread.currentThread().isInterrupted())
		{
			return false;
		}

		try
		{
			return Microbot.getClientThread().runOnClientThreadOptional(() -> {
				var player = Microbot.getClient().getLocalPlayer();
				return player != null && player.getWorldView() != null;
			}).orElse(false);
		}
		catch (RuntimeException ex)
		{
			log.debug("Player state unavailable during MLM lifecycle transition", ex);
			return false;
		}
	}

	private void abortCurrentWorkflow()
	{
		resetRuntime();
	}

    private boolean hasOreInInventory()
    {
        return Rs2Inventory.contains(
                ItemID.RUNITE_ORE, ItemID.ADAMANTITE_ORE, ItemID.MITHRIL_ORE,
                ItemID.GOLD_ORE, ItemID.COAL
        );
    }

    private boolean hasGemsInInventory() {
        return Rs2Inventory.contains(ItemID.UNCUT_SAPPHIRE, ItemID.UNCUT_EMERALD, ItemID.UNCUT_RUBY, ItemID.UNCUT_DIAMOND);
    }
    
    private void dropGems() {
        if (hasGemsInInventory()) {
            Rs2Inventory.dropAll(ItemID.UNCUT_SAPPHIRE, ItemID.UNCUT_EMERALD, ItemID.UNCUT_RUBY, ItemID.UNCUT_DIAMOND);
        }
    }

    private int payDirtCount() {
        return Rs2Inventory.count(ItemID.PAYDIRT);
    }

    int currentSackCount() {
        return Microbot.getVarbitValue(VarbitID.MOTHERLODE_SACK_TRANSMIT);
    }

    private void fixWaterwheel() {
        if (!waterwheelNeedsRepair()) {
            shouldRepairWaterwheel = false;
            wheelRepairMisses = 0;
            log.info("Water wheels are spinning");
            return;
        }

        List<Rs2TileObjectModel> broken = brokenStruts();
        if (broken.isEmpty()) {
            wheelRepairMisses++;
            if (wheelRepairMisses >= 2) {
                shouldRepairWaterwheel = false;
                wheelRepairMisses = 0;
                log.debug("Stopped water wheel has no broken strut to hammer");
            }
            return;
        }

        wheelRepairMisses = 0;
        if (isUpperFloor()) {
            ensureLowerFloor();
            if (isUpperFloor()) {
                return;
            }
        }

        status = MLMStatus.FIXING_WATERWHEEL;
        log.info("Fixing stopped water wheel, brokenStruts={}", broken.size());

		if (!hasHammer()) {
			if (!obtainHammer()) return;
		}

        while (isRunning() && getBrokenStrutCount() > 0) {
            int brokenBefore = getBrokenStrutCount();
            if (!rs2TileObjectCache.query().interact(ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN)) {
                log.debug("Broken strut click failed, retrying next loop");
                return;
            }

			sleepUntil(() -> getBrokenStrutCount() < brokenBefore, 8_000);
            if (getBrokenStrutCount() >= brokenBefore) {
                log.debug("Strut repair made no progress, retrying next loop");
                return;
            }
        }

        if (getBrokenStrutCount() == 0) {
			dropHammerIfNeeded();
			shouldRepairWaterwheel = false;
            log.info("Waterwheel repair complete");
        }
    }

    private void depositHopper()
    {
        // if using a gem bag, fill the gem bag and return to mining if the inventory is no longer full
        if (Rs2Inventory.isFull() && (Rs2Gembag.hasGemBag() && !Rs2Gembag.isGemBagOpen()))
        {
			Rs2Inventory.interact("gem bag", "open");
			sleepUntil(Rs2Gembag::isGemBagOpen, GEM_BAG_OPEN_TIMEOUT_MS);
			if (!Rs2Gembag.isGemBagOpen())
			{
				log.debug("Gem bag did not open");
				return;
			}
            Rs2Inventory.interact("gem bag", "fill");
            if (!Rs2Inventory.isFull())
            {
                return;
            }
        }

        WorldPoint hopperDeposit = (isUpperFloor() && config.upstairsHopperUnlocked()) ? HOPPER_DEPOSIT_UP : HOPPER_DEPOSIT_DOWN;
        Rs2TileObjectModel hopper = rs2TileObjectCache.query().where(x -> x.getWorldLocation().equals(hopperDeposit)).withId(ObjectID.MOTHERLODE_HOPPER).firstOnClientThread();

        if(isUpperFloor() && !config.upstairsHopperUnlocked())
        {
            ensureLowerFloor();
        }

        final int paydirtToDeposit = payDirtCount();

        if (hopper != null && hopper.click()) {
            log.debug("Depositing pay-dirt into hopper");
            sleepUntil(() -> payDirtCount() != paydirtToDeposit && !Rs2Player.isAnimating(), 10_000);

            if (config.fixWaterwheel() && payDirtCount() != paydirtToDeposit) {
                if (waterwheelNeedsRepair()) {
                    log.info("Water wheel is stopped after deposit");
                    shouldRepairWaterwheel = true;
                    fixWaterwheel();
                } else {
                    shouldRepairWaterwheel = false;
                    log.debug("Water wheels still spinning after deposit");
                }
            }

            log.debug("Hopper deposit complete: paydirtDeposited={}, sack={}/{}",
                    paydirtToDeposit, currentSackCount(), SACK_SIZE);
        }
        else
        {
            log.debug("Hopper unavailable, walking closer to deposit point");
            walkToward(hopperDeposit, 15);
        }
    }

    private void useDepositBox()
    {
        if (!Rs2DepositBox.isOpen())
        {
            Rs2TileObjectModel box = findDepositBox();
            if (!clickOnScreen(box, "Deposit"))
            {
                if (stillOffScreen(box))
                {
                    walkToward(objectTile(box, DEPOSIT_BOX_TILE), 2);
                }
                return;
            }
            sleepUntil(Rs2DepositBox::isOpen, 5_000);
            if (!Rs2DepositBox.isOpen())
            {
                return;
            }
        }

        if (Rs2Gembag.hasGemBag() && Rs2Gembag.getGemBagContents().stream().anyMatch(s -> s.getQuantity() > 30))
        {
            Rs2Bank.emptyGemBag();
            sleepUntil(() -> Rs2Gembag.getGemBagContents().stream().noneMatch(s -> s.getQuantity() > 30), 3000);
        }

        if (config.useDepositAll())
        {
            if (Rs2Widget.clickWidget("Deposit inventory"))
            {
                Rs2Inventory.waitForInventoryChanges(5000);
            }
            else
            {
                log.debug("Deposit inventory button missing, depositing items individually");
                depositAllExceptKept();
            }
        }
        else
        {
            depositAllExceptKept();
        }

        Rectangle gameObjectBounds = getMotherloadSackBounds();
        Rectangle depositBoxBounds = Rs2DepositBox.getDepositBoxBounds();
        if (depositBoxBounds != null && (!Rs2UiHelper.isRectangleWithinViewport(gameObjectBounds) || depositBoxBounds.intersects(gameObjectBounds))) {
            Rs2DepositBox.closeDepositBox();
        }
    }

    private Rs2TileObjectModel findDepositBox()
    {
        return rs2TileObjectCache.query().where(object -> {
            WorldPoint location = object.getWorldLocation();
            if (location == null || !DEPOSIT_BOX_TILE.equals(location))
            {
                return false;
            }
            int id = object.getId();
            return id == ObjectID.BANK_DEPOSIT_BOX || id == ObjectID.KR_BANK_DEPOSIT_BOX;
        }).firstOnClientThread();
    }

    private boolean clickOnScreen(Rs2TileObjectModel object, String action)
    {
        turnedCameraThisCall = false;
        if (object == null)
        {
            return false;
        }
        if (isClickboxOnScreen(object))
        {
            cameraTurnedTowardId = 0;
            return object.click(action);
        }
        if (cameraTurnedTowardId != object.getId())
        {
            Rs2Camera.turnTo(object);
            cameraTurnedTowardId = object.getId();
            turnedCameraThisCall = true;
            if (isClickboxOnScreen(object))
            {
                cameraTurnedTowardId = 0;
                return object.click(action);
            }
        }
        return false;
    }

    private boolean stillOffScreen(Rs2TileObjectModel object)
    {
        if (turnedCameraThisCall)
        {
            return false;
        }
        if (object == null)
        {
            return true;
        }
        return cameraTurnedTowardId == object.getId() && !isClickboxOnScreen(object);
    }

    private boolean isClickboxOnScreen(TileObject object)
    {
        Rectangle bounds = Rs2UiHelper.getObjectClickbox(object);
        return bounds != null && Rs2UiHelper.isRectangleWithinViewport(bounds);
    }

    private WorldPoint objectTile(Rs2TileObjectModel object, WorldPoint fallback)
    {
        if (object != null && object.getWorldLocation() != null)
        {
            return object.getWorldLocation();
        }
        return fallback;
    }

	private void setupInventory() {
        log.info("Withdrawing a pickaxe from the bank");
		Rs2ItemModel pickaxe = Pickaxe.getBestPickaxe();

		if (pickaxe != null) {
			return;
		}

		Rs2Bank.openBank();
		sleepUntil(Rs2Bank::isOpen);

		pickaxe = Pickaxe.getBestPickaxeFromBank();
		if (pickaxe == null) {
			Microbot.showMessage("No pickaxe found in bank or inventory. Please bank a pickaxe.");
			log.warn("No pickaxe found in bank or inventory, stopping plugin");
			Microbot.stopPlugin(plugin);
			return;
		}

		if (Rs2Inventory.isFull()) {
			Rs2Bank.depositAll();
		}

		if (Pickaxe.hasAttackLevelRequirement(pickaxe.getId())) {
			final Rs2ItemModel currentWeapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
			final Rs2ItemModel _pickaxe = pickaxe;
			Rs2Bank.withdrawAndEquip(_pickaxe.getId());
			sleepUntil(() -> Rs2Equipment.isWearing(_pickaxe.getId()));
			if (currentWeapon != null) {
				Rs2Bank.depositOne(currentWeapon.getId());
				Rs2Inventory.waitForInventoryChanges(5000);
			}
		} else {
			Rs2Bank.withdrawOne(pickaxe.getId());
			Rs2Inventory.waitForInventoryChanges(5000);
		}

		final int[] gemBagIDs = {ItemID.GEM_BAG, ItemID.GEM_BAG_OPEN};
		for (int gemBagID : gemBagIDs) {
			if (!isRunning()) break;
			if (Rs2Bank.withdrawOne(gemBagID)) {
				Rs2Inventory.waitForInventoryChanges(5000);
				break;
			}
		}

		Rs2Bank.toggleItemLock("pickaxe", false);
		Rs2Bank.toggleItemLock("gem bag", false);

		Rs2Bank.closeBank();
		sleepUntil(() -> !Rs2Bank.isOpen());
        log.info("Pickaxe withdraw complete");
	}

    private void selectMiningSpotFromConfig() {
        MLMMiningSpot selected = MLMMiningSpot.valueOf(config.miningArea().name());

        if (selected == MLMMiningSpot.ANY) {
            if (config.mineUpstairs()) {
                miningSpot = Rs2Random.between(0, 1) == 0 ? MLMMiningSpot.WEST_UPPER : MLMMiningSpot.EAST_UPPER;
            }
            else {
				MLMMiningSpot[] filteredSpots = Arrays.stream(MLMMiningSpot.values())
					.filter(s -> s.getWorldPoint() != null && s.isDownstairs())
					.toArray(MLMMiningSpot[]::new);

				int size = filteredSpots.length;
				if (size == 0) return;

				int randomIndex = Rs2Random.randomGaussian(size / 2.0, size / 6.0);
				randomIndex = Math.max(0, Math.min(size - 1, randomIndex));

				miningSpot = filteredSpots[randomIndex];
            }
        } else {
            switch (selected) {
                case EAST_UPPER:
                case WEST_UPPER:
                case WEST_LOWER:
                case WEST_MID:
                case SOUTH_WEST:
                case SOUTH_EAST:
                    miningSpot = selected;
                    break;
                default:
                    Microbot.showMessage("Invalid mining area selected.");
                    log.warn("Invalid mining area selected: {}", selected);
                    Microbot.stopPlugin(plugin);
                    return;
            }
        }

        List<WorldPoint> points = miningSpot.getWorldPoint();
        if (points == null || points.isEmpty()) {
            activeStandTile = null;
        } else {
            activeStandTile = points.get(ThreadLocalRandom.current().nextInt(points.size()));
        }
        log.info("Selected mining spot: {}", miningSpot);
    }

    private WorldPoint miningSpotTile()
    {
        return activeStandTile;
    }

    private WorldPoint playerLocation()
    {
        return Microbot.getClientThread().invoke(() -> {
            if (Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null) {
                return null;
            }
            return Microbot.getClient().getLocalPlayer().getWorldLocation();
        });
    }

    private boolean walkToward(WorldPoint destination, int radius)
    {
        if (destination == null) {
            return false;
        }
        Rs2TileObjectModel rockfall = blockingRockfall(destination);
        if (rockfall != null) {
            WorldPoint tile = rockfall.getWorldLocation();
            log.info("Mining rockfall at {}", tile);
            if (!rockfall.click("Mine")) {
                Rs2Walker.walkTo(tile, 2);
                return false;
            }
            sleepUntil(() -> !rockfallAt(tile), 8_000);
        }
        return Rs2Walker.walkTo(destination, radius);
    }

    private Rs2TileObjectModel blockingRockfall(WorldPoint destination)
    {
        WorldPoint here = playerLocation();
        if (here == null || destination == null) {
            return null;
        }
        int playerToDestination = here.distanceTo(destination);
        return rs2TileObjectCache.query().where(object -> {
            if (!isRockfall(object.getId())) {
                return false;
            }
            WorldPoint location = object.getWorldLocation();
            if (location == null || here.distanceTo(location) > ROCKFALL_RANGE) {
                return false;
            }
            return location.distanceTo(destination) < playerToDestination;
        }).nearestOnClientThread();
    }

    private boolean rockfallAt(WorldPoint tile)
    {
        if (tile == null) {
            return false;
        }
        return rs2TileObjectCache.query().where(object -> {
            WorldPoint location = object.getWorldLocation();
            return location != null && location.equals(tile) && isRockfall(object.getId());
        }).firstOnClientThread() != null;
    }

    private boolean isRockfall(int id)
    {
        return id == ROCKFALL_A || id == ROCKFALL_B;
    }

    private boolean walkToMiningSpot()
    {
        WorldPoint target = miningSpotTile();
        if (target == null) {
            return false;
        }

        // Navigates to correct floor based on selected mining area
        if (miningSpot.isUpstairs() && !isUpperFloor())
        {
            goUp();
            return false; // Wait until we've gone up
        }

        if (miningSpot.isDownstairs() && isUpperFloor()) {
            goDown();
            return false; // Wait until we've gone down
        }

        return walkToward(target, 2);
    }

	private boolean attemptToMineVein() {
        Rs2TileObjectModel vein = findClosestVein();
		if (vein == null) {
			if (spotHasOnlyOccupiedVeins()) {
				handleOccupiedSpot();
				return false;
			}
			repositionCameraAndMove();
			return false;
		}

		WorldPoint veinLocation = vein.getWorldLocation();
		if (!isClickboxOnScreen(vein))
		{
			if (cameraTurnedTowardId != vein.getId())
			{
				Rs2Camera.turnTo(vein);
				cameraTurnedTowardId = vein.getId();
				if (!isClickboxOnScreen(vein))
				{
					return false;
				}
			}
			else
			{
				WorldPoint stand = standTileNearest(veinLocation);
				if (stand == null)
				{
					return false;
				}
				walkToward(stand, 1);
				return false;
			}
		}

		cameraTurnedTowardId = 0;
		handlePickaxeSpec();

		if (!vein.click("Mine")) return false;

		miningVeinTile = veinLocation;
		idleAtVeinSince = 0;

		return sleepUntil(() -> {
			WorldPoint here = playerLocation();
			return AntibanPlugin.isMining() && here != null && veinLocation.distanceTo(here) <= 2;
		}, VEIN_CLICK_TIMEOUT_MS);
	}

    private Rs2TileObjectModel findClosestVein()
    {
        return rs2TileObjectCache.query().where(this::isValidVein).nearest();
    }

    private boolean isValidVein(Rs2TileObjectModel wallObject)
    {
        int id = wallObject.getId();
        boolean isVein = isLiveVeinId(id);
        if (!isVein) return false;

        WorldPoint location = wallObject.getWorldLocation();
        if (location == null || !belongsToSelectedSpot(location)) {
            return false;
        }

		if (config.useAntiCrash() && veinOccupied(location))
		{
			return false;
		}

        return hasWalkableTilesAround(wallObject);
    }

    private boolean belongsToSelectedSpot(WorldPoint location)
    {
        WorldPoint stand = standTileNearest(location);
        return stand != null && stand.distanceTo(location) <= SPOT_VEIN_RADIUS;
    }

    private WorldPoint standTileNearest(WorldPoint location)
    {
        List<WorldPoint> points = miningSpot.getWorldPoint();
        if (points == null || location == null) {
            return null;
        }
        WorldPoint nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        for (WorldPoint stand : points) {
            if (stand == null) {
                continue;
            }
            int distance = stand.distanceTo(location);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = stand;
            }
        }
        return nearest;
    }

    private boolean veinOccupied(WorldPoint veinTile)
    {
        if (!config.useAntiCrash() || veinTile == null || nearbyMiners == null)
        {
            return false;
        }
        for (MinerSnapshot miner : nearbyMiners)
        {
            int distance = miner.tile.distanceTo(veinTile);
            if (distance <= 1)
            {
                return true;
            }
            if (miner.animation != -1 && miner.animation != AnimationID.IDLE && distance <= OCCUPIED_VEIN_RADIUS)
            {
                return true;
            }
        }
        return false;
    }

    private void refreshNearbyMiners()
    {
        if (!config.useAntiCrash())
        {
            nearbyMiners = new ArrayList<>();
            return;
        }
        List<MinerSnapshot> miners = Microbot.getClientThread().invoke(() -> {
            var client = Microbot.getClient();
            WorldPoint local = client != null && client.getLocalPlayer() != null
                ? client.getLocalPlayer().getWorldLocation()
                : null;
            List<Rs2PlayerModel> players = rs2PlayerCache.query().toList();
            List<MinerSnapshot> snapshots = new ArrayList<>();
            if (players == null)
            {
                return snapshots;
            }
            for (Rs2PlayerModel player : players)
            {
                if (player == null)
                {
                    continue;
                }
                WorldPoint tile = player.getWorldLocation();
                if (tile == null || (local != null && tile.equals(local)))
                {
                    continue;
                }
                snapshots.add(new MinerSnapshot(tile, player.getAnimation()));
            }
            return snapshots;
        });
        nearbyMiners = miners == null ? new ArrayList<>() : miners;
    }

    private boolean spotHasOnlyOccupiedVeins()
    {
        if (!config.useAntiCrash() || miningSpot.getWorldPoint() == null)
        {
            return false;
        }
        List<Rs2TileObjectModel> veins = rs2TileObjectCache.query().where(object -> {
            int id = object.getId();
            if (!isLiveVeinId(id))
            {
                return false;
            }
            WorldPoint location = object.getWorldLocation();
            return location != null && belongsToSelectedSpot(location) && hasWalkableTilesAround(object);
        }).toListOnClientThread();
        if (veins == null || veins.isEmpty())
        {
            return false;
        }
        for (Rs2TileObjectModel vein : veins)
        {
            if (!veinOccupied(vein.getWorldLocation()))
            {
                return false;
            }
        }
        return true;
    }

    private void handleOccupiedSpot()
    {
        if (config.miningArea() == MLMMiningSpotList.ANY)
        {
            selectAnotherMiningSpot();
            return;
        }
        waitOffOccupiedSpot();
    }

    private void selectAnotherMiningSpot()
    {
        MLMMiningSpot previous = miningSpot;
        MLMMiningSpot[] options = config.mineUpstairs()
            ? new MLMMiningSpot[] { MLMMiningSpot.WEST_UPPER, MLMMiningSpot.EAST_UPPER }
            : Arrays.stream(MLMMiningSpot.values())
                .filter(spot -> spot.getWorldPoint() != null && spot.isDownstairs())
                .toArray(MLMMiningSpot[]::new);
        List<MLMMiningSpot> choices = new ArrayList<>();
        for (MLMMiningSpot option : options)
        {
            if (option != previous)
            {
                choices.add(option);
            }
        }
        if (choices.isEmpty())
        {
            return;
        }
        miningSpot = choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
        List<WorldPoint> points = miningSpot.getWorldPoint();
        activeStandTile = points == null || points.isEmpty()
            ? null
            : points.get(ThreadLocalRandom.current().nextInt(points.size()));
        log.info("Occupied mining spot {}, switching to {}", previous, miningSpot);
    }

    private void waitOffOccupiedSpot()
    {
        WorldPoint here = playerLocation();
        WorldPoint stand = miningSpotTile();
        if (here == null || stand == null || here.distanceTo(stand) > SPOT_VEIN_RADIUS)
        {
            return;
        }
        WorldPoint anchor = miningSpot.isUpstairs() ? HOPPER_DEPOSIT_UP : HOPPER_DEPOSIT_DOWN;
        int stepX = Integer.signum(anchor.getX() - stand.getX());
        int stepY = Integer.signum(anchor.getY() - stand.getY());
        if (stepX == 0 && stepY == 0)
        {
            stepX = 1;
        }
        WorldPoint away = new WorldPoint(stand.getX() + stepX * 5, stand.getY() + stepY * 5, stand.getPlane());
        log.debug("Mining spot {} is occupied, waiting off the rocks", miningSpot);
        walkToward(away, 2);
    }

    private boolean hasWalkableTilesAround(Rs2TileObjectModel wallObject)
    {
        return Rs2Tile.areSurroundingTilesWalkable(wallObject.getWorldLocation(), 1, 1);
    }

    private void repositionCameraAndMove()
    {
        WorldPoint target = miningSpotTile();
        if (target == null) {
            return;
        }
        Rs2Camera.resetPitch();
        Rs2Camera.resetZoom();
		LocalPoint localTarget = Microbot.getClientThread().invoke(() ->
			LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), target)
		);
		if (localTarget != null) {
        	Rs2Camera.turnTo(localTarget);
		}
        walkToward(target, 2);
    }

    private void goUp()
    {
        if (isUpperFloor()) return;
        log.debug("Transitioning to upper floor");

		Rs2TileObjectModel ladder = rs2TileObjectCache.query().withId(ObjectID.MOTHERLODE_LADDER_BOTTOM).nearestReachable();
		if (ladder == null) {
			walkToward(miningSpot.getWorldPoint().get(0), 6);
			return;
		}

		if (!ladder.click()) return;

		sleepUntil(() -> Rs2Player.isMoving() || Rs2Player.isAnimating(), 1_500);
		sleepUntil(this::isUpperFloor, 8_000);
    }

    private void goDown()
    {
        if (!isUpperFloor()) return;
        log.debug("Transitioning to lower floor");

		Rs2TileObjectModel ladder = rs2TileObjectCache.query().withId(ObjectID.MOTHERLODE_LADDER_TOP).nearestReachable();
		if (ladder == null) {
			walkToward(HOPPER_DEPOSIT_DOWN, 6);
			return;
		}

		if (!ladder.click()) return;

		sleepUntil(() -> Rs2Player.isMoving() || Rs2Player.isAnimating(), 1_500);
        sleepUntil(() -> !isUpperFloor(), 8_000);
    }

    private void ensureLowerFloor()
    {
        if (isUpperFloor()) goDown();
    }

    private boolean isUpperFloor()
    {
		Integer height = Microbot.getClientThread().invoke(() -> {
			if (Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null) return null;
			return Perspective.getTileHeight(
				Microbot.getClient(),
				Microbot.getClient().getLocalPlayer().getLocalLocation(),
				0
			);
		});
		return height != null && height < UPPER_FLOOR_HEIGHT;
    }

    private void resetMiningState(boolean force)
    {
        miningSpot = (ThreadLocalRandom.current().nextBoolean() || force) ? MLMMiningSpot.IDLE : miningSpot;
        miningVeinTile = null;
        idleAtVeinSince = 0;
    }

    private boolean isLiveVeinId(int id)
    {
        return id == 26661 || id == 26662 || id == 26663 || id == 26664;
    }

    private boolean stillMiningTrackedVein()
    {
        if (miningVeinTile == null) {
            return false;
        }
        WorldPoint here = playerLocation();
        if (here == null || here.distanceTo(miningVeinTile) > 2) {
            return false;
        }
        WorldPoint tracked = miningVeinTile;
        return rs2TileObjectCache.query().where(object -> {
            WorldPoint location = object.getWorldLocation();
            return location != null && location.equals(tracked) && isLiveVeinId(object.getId());
        }).firstOnClientThread() != null;
    }

	private void resetMiningState()
	{
		resetMiningState(false);
	}

	private boolean hasHammer() {
		return Rs2Equipment.isWearing("hammer") || Rs2Inventory.hasItem("hammer");
	}

	private boolean obtainHammer() {
		/*

			Typically, the hammer is located near the hopper on the lower floor OR near the sack,
			so we should be close enough to directly interact with it.

			WorldPoint nearestCratePoint = Arrays.stream(CRATE_WALKPOINTS)
				.min(WorldPoint::distanceTo)
				.orElse(CRATE_WALKPOINTS[0]);
			if (!Rs2Walker.walkTo(nearestCratePoint)) return false;
		 */

        if (Rs2Inventory.isFull()) {
            if (Rs2Inventory.interact("pay-dirt", "drop")) {
                sleepUntil(() -> !Rs2Inventory.isFull());
            } else {
                return false;
            }
        }

        while (!Rs2Inventory.hasItem("hammer") && isRunning()) {
            //The crate at this point ALWAYS gives the player a hammer
            rs2TileObjectCache.query().where(obj -> obj.getWorldLocation().equals(new WorldPoint(3752, 5674, 0))).interact("Search");
            Rs2Inventory.waitForInventoryChanges(5_000);
            if (Rs2Inventory.hasItem("hammer")) {
                pickedUpHammer = true;
                itemsToKeep = null;
                log.info("Hammer obtained from crate");
                break;
            }
		}

		return pickedUpHammer;
	}

	private void dropHammerIfNeeded() {
		if (pickedUpHammer && Rs2Inventory.hasItem("hammer")) {
			Rs2Inventory.drop("hammer");
			sleepUntil(() -> !Rs2Inventory.hasItem("hammer"));
			pickedUpHammer = false;
		}
	}

    private void logStatusTransitionIfChanged()
    {
        if (status == lastLoggedStatus)
        {
            return;
        }

        log.info("MLM status transition: {} -> {}", lastLoggedStatus, status);
        lastLoggedStatus = status;
    }

	private Rectangle getMotherloadSackBounds() {
		TileObject sack = findSack();
		return Rs2UiHelper.getObjectClickbox(sack);
	}

	private int getBrokenStrutCount() {
		return brokenStruts().size();
	}

	private List<Rs2TileObjectModel> brokenStruts() {
		List<Rs2TileObjectModel> broken = rs2TileObjectCache.query()
			.where(o -> o.getId() == ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN)
			.toListOnClientThread();
		return broken == null ? new ArrayList<>() : broken;
	}

	private boolean waterwheelNeedsRepair() {
		if (!brokenStruts().isEmpty()) {
			return true;
		}
		List<Rs2TileObjectModel> wheels = rs2TileObjectCache.query()
			.where(o -> o.getId() == SPINNING_WATER_WHEEL || o.getId() == STOPPED_WATER_WHEEL)
			.toListOnClientThread();
		if (wheels == null || wheels.isEmpty()) {
			return false;
		}
		for (Rs2TileObjectModel wheel : wheels) {
			if (wheel != null && wheel.getId() == STOPPED_WATER_WHEEL) {
				return true;
			}
		}
		return false;
	}

	private void depositAllExceptKept() {
		List<String> keep = new ArrayList<>(getItemsToKeep());
		if (!keep.contains("pay-dirt"))
		{
			keep.add("pay-dirt");
		}
		Rs2DepositBox.depositAllExcept(keep, false);
		Rs2Inventory.waitForInventoryChanges(5000);
	}

	private static final class MinerSnapshot {
		private final WorldPoint tile;
		private final int animation;

		private MinerSnapshot(WorldPoint tile, int animation) {
			this.tile = tile;
			this.animation = animation;
		}
	}

	private List<String> getItemsToKeep() {
		if (itemsToKeep == null) {
			List<String> _itemsToKeep = new ArrayList<>();
			if (Rs2Inventory.hasItem("hammer")) {
				_itemsToKeep.add("hammer");
			}
			if (Rs2Inventory.hasItem("pickaxe")) {
				_itemsToKeep.add("pickaxe");
			}
			if (Rs2Gembag.hasGemBag()) {
				_itemsToKeep.add("gem bag");
			}
			itemsToKeep = _itemsToKeep;
		}
		return itemsToKeep;
	}

    @Override
    public void shutdown()
    {
        log.info("Starting MLM script shutdown");
        Rs2Antiban.resetAntibanSettings();
        Rs2Walker.setTarget(null);
        resetRuntime();
        super.shutdown();
        log.info("MLM script shutdown complete");
    }
}
