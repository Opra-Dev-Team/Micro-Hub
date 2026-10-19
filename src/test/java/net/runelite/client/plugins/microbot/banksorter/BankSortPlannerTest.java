package net.runelite.client.plugins.microbot.banksorter;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankSortPlannerTest {

    @Test
    void sortedTabNeedsNoMoves() {
        List<Integer> ids = List.of(1, 2, 3, 4);
        assertTrue(BankSortPlanner.plan(ids, ids).isEmpty());
    }

    @Test
    void singleOutOfPlaceItemIsOneMove() {
        List<Integer> current = List.of(2, 3, 4, 5, 1);
        List<Integer> desired = List.of(1, 2, 3, 4, 5);

        List<BankSortPlanner.Move> moves = BankSortPlanner.plan(current, desired);

        assertEquals(List.of(new BankSortPlanner.Move(1, 4, 0)), moves);
        assertEquals(desired, applyAll(current, moves));
    }

    @Test
    void forwardMoveDropsOntoTargetSlot() {
        List<Integer> current = List.of(5, 1, 2, 3, 4);
        List<Integer> desired = List.of(1, 2, 3, 4, 5);

        List<BankSortPlanner.Move> moves = BankSortPlanner.plan(current, desired);

        assertEquals(List.of(new BankSortPlanner.Move(5, 0, 4)), moves);
        assertEquals(desired, applyAll(current, moves));
    }

    @Test
    void reversedTabMovesAllButOne() {
        List<Integer> desired = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            desired.add(i);
        }
        List<Integer> current = new ArrayList<>(desired);
        Collections.reverse(current);

        List<BankSortPlanner.Move> moves = BankSortPlanner.plan(current, desired);

        assertEquals(49, moves.size());
        assertEquals(desired, applyAll(current, moves));
    }

    @Test
    void randomTabsSortWithMinimumMoves() {
        Random random = new Random(7);
        for (int round = 0; round < 500; round++) {
            int n = 1 + random.nextInt(round < 450 ? 40 : 800);
            List<Integer> desired = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                desired.add(1000 + i * 3);
            }
            List<Integer> current = new ArrayList<>(desired);
            Collections.shuffle(current, random);

            List<BankSortPlanner.Move> moves = BankSortPlanner.plan(current, desired);

            assertEquals(desired, applyAll(current, moves), "round " + round);
            assertEquals(BankSortPlanner.movesNeeded(current, desired), moves.size(), "round " + round);
            assertEquals(n - lisBruteForceLength(current, desired), moves.size(), "round " + round);
        }
    }

    @Test
    void nearlySortedBigTabIsCheap() {
        List<Integer> desired = new ArrayList<>();
        for (int i = 0; i < 260; i++) {
            desired.add(i);
        }
        List<Integer> current = new ArrayList<>(desired);
        Collections.swap(current, 3, 250);
        current.add(120, current.remove(259));

        List<BankSortPlanner.Move> moves = BankSortPlanner.plan(current, desired);

        assertEquals(3, moves.size());
        assertEquals(desired, applyAll(current, moves));
    }

    @Test
    void pinnedItemsStayPutWhileTheRestSorts() {
        Random random = new Random(11);
        for (int round = 0; round < 300; round++) {
            int n = 2 + random.nextInt(60);
            List<Integer> desired = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                desired.add(i);
            }
            List<Integer> live = new ArrayList<>(desired);
            Collections.shuffle(live, random);
            Set<Integer> pinned = new HashSet<>();
            for (int i = 0; i < 1 + random.nextInt(3); i++) {
                pinned.add(live.get(random.nextInt(n)));
            }
            for (int step = 0; step <= n; step++) {
                List<BankSortPlanner.Move> plan = BankSortPlanner.plan(
                        BankInTabSorter.without(live, pinned), BankInTabSorter.without(desired, pinned));
                if (plan.isEmpty()) {
                    break;
                }
                BankSortPlanner.Move move = BankInTabSorter.toLive(live, pinned, plan.get(0));
                assertEquals(move.key, (int) live.get(move.from), "round " + round);
                assertFalse(pinned.contains(move.key), "pinned item dragged, round " + round);
                BankSortPlanner.apply(live, move);
            }

            assertEquals(BankInTabSorter.without(desired, pinned), BankInTabSorter.without(live, pinned),
                    "round " + round);
        }
    }

    @Test
    void liveMoveIsUnchangedWithoutPins() {
        BankSortPlanner.Move move = new BankSortPlanner.Move(5, 0, 4);
        assertEquals(move, BankInTabSorter.toLive(List.of(5, 1, 2, 3, 4), Set.of(), move));
    }

    private static List<Integer> applyAll(List<Integer> start, List<BankSortPlanner.Move> moves) {
        List<Integer> list = new ArrayList<>(start);
        for (BankSortPlanner.Move move : moves) {
            assertEquals(move.key, (int) list.get(move.from), "move " + move);
            BankSortPlanner.apply(list, move);
        }
        return list;
    }

    private static int lisBruteForceLength(List<Integer> current, List<Integer> desired) {
        int n = current.size();
        int[] rank = new int[n];
        for (int i = 0; i < n; i++) {
            rank[i] = desired.indexOf(current.get(i));
        }
        int[] best = new int[n];
        int max = 0;
        for (int i = 0; i < n; i++) {
            best[i] = 1;
            for (int j = 0; j < i; j++) {
                if (rank[j] < rank[i]) {
                    best[i] = Math.max(best[i], best[j] + 1);
                }
            }
            max = Math.max(max, best[i]);
        }
        return max;
    }
}
