package net.runelite.client.plugins.microbot.banksorter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Plans the fewest insert-mode drags that turn one ordering of a tab into another.
 *
 * <p>A move removes the key at {@code from} and inserts it at {@code to}. In the bank that is
 * dropping the item onto the slot currently at {@code to} with insert mode on. Keys on the
 * longest run that is already in sorted order never move, so a plan has exactly
 * {@code n - LIS} moves.
 */
final class BankSortPlanner {

    private BankSortPlanner() {
    }

    static final class Move {
        final int key;
        final int from;
        final int to;

        Move(int key, int from, int to) {
            this.key = key;
            this.from = from;
            this.to = to;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Move)) {
                return false;
            }
            Move m = (Move) o;
            return key == m.key && from == m.from && to == m.to;
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, from, to);
        }

        @Override
        public String toString() {
            return key + ":" + from + "->" + to;
        }
    }

    /**
     * Keys must be unique and both lists must hold the same keys.
     */
    static List<Move> plan(List<Integer> current, List<Integer> desired) {
        int[] ranks = ranks(current, desired);
        Set<Integer> keep = new HashSet<>();
        for (int pos : longestIncreasing(ranks)) {
            keep.add(current.get(pos));
        }

        List<Integer> work = new ArrayList<>(current);
        List<Move> moves = new ArrayList<>(current.size() - keep.size());
        for (int k = 0; k < desired.size(); k++) {
            int key = desired.get(k);
            if (keep.contains(key)) {
                continue;
            }
            int from = work.indexOf(key);
            work.remove(from);
            int to = k == 0 ? 0 : work.indexOf(desired.get(k - 1)) + 1;
            work.add(to, key);
            if (from != to) {
                moves.add(new Move(key, from, to));
            }
        }
        return moves;
    }

    static int movesNeeded(List<Integer> current, List<Integer> desired) {
        return current.size() - longestIncreasing(ranks(current, desired)).length;
    }

    static void apply(List<Integer> list, Move move) {
        int key = list.remove(move.from);
        list.add(move.to, key);
    }

    private static int[] ranks(List<Integer> current, List<Integer> desired) {
        if (current.size() != desired.size()) {
            throw new IllegalArgumentException("size mismatch " + current.size() + " vs " + desired.size());
        }
        Map<Integer, Integer> rank = new HashMap<>(desired.size() * 2);
        for (int i = 0; i < desired.size(); i++) {
            if (rank.put(desired.get(i), i) != null) {
                throw new IllegalArgumentException("duplicate key " + desired.get(i));
            }
        }
        int[] ranks = new int[current.size()];
        for (int i = 0; i < current.size(); i++) {
            Integer r = rank.get(current.get(i));
            if (r == null) {
                throw new IllegalArgumentException("key " + current.get(i) + " not in desired order");
            }
            ranks[i] = r;
        }
        return ranks;
    }

    /** Positions in {@code a} of one longest strictly increasing subsequence. */
    static int[] longestIncreasing(int[] a) {
        int n = a.length;
        int[] tailPos = new int[n];
        int[] prev = new int[n];
        int len = 0;
        for (int i = 0; i < n; i++) {
            int lo = 0;
            int hi = len;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (a[tailPos[mid]] < a[i]) {
                    lo = mid + 1;
                } else {
                    hi = mid;
                }
            }
            prev[i] = lo > 0 ? tailPos[lo - 1] : -1;
            tailPos[lo] = i;
            if (lo == len) {
                len++;
            }
        }
        int[] out = new int[len];
        int p = len > 0 ? tailPos[len - 1] : -1;
        for (int i = len - 1; i >= 0; i--) {
            out[i] = p;
            p = prev[p];
        }
        return out;
    }
}
