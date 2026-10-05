package net.runelite.client.plugins.microbot.motherloadmine;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameObject;
import net.runelite.api.Perspective;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.player.Rs2PlayerCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.motherloadmine.enums.MLMMiningSpot;
import net.runelite.client.plugins.microbot.motherloadmine.enums.MLMStatus;
import net.runelite.client.plugins.microbot.motherloadmine.enums.Pickaxe;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
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

@Slf4j
public class MotherloadMineScript extends Script
{

    private static final int SPOT_VEIN_RADIUS = 2;

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
    private int maxSackSize;
	private List<String> itemsToKeep;

	private final MotherloadMinePlugin plugin;
    private final MotherloadMineConfig config;
    private final Rs2TileObjectCache rs2TileObjectCache;
    private final Rs2PlayerCache rs2PlayerCache;


	private boolean shouldEmptySack = false;
	private boolean shouldRepairWaterwheel = false;
	private boolean emptySackWorkflowActive = false;
	private int cameraTurnedTowardId;
	private boolean turnedCameraThisCall;
	private long idleSince = 0;
	private int idleThreshold = 0;
	private boolean pickedUpHammer = false;
    private MLMStatus lastLoggedStatus = null;

	@Inject
	public MotherloadMineScript(MotherloadMinePlugin plugin, MotherloadMineConfig config, Rs2TileObjectCache rs2TileObjectCache, Rs2PlayerCache rs2PlayerCache)
	{
		this.plugin = plugin;
		this.config = config;
        this.rs2TileObjectCache = rs2TileObjectCache;
        this.rs2PlayerCache = rs2PlayerCache;
    }

    public boolean run()
    {
        log.info("Starting MotherloadMine script");
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
        lastLoggedStatus = null;
        idleSince = 0;
        idleThreshold = 0;
        shouldEmptySack = false;
        shouldRepairWaterwheel = false;
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
                if (Rs2Player.isAnimating()) return;
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
        updateSackSize();
        if (!hasRequiredTools())
        {
            log.info("Missing required tools, running inventory setup");
            setupInventory();
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
            boolean lastDeposit = currentSackCount() + payDirtCount() >= SACK_SIZE;
            if (lastDeposit && config.fixWaterwheel() && getBrokenStrutCount() > 1) {
                status = MLMStatus.FIXING_WATERWHEEL;
                return;
            }
            status = MLMStatus.DEPOSIT_HOPPER;
            return;
        }
        status = MLMStatus.MINING;
    }

    private boolean sackNeedsEmpty()
    {
        int sack = currentSackCount();
        if (sack >= SACK_SIZE) {
            return true;
        }
        if (sack >= SACK_SIZE - 28 && payDirtCount() == 0) {
            return true;
        }
        if (hasOreInInventory() || (shouldEmptySack && !Rs2Inventory.contains(ItemID.PAYDIRT))) {
            return true;
        }
        return emptySackWorkflowActive && (sack > 0 || hasOreInInventory());
    }

    private boolean hasRequiredTools()
    {
		return Pickaxe.hasItem();
    }

    private void updateSackSize()
    {
        maxSackSize = SACK_SIZE;
    }

