package net.runelite.client.plugins.microbot.oprahousethieving;

import net.runelite.api.coords.WorldPoint;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpraHouseThievingScriptTest {

    @Test
    void visibleDoorIsClickedInsteadOfPatrolling() {
        OpraHouseThievingScript.ScoutChoice choice = OpraHouseThievingScript.chooseScoutDoor(
                ThievingHouse.CAIUS,
                Collections.singletonList(door(ThievingHouse.VICTOR, true, 6)));

        assertEquals(OpraHouseThievingScript.ScoutMove.CLICK, choice.move);
        assertEquals(ThievingHouse.VICTOR, choice.house);
    }

    @Test
    void currentHouseWinsWhenItsDoorIsVisible() {
        OpraHouseThievingScript.ScoutChoice choice = OpraHouseThievingScript.chooseScoutDoor(
                ThievingHouse.VICTOR,
                Arrays.asList(
                        door(ThievingHouse.CAIUS, true, 4),
                        door(ThievingHouse.VICTOR, true, 12)
                ));

        assertEquals(OpraHouseThievingScript.ScoutMove.CLICK, choice.move);
        assertEquals(ThievingHouse.VICTOR, choice.house);
    }
    @Test
    void nearestVisibleDoorWinsWhenTheCurrentHouseIsClosed() {
        OpraHouseThievingScript.ScoutChoice choice = OpraHouseThievingScript.chooseScoutDoor(
                ThievingHouse.LAVINIA,
                Arrays.asList(
                        door(ThievingHouse.VICTOR, true, 20),
                        door(ThievingHouse.CAIUS, true, 5)
                ));

        assertEquals(OpraHouseThievingScript.ScoutMove.CLICK, choice.move);
        assertEquals(ThievingHouse.CAIUS, choice.house);
    }

    @Test
    void offscreenDoorIsApproachedInsteadOfTheScoutTile() {
        OpraHouseThievingScript.ScoutChoice choice = OpraHouseThievingScript.chooseScoutDoor(
                ThievingHouse.LAVINIA,
                Arrays.asList(
                        door(ThievingHouse.LAVINIA, false, 18),
                        door(ThievingHouse.VICTOR, false, 6)
                ));

        assertEquals(OpraHouseThievingScript.ScoutMove.APPROACH_DOOR, choice.move);
        assertEquals(ThievingHouse.VICTOR, choice.house);
    }

    @Test
    void noDoorKeepsThePatrol() {
        OpraHouseThievingScript.ScoutChoice choice = OpraHouseThievingScript.chooseScoutDoor(
                ThievingHouse.CAIUS,
                Collections.emptyList());

        assertEquals(OpraHouseThievingScript.ScoutMove.PATROL, choice.move);
        assertNull(choice.house);
    }

    @Test
    void ownerAtTheDoorIsNotClicked() {
        OpraHouseThievingScript.ScoutChoice choice = OpraHouseThievingScript.chooseScoutDoor(
                ThievingHouse.VICTOR,
                Arrays.asList(
                        new OpraHouseThievingScript.ScoutDoor(ThievingHouse.VICTOR, true, 2, true),
                        door(ThievingHouse.CAIUS, true, 4)
                ));

        assertEquals(OpraHouseThievingScript.ScoutMove.CLICK, choice.move);
        assertEquals(ThievingHouse.CAIUS, choice.house);
    }

    @Test
    void onlyAnOwnerDoorIsApproachedNotClicked() {
        OpraHouseThievingScript.ScoutChoice choice = OpraHouseThievingScript.chooseScoutDoor(
                ThievingHouse.LAVINIA,
                Collections.singletonList(new OpraHouseThievingScript.ScoutDoor(ThievingHouse.CAIUS, true, 2, true)));

        assertEquals(OpraHouseThievingScript.ScoutMove.APPROACH_DOOR, choice.move);
        assertEquals(ThievingHouse.CAIUS, choice.house);
    }

    @Test
    void freeOffscreenDoorBeatsAnOwnerDoor() {
        OpraHouseThievingScript.ScoutChoice choice = OpraHouseThievingScript.chooseScoutDoor(
                ThievingHouse.LAVINIA,
                Arrays.asList(
                        new OpraHouseThievingScript.ScoutDoor(ThievingHouse.CAIUS, true, 2, true),
                        door(ThievingHouse.VICTOR, false, 20)
                ));

        assertEquals(OpraHouseThievingScript.ScoutMove.APPROACH_DOOR, choice.move);
        assertEquals(ThievingHouse.VICTOR, choice.house);
    }

    @Test
    void ownerDoorOfTheHouseJustLeftIsIgnored() {
        OpraHouseThievingScript.ScoutChoice choice = OpraHouseThievingScript.chooseScoutDoor(
                ThievingHouse.LAVINIA,
                Collections.singletonList(new OpraHouseThievingScript.ScoutDoor(ThievingHouse.CAIUS, true, 2, true)),
                ThievingHouse.CAIUS);

        assertEquals(OpraHouseThievingScript.ScoutMove.PATROL, choice.move);
        assertNull(choice.house);
    }

    @Test
    void passingThroughTheHouseBoxIsNotAnEntry() {
        assertFalse(OpraHouseThievingScript.isSettledEntry(false, true));
        assertFalse(OpraHouseThievingScript.isSettledEntry(true, false));
        assertTrue(OpraHouseThievingScript.isSettledEntry(true, true));
    }

    @Test
    void lockedDoorNotClosedIsClickedAgain() {
        OpraHouseThievingScript.ScoutChoice choice = OpraHouseThievingScript.chooseScoutDoor(
                ThievingHouse.LAVINIA,
                Collections.singletonList(door(ThievingHouse.LAVINIA, true, 1)),
                (ThievingHouse) null);

        assertEquals(OpraHouseThievingScript.ScoutMove.CLICK, choice.move);
        assertEquals(ThievingHouse.LAVINIA, choice.house);
    }

    @Test
    void returningOwnerDoesNotReopenTheHouse() {
        assertFalse(OpraHouseThievingScript.reopensClosedHouse(false, true, 0));
        assertFalse(OpraHouseThievingScript.reopensClosedHouse(false, true, -1));
        assertFalse(OpraHouseThievingScript.reopensClosedHouse(false, true, 11));
    }

    @Test
    void closedHouseReopensAfterUnlockedThenLocked() {
        assertTrue(OpraHouseThievingScript.reopensClosedHouse(true, true, 6));
        assertTrue(OpraHouseThievingScript.reopensClosedHouse(true, true, 11));
        assertTrue(OpraHouseThievingScript.reopensClosedHouse(true, true, -1));
        assertFalse(OpraHouseThievingScript.reopensClosedHouse(true, true, 3));
        assertFalse(OpraHouseThievingScript.reopensClosedHouse(true, false, 11));
    }

    @Test
    void arrivingOwnerAbortsTheDoorRun() {
        assertTrue(OpraHouseThievingScript.ownerBeatUs(true, -1, -1, false));
        assertTrue(OpraHouseThievingScript.ownerBeatUs(false, 4, 2, false));
        assertTrue(OpraHouseThievingScript.ownerBeatUs(false, 1, 0, false));
    }

    @Test
    void leavingOrMissingOwnerDoesNotAbortTheDoorRun() {
        assertFalse(OpraHouseThievingScript.ownerBeatUs(false, -1, 1, false));
        assertFalse(OpraHouseThievingScript.ownerBeatUs(false, 1, 3, false));
        assertFalse(OpraHouseThievingScript.ownerBeatUs(false, 3, 8, false));
        assertFalse(OpraHouseThievingScript.ownerBeatUs(false, 4, 2, true));
        assertFalse(OpraHouseThievingScript.ownerBeatUs(false, 6, 5, false));
        assertFalse(OpraHouseThievingScript.ownerBeatUs(false, -1, -1, false));
    }

    @Test
    void allOwnersHomeWaitsAtTheHouseLikeliestToOpen() {
        Map<ThievingHouse, Long> unlockedAt = new EnumMap<>(ThievingHouse.class);
        unlockedAt.put(ThievingHouse.LAVINIA, 1_000L);
        unlockedAt.put(ThievingHouse.CAIUS, 500L);
        unlockedAt.put(ThievingHouse.VICTOR, 2_000L);
        Set<ThievingHouse> closed = EnumSet.of(ThievingHouse.LAVINIA, ThievingHouse.CAIUS);

        assertEquals(ThievingHouse.LAVINIA, OpraHouseThievingScript.waitTarget(
                closed, EnumSet.of(ThievingHouse.LAVINIA), unlockedAt));
        assertEquals(ThievingHouse.CAIUS, OpraHouseThievingScript.waitTarget(
                closed, EnumSet.noneOf(ThievingHouse.class), unlockedAt));
        assertEquals(ThievingHouse.LAVINIA, OpraHouseThievingScript.waitTarget(
                EnumSet.noneOf(ThievingHouse.class), EnumSet.noneOf(ThievingHouse.class),
                new EnumMap<>(ThievingHouse.class)));
    }

    @Test
    void bestOwnedPrefersRogueThenGraceful() {
        List<String> head = List.of("Rogue mask", "Graceful hood");
        List<String> cape = List.of("Graceful cape");

        assertEquals("Rogue mask", OpraHouseThievingScript.bestOwned(head, name -> true));
        assertEquals("Graceful hood", OpraHouseThievingScript.bestOwned(head, "Graceful hood"::equals));
        assertNull(OpraHouseThievingScript.bestOwned(head, name -> false));
        assertEquals("Graceful cape", OpraHouseThievingScript.bestOwned(cape, "Graceful cape"::equals));
        assertNull(OpraHouseThievingScript.bestOwned(cape, name -> false));
    }

    @Test
    void gearSlotsCoverSixSlots() {
        List<List<String>> slots = OpraHouseThievingScript.gearSlots();

        assertEquals(6, slots.size());
        assertEquals(List.of("Rogue mask", "Graceful hood"), slots.get(0));
        assertEquals(List.of("Rogue boots", "Graceful boots"), slots.get(4));
        assertEquals(List.of("Graceful cape"), slots.get(5));
    }

    @Test
    void bestGearNeedsFullRogueAndGracefulCape() {
        Set<String> full = new HashSet<>(OpraHouseThievingScript.ROGUE_GEAR);
        full.add("Graceful cape");
        assertTrue(OpraHouseThievingScript.isBestGear(full::contains));

        Set<String> noCape = new HashSet<>(OpraHouseThievingScript.ROGUE_GEAR);
        assertFalse(OpraHouseThievingScript.isBestGear(noCape::contains));

        Set<String> gracefulTop = new HashSet<>(full);
        gracefulTop.remove("Rogue top");
        gracefulTop.add("Graceful top");
        assertFalse(OpraHouseThievingScript.isBestGear(gracefulTop::contains));
    }

    @Test
    void needsGearBankUnlessBestGearAndKeys() {
        assertFalse(OpraHouseThievingScript.needsGearBank(true, 5));
        assertTrue(OpraHouseThievingScript.needsGearBank(true, 0));
        assertTrue(OpraHouseThievingScript.needsGearBank(false, 5));
        assertTrue(OpraHouseThievingScript.needsGearBank(false, 0));
    }

    @Test
    void depositKeepsOnlyKeysAndValuables() {
        assertTrue(OpraHouseThievingScript.keepOnDeposit("House keys"));
        assertTrue(OpraHouseThievingScript.keepOnDeposit("Valuables"));
        assertTrue(OpraHouseThievingScript.keepOnDeposit("valuables"));
        assertFalse(OpraHouseThievingScript.keepOnDeposit("Coins"));
        assertFalse(OpraHouseThievingScript.keepOnDeposit("Coin pouch"));
        assertFalse(OpraHouseThievingScript.keepOnDeposit("Sapphire ring"));
        assertFalse(OpraHouseThievingScript.keepOnDeposit("Blessed bone statuette"));
        assertFalse(OpraHouseThievingScript.keepOnDeposit("Graceful top"));
        assertFalse(OpraHouseThievingScript.keepOnDeposit(null));
    }

    @Test
    void afterBankingRunsWhileKeysLast() {
        assertEquals(OpraHouseThievingScript.State.FINDING_HOUSE, OpraHouseThievingScript.stateAfterBank(5, 2));
        assertEquals(OpraHouseThievingScript.State.FINDING_HOUSE, OpraHouseThievingScript.stateAfterBank(2, 2));
        assertEquals(OpraHouseThievingScript.State.PICKPOCKETING, OpraHouseThievingScript.stateAfterBank(1, 2));
        assertEquals(OpraHouseThievingScript.State.PICKPOCKETING, OpraHouseThievingScript.stateAfterBank(0, 2));
    }

    @Test
    void bankPinMustBeFourDigits() {
        assertTrue(OpraHouseThievingScript.isUsablePin("1234"));
        assertFalse(OpraHouseThievingScript.isUsablePin(null));
        assertFalse(OpraHouseThievingScript.isUsablePin(""));
        assertFalse(OpraHouseThievingScript.isUsablePin("123"));
        assertFalse(OpraHouseThievingScript.isUsablePin("12345"));
        assertFalse(OpraHouseThievingScript.isUsablePin("12a4"));
        assertFalse(OpraHouseThievingScript.isUsablePin(" 1234"));
    }

    @Test
    void ownerGettingCloserFromOutsideIsArriving() {
        assertTrue(OpraHouseThievingScript.ownerArriving(4, 2, false));
        assertFalse(OpraHouseThievingScript.ownerArriving(0, 3, false));
        assertFalse(OpraHouseThievingScript.ownerArriving(4, 2, true));
        assertFalse(OpraHouseThievingScript.ownerArriving(-1, 2, false));
        assertFalse(OpraHouseThievingScript.ownerArriving(2, 2, false));
    }

    @Test
    void stalestOpenHouseIsPatrolledNext() {
        Map<ThievingHouse, Long> unlockedAt = new EnumMap<>(ThievingHouse.class);
        unlockedAt.put(ThievingHouse.LAVINIA, 1_000L);
        unlockedAt.put(ThievingHouse.CAIUS, 2_000L);

        assertEquals(ThievingHouse.LAVINIA, OpraHouseThievingScript.pickNextHouse(ThievingHouse.VICTOR,
                EnumSet.noneOf(ThievingHouse.class), EnumSet.of(ThievingHouse.VICTOR), unlockedAt));
    }

    @Test
    void lockedDoorInSightBeatsTheStalestHouse() {
        Map<ThievingHouse, Long> unlockedAt = new EnumMap<>(ThievingHouse.class);
        unlockedAt.put(ThievingHouse.LAVINIA, 1_000L);
        unlockedAt.put(ThievingHouse.CAIUS, 2_000L);

        assertEquals(ThievingHouse.CAIUS, OpraHouseThievingScript.pickNextHouse(ThievingHouse.VICTOR,
                EnumSet.of(ThievingHouse.CAIUS), EnumSet.noneOf(ThievingHouse.class), unlockedAt));
    }

    @Test
    void closedHousesAreSkippedAndTiesKeepThePatrolOrder() {
        Map<ThievingHouse, Long> none = new EnumMap<>(ThievingHouse.class);

        assertEquals(ThievingHouse.VICTOR, OpraHouseThievingScript.pickNextHouse(ThievingHouse.LAVINIA,
                EnumSet.noneOf(ThievingHouse.class), EnumSet.noneOf(ThievingHouse.class), none));
        assertEquals(ThievingHouse.CAIUS, OpraHouseThievingScript.pickNextHouse(ThievingHouse.LAVINIA,
                EnumSet.noneOf(ThievingHouse.class), EnumSet.of(ThievingHouse.VICTOR), none));
        assertEquals(ThievingHouse.LAVINIA, OpraHouseThievingScript.pickNextHouse(ThievingHouse.LAVINIA,
                EnumSet.noneOf(ThievingHouse.class), EnumSet.of(ThievingHouse.VICTOR, ThievingHouse.CAIUS), none));
    }

    @Test
    void closedHouseIsIgnoredWithOthersClosedToo() {
        OpraHouseThievingScript.ScoutChoice choice = OpraHouseThievingScript.chooseScoutDoor(
                ThievingHouse.VICTOR,
                Arrays.asList(
                        door(ThievingHouse.LAVINIA, true, 3),
                        door(ThievingHouse.CAIUS, true, 4),
                        door(ThievingHouse.VICTOR, true, 5)
                ),
                EnumSet.of(ThievingHouse.LAVINIA, ThievingHouse.CAIUS));

        assertEquals(OpraHouseThievingScript.ScoutMove.CLICK, choice.move);
        assertEquals(ThievingHouse.VICTOR, choice.house);
    }

    @Test
    void houseJustLeftIsIgnored() {
        OpraHouseThievingScript.ScoutChoice choice = OpraHouseThievingScript.chooseScoutDoor(
                ThievingHouse.VICTOR,
                Arrays.asList(
                        door(ThievingHouse.LAVINIA, true, 3),
                        door(ThievingHouse.VICTOR, true, 5)
                ),
                ThievingHouse.LAVINIA);

        assertEquals(OpraHouseThievingScript.ScoutMove.CLICK, choice.move);
        assertEquals(ThievingHouse.VICTOR, choice.house);
    }

    @Test
    void streetAndWindowTilesAreOutside() {
        assertFalse(OpraHouseThievingScript.isInsideHouse(ThievingHouse.VICTOR, new WorldPoint(1636, 3105, 0)));
        assertTrue(OpraHouseThievingScript.isInsideHouse(ThievingHouse.LAVINIA, new WorldPoint(1670, 3087, 0)));
        assertFalse(OpraHouseThievingScript.isInsideHouse(ThievingHouse.LAVINIA, new WorldPoint(1668, 3095, 0)));
        assertTrue(OpraHouseThievingScript.isInsideHouse(ThievingHouse.LAVINIA, new WorldPoint(1670, 3091, 0)));
        assertTrue(OpraHouseThievingScript.isInsideHouse(ThievingHouse.VICTOR, new WorldPoint(1636, 3103, 0)));
        assertTrue(OpraHouseThievingScript.isInsideHouse(ThievingHouse.CAIUS, new WorldPoint(1635, 3118, 0)));
        assertFalse(OpraHouseThievingScript.isInsideHouse(ThievingHouse.CAIUS, new WorldPoint(1635, 3125, 0)));
        assertFalse(OpraHouseThievingScript.isInsideHouse(ThievingHouse.CAIUS, new WorldPoint(1638, 3125, 0)));
    }

    private static OpraHouseThievingScript.ScoutDoor door(ThievingHouse house, boolean visible, int distance) {
        return new OpraHouseThievingScript.ScoutDoor(house, visible, distance);
    }
}