	private void handleMining()
	{
		if (Rs2Player.getAnimation() != net.runelite.api.AnimationID.IDLE || Rs2Player.isMoving()) {
			idleSince = 0;
			return;
		}
		if (idleSince == 0) {
			idleSince = System.currentTimeMillis();
			idleThreshold = Math.max(2000, Rs2Random.randomGaussian(3000, 600));
			return;
		}
		if (System.currentTimeMillis() - idleSince < idleThreshold) return;
		idleSince = 0;

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

		if (findClosestVein() != null)
		{
			attemptToMineVein();
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
			Rs2Walker.walkTo(objectTile(sack, SACK_TILE), 2);
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
		shouldEmptySack = false;
		shouldRepairWaterwheel = false;
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
        log.info("Fixing waterwheel workflow started");
        ensureLowerFloor();

		if (!hasHammer()) {
			if (!obtainHammer()) return;
		}

		if (rs2TileObjectCache.query().interact(ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN))
		{
			// We use a modified version of waitForXpDrop to ensure we break out of the sleep if the strut is repaired
			final int skillExp = Microbot.getClientThread().invoke(() -> Microbot.getClient().getSkillExperience(Skill.SMITHING));
			sleepUntilTrue(() -> skillExp != Microbot.getClientThread().invoke(() -> Microbot.getClient().getSkillExperience(Skill.SMITHING)) || getBrokenStrutCount() <= 1, 250, 20_000);

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
			sleepUntil(Rs2Gembag::isGemBagOpen);
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

			shouldRepairWaterwheel = true;

            // Calculate the effective sack size after deposit as VarbitID.MOTHERLODE_SACK_TRANSMIT takes time to update
            final int currentSackAmount = currentSackCount();
            final int effectiveSackAmount = Math.max(currentSackAmount, Math.min(maxSackSize, currentSackAmount + paydirtToDeposit));

			shouldEmptySack = effectiveSackAmount >= (maxSackSize - 28);
            log.debug("Hopper deposit complete: paydirtDeposited={}, effectiveSackAmount={}, shouldEmptySack={}",
                    paydirtToDeposit, effectiveSackAmount, shouldEmptySack);
        }
        else
        {
            log.debug("Hopper unavailable, walking closer to deposit point");
            Rs2Walker.walkTo(hopperDeposit, 15);
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
                    Rs2Walker.walkTo(objectTile(box, DEPOSIT_BOX_TILE), 2);
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

        List<String> keep = new ArrayList<>(getItemsToKeep());
        if (!keep.contains("pay-dirt"))
        {
            keep.add("pay-dirt");
        }
        Rs2DepositBox.depositAllExcept(keep, false);
        Rs2Inventory.waitForInventoryChanges(5000);

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
        log.info("Running MLM inventory setup (useInventorySetup={})", config.useInventorySetup());
		if (!config.useInventorySetup()) {
			Rs2ItemModel pickaxe = Pickaxe.getBestPickaxe();

			if (pickaxe == null) {
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

				// Only equip if it has attack requirements, otherwise keep in inventory
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

				// Get gem bag and hammer
				final int[] gemBagIDs = {ItemID.GEM_BAG, ItemID.GEM_BAG_OPEN};
				for (int gemBagID : gemBagIDs) {
					if (!isRunning()) break;
					if (Rs2Bank.withdrawOne(gemBagID)) {
						Rs2Inventory.waitForInventoryChanges(5000);
						break;
					}
				}

				if (Rs2Random.dicePercentage(10) && !hasHammer()) {
					if (Rs2Bank.withdrawOne("hammer")) {
						Rs2Inventory.waitForInventoryChanges(5000);
					}
				}

				Rs2Bank.toggleItemLock("pickaxe", false);
				Rs2Bank.toggleItemLock("hammer", false);
				Rs2Bank.toggleItemLock("gem bag", false);
			}

		} else {
			Rs2InventorySetup mlmInventorySetup = new Rs2InventorySetup(config.getInventorySetup(), mainScheduledFuture);
			boolean doesEquipmentMatch = true;
			boolean doesInventoryMatch = true;

			if (!mlmInventorySetup.doesEquipmentMatch()) {
				doesEquipmentMatch = mlmInventorySetup.loadEquipment();
			}

			if (!mlmInventorySetup.doesInventoryMatch()) {
				doesInventoryMatch = mlmInventorySetup.loadInventory();
			}

			if (!doesEquipmentMatch || !doesInventoryMatch) {
				Microbot.showMessage("Failed to load inventory setup. Please check your settings.");
                log.warn("Inventory setup failed (equipmentMatch={}, inventoryMatch={}), stopping plugin",
                        doesEquipmentMatch, doesInventoryMatch);
				Microbot.stopPlugin(plugin);
				return;
			}
		}

		Rs2Bank.closeBank();
		sleepUntil(() -> !Rs2Bank.isOpen());
        log.info("Inventory setup complete");
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

        return Rs2Walker.walkTo(target, 2);
    }

	private boolean attemptToMineVein() {
        Rs2TileObjectModel vein = findClosestVein();
		if (vein == null) {
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
				Rs2Walker.walkTo(stand, 1);
				return false;
			}
		}

		cameraTurnedTowardId = 0;
		handlePickaxeSpec();

		if (!vein.click("Mine")) return false;

		return sleepUntil(() -> {
			WorldPoint here = playerLocation();
			return AntibanPlugin.isMining() && here != null && veinLocation.distanceTo(here) <= 2;
		}, 10_000);
	}

    private Rs2TileObjectModel findClosestVein()
    {
        return rs2TileObjectCache.query().where(this::isValidVein).nearest();
    }

    private boolean isValidVein(Rs2TileObjectModel wallObject)
    {
        int id = wallObject.getId();
        boolean isVein = (id == 26661 || id == 26662 || id == 26663 || id == 26664);
        if (!isVein) return false;

        WorldPoint location = wallObject.getWorldLocation();
        if (location == null || !belongsToSelectedSpot(location)) {
            return false;
        }

		if (miningSpot.isDownstairs() && config.useAntiCrash() && otherPlayerNear(location))
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

    private boolean otherPlayerNear(WorldPoint veinTile)
    {
        String localName = Microbot.getClientThread().invoke(() -> {
            if (Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null) {
                return null;
            }
            return Microbot.getClient().getLocalPlayer().getName();
        });
        if (localName == null) {
            return false;
        }
        return rs2PlayerCache.query().where(p -> {
            if (p == null || p.getWorldLocation() == null) {
                return false;
            }
            if (p.getWorldLocation().distanceTo(veinTile) > 2) {
                return false;
            }
            String name = p.getName();
            return name != null && !name.equals(localName);
        }).firstOnClientThread() != null;
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
        Rs2Walker.walkTo(target, 2);
    }

    private void goUp()
    {
        if (isUpperFloor()) return;
        log.debug("Transitioning to upper floor");

		Rs2TileObjectModel ladder = rs2TileObjectCache.query().withId(ObjectID.MOTHERLODE_LADDER_BOTTOM).nearestReachable();
		if (ladder == null) {
			Rs2Walker.walkTo(miningSpot.getWorldPoint().get(0), 6);
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
			Rs2Walker.walkTo(HOPPER_DEPOSIT_DOWN, 6);
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
		List<Rs2TileObjectModel> brokenStruts = rs2TileObjectCache.query().where(o -> o.getId() == ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN).toListOnClientThread();
		return brokenStruts.isEmpty() ? 0 : brokenStruts.size();
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
