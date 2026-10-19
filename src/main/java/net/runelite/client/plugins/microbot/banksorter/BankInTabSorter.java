package net.runelite.client.plugins.microbot.banksorter;

import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class BankInTabSorter {
    private BankInTabSorter() {
    }

    static List<BankSortItem> sortItems(List<BankSortItem> items) {
        if (items.isEmpty()) {
            return items;
        }
        Map<String, List<BankSortItem>> byCategory = items.stream()
                .collect(Collectors.groupingBy(BankSortItem::getCategory));

        List<BankSortItem> finalSorted = new ArrayList<>();
        for (String category : BankClassifier.CATEGORY_ORDER) {
            List<BankSortItem> categoryItems = byCategory.remove(category);
            if (categoryItems != null) {
                categoryItems.sort(BankInTabSorter::compareWithinCategory);
                finalSorted.addAll(categoryItems);
            }
        }
        byCategory.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    entry.getValue().sort(BankInTabSorter::compareWithinCategory);
                    finalSorted.addAll(entry.getValue());
                });
        return finalSorted;
    }

    private static final int MAX_KEY_ATTEMPTS = 3;

    /**
     * Keys for the planner. Sortable items use their id. Slots the sorter does not sort, like
     * bank fillers, get unique negative keys by order and stay at the end of the tab.
     */
    static final class TabKeys {
        final List<Integer> current;
        final List<Integer> desired;

        TabKeys(List<Integer> current, List<Integer> desired) {
            this.current = current;
            this.desired = desired;
        }
    }

    static TabKeys buildKeys(int[] liveIds, List<Integer> sortedIds) {
        Set<Integer> sortable = new HashSet<>(sortedIds);
        Set<Integer> seen = new HashSet<>();
        List<Integer> current = new ArrayList<>(liveIds.length);
        List<Integer> extras = new ArrayList<>();
        int other = 0;
        for (int id : liveIds) {
            if (sortable.contains(id) && seen.add(id)) {
                current.add(id);
            } else {
                int key = -(++other);
                current.add(key);
                extras.add(key);
            }
        }
        List<Integer> desired = new ArrayList<>(current.size());
        for (int id : sortedIds) {
            if (seen.contains(id)) {
                desired.add(id);
            }
        }
        desired.addAll(extras);
        return new TabKeys(current, desired);
    }

    /** Insert moves still needed to sort the live tab. */
    static int movesNeeded(int tab, List<BankSortItem> sorted) {
        int[] range = BankTabMover.tabRange(tab);
        TabKeys keys = buildKeys(BankTabMover.containerIds(range[0], range[1]), ids(sorted));
        return BankSortPlanner.movesNeeded(keys.current, keys.desired);
    }

    /**
     * Plans the fewest moves from the live tab, does the first one, then plans again from
     * the live tab. Re-planning after every move doubles as verify and repair: a drag that
     * landed wrong is simply part of the next plan. An item that fails {@value #MAX_KEY_ATTEMPTS}
     * times is pinned where it is and the rest of the tab is still sorted around it.
     *
     * @return true when the tab ended in sorted order
     */
    static boolean rearrange(int tab, List<BankSortItem> sorted, BankSorterScript script, BankOrganiserDebug debug) {
        List<Integer> sortedIds = ids(sorted);
        Map<Integer, String> names = new HashMap<>();
        for (BankSortItem item : sorted) {
            names.put(item.getId(), item.getOriginalName());
        }
        Map<Integer, Integer> attempts = new HashMap<>();
        Map<Integer, String> pinned = new LinkedHashMap<>();
        int initial = -1;
        int budget = Integer.MAX_VALUE;
        int steps = 0;
        int moved = 0;
        int humanRetries = 0;
        boolean retriedPinned = false;
        Map<Integer, Integer> staleRetries = new HashMap<>();
        while (true) {
            if (script.isStopRequested() || !Rs2Bank.isOpen() || !script.waitForHandsOff()) {
                return false;
            }
            if (Rs2Bank.getCurrentTab() != tab && !BankTabMover.openTab(tab)) {
                debug.info("Tab " + tab + " could not be reopened");
                script.recordFailure();
                return false;
            }
            int[] range = BankTabMover.tabRange(tab);
            int[] liveIds = BankTabMover.containerIds(range[0], range[1]);
            TabKeys keys = buildKeys(liveIds, sortedIds);
            List<BankSortPlanner.Move> plan = BankSortPlanner.plan(
                    without(keys.current, pinned.keySet()), without(keys.desired, pinned.keySet()));
            if (initial < 0) {
                initial = plan.size();
                budget = initial * 2 + 20;
                script.planStage(plan.size());
                debug.info("Tab " + tab + " plan: " + plan.size() + " moves for " + keys.current.size() + " slots");
            } else {
                script.replan(plan.size());
            }
            if (plan.isEmpty()) {
                if (pinned.isEmpty()) {
                    debug.info("Tab " + tab + " sorted and verified");
                    return true;
                }
                if (!retriedPinned) {
                    retriedPinned = true;
                    debug.info("Tab " + tab + ": retrying " + pinned.size() + " skipped item(s) once");
                    for (Integer key : pinned.keySet()) {
                        attempts.put(key, MAX_KEY_ATTEMPTS - 1);
                    }
                    pinned.clear();
                    continue;
                }
                debug.info("Tab " + tab + ": " + moved + " of " + (moved + pinned.size()) + " moved, skipped "
                        + describePinned(pinned, names));
                for (int i = 0; i < pinned.size(); i++) {
                    script.recordFailure();
                }
                return false;
            }
            if (steps >= budget) {
                debug.info("Tab " + tab + " stopped after " + budget + " steps without converging ("
                        + plan.size() + " moves left)");
                script.recordFailure();
                return false;
            }
            steps++;

            BankSortPlanner.Move move = toLive(keys.current, pinned.keySet(), plan.get(0));
            int itemId = liveIds[move.from];
            String name = names.getOrDefault(itemId, String.valueOf(itemId));
            if (BankTabMover.itemIdAtSlot(range[0] + move.from) != itemId) {
                BankTabMover.awaitSync(range[0] + move.from);
                if (BankTabMover.containerIdAt(range[0] + move.from) != itemId) {
                    continue;
                }
            }
            long started = System.currentTimeMillis();
            BankTabMover.MoveOutcome outcome = BankTabMover.moveWithinTab(
                    range[0] + move.from, itemId, range[0] + move.to);
            long ms = System.currentTimeMillis() - started;
            debug.trace(String.format("MOVE tab=%d %s from=%d to=%d %s %s %dms",
                    tab, name, move.from, move.to, outcome.ok() ? "OK" : "FAIL", outcome, ms));
            if (outcome.ok()) {
                moved++;
                attempts.remove(move.key);
                script.recordMove(ms);
                script.pauseAfterMove();
                continue;
            }
            if (outcome.humanInput() && humanRetries < MAX_HUMAN_RETRIES) {
                humanRetries++;
                steps--;
                continue;
            }
            if (BankTabMover.REASON_SOURCE_MOVED.equals(outcome.reason)
                    && staleRetries.merge(move.key, 1, Integer::sum) <= MAX_KEY_ATTEMPTS) {
                BankTabMover.awaitSync(range[0] + move.from);
                steps--;
                continue;
            }
            int tries = attempts.merge(move.key, 1, Integer::sum);
            if (tries >= MAX_KEY_ATTEMPTS) {
                pinned.put(move.key, outcome.reason);
                debug.info("Tab " + tab + ": skipping " + name + " after " + tries + " tries (" + outcome.reason + ")");
            }
        }
    }

    private static final int MAX_HUMAN_RETRIES = 20;

    private static String describePinned(Map<Integer, String> pinned, Map<Integer, String> names) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : pinned.entrySet()) {
            parts.add(names.getOrDefault(entry.getKey(), String.valueOf(entry.getKey())) + " (" + entry.getValue() + ")");
        }
        return String.join(", ", parts);
    }

    static List<Integer> without(List<Integer> keys, Set<Integer> pinned) {
        if (pinned.isEmpty()) {
            return keys;
        }
        List<Integer> out = new ArrayList<>(keys.size());
        for (Integer key : keys) {
            if (!pinned.contains(key)) {
                out.add(key);
            }
        }
        return out;
    }

    /**
     * Turns a move planned with the pinned keys left out into a move on the live tab. The item
     * goes right after the key it follows in the plan, or right before the first one, so pinned
     * items are never dragged and the planned order of everything else holds.
     */
    static BankSortPlanner.Move toLive(List<Integer> live, Set<Integer> pinned, BankSortPlanner.Move move) {
        if (pinned.isEmpty()) {
            return move;
        }
        List<Integer> rest = without(live, pinned);
        rest = new ArrayList<>(rest);
        rest.remove(Integer.valueOf(move.key));
        List<Integer> liveRest = new ArrayList<>(live);
        liveRest.remove(Integer.valueOf(move.key));
        int to;
        if (move.to == 0) {
            to = rest.isEmpty() ? 0 : liveRest.indexOf(rest.get(0));
        } else {
            to = liveRest.indexOf(rest.get(move.to - 1)) + 1;
        }
        return new BankSortPlanner.Move(move.key, live.indexOf(move.key), to);
    }

    private static List<Integer> ids(List<BankSortItem> items) {
        List<Integer> ids = new ArrayList<>(items.size());
        for (BankSortItem item : items) {
            ids.add(item.getId());
        }
        return ids;
    }

    private static int compareSpecial(BankSortItem a, BankSortItem b) {
        String setA = a.getItemSetType();
        String setB = b.getItemSetType();
        if (setA != null || setB != null) {
            if (setA != null && setB == null) return -1;
            if (setB != null && setA == null) return 1;
            if (setA != null) {
                int setCmp = setA.compareTo(setB);
                if (setCmp != 0) return setCmp;
                List<String> order = BankClassifier.ITEM_SETS.get(setA);
                if (order != null) {
                    int ia = indexContains(order, a.getProcessedName());
                    int ib = indexContains(order, b.getProcessedName());
                    if (ia != ib) return Integer.compare(ia, ib);
                }
            }
        }
        if ("Runes".equals(a.getCategory()) && "Runes".equals(b.getCategory())) {
            int ia = BankClassifier.RUNE_TYPES_ORDER.indexOf(a.getProcessedName());
            int ib = BankClassifier.RUNE_TYPES_ORDER.indexOf(b.getProcessedName());
            if (ia != -1 || ib != -1) {
                if (ia == -1) return 1;
                if (ib == -1) return -1;
                if (ia != ib) return Integer.compare(ia, ib);
            }
        }
        Integer tierA = tierValue(a);
        Integer tierB = tierValue(b);
        if (tierA != null || tierB != null) {
            if (tierA != null && tierB == null) return -1;
            if (tierB != null && tierA == null) return 1;
            if (tierA != null) {
                int t = Integer.compare(tierB, tierA);
                if (t != 0) return t;
            }
        }
        int base = a.getBaseName().compareTo(b.getBaseName());
        if (base != 0) return base;
        return Integer.compare(b.getDoseOrCharge(), a.getDoseOrCharge());
    }

    private static int compareWithinCategory(BankSortItem a, BankSortItem b) {
        if (OrganizeOptions.current().tight()) {
            int family = Integer.compare(tightFamily(a), tightFamily(b));
            if (family != 0) {
                return family;
            }
            int metal = Integer.compare(tightMetal(b), tightMetal(a));
            if (metal != 0) {
                return metal;
            }
        }
        if (BankClassifier.isWorkflowCategory(a.getCategory())) {
            int level = Integer.compare(a.getItemLevel(), b.getItemLevel());
            if (level != 0) return level;
            int stage = Integer.compare(a.getWorkflowStage(), b.getWorkflowStage());
            if (stage != 0) return stage;
        }
        return compareSpecial(a, b);
    }

    private static int tightFamily(BankSortItem item) {
        String name = item.getBaseName();
        switch (item.getCategory()) {
            case "Weapon-Melee":
                return meleeFamily(name);
            case "Ammunition":
                return ammoFamily(name);
            case "Food-Cooked":
                return foodFamily(name);
            case "Farming-Seed":
                return seedFamily(name);
            case "Gathering-Bones":
                return boneFamily(name);
            case "Production-Other":
                return productionFamily(name);
            case "Miscellaneous":
                return junkFamily(name);
            default:
                return 0;
        }
    }

    private static int tightMetal(BankSortItem item) {
        if (!"Production-Other".equals(item.getCategory()) || !item.getBaseName().contains("nails")) {
            return 0;
        }
        String metal = BankClassifier.metalTierWord(item.getBaseName());
        if (metal == null) {
            return 0;
        }
        Integer value = BankClassifier.GEAR_TIERS.get(metal);
        return value == null ? 0 : value;
    }

    private static int meleeFamily(String name) {
        if (name.contains("dagger")) return 0;
        if (name.contains("scimitar") || name.contains("sword") || name.contains("longsword")) return 1;
        if (name.contains("mace")) return 2;
        if (name.contains("spear") || name.contains("hasta")) return 3;
        if (name.contains("warhammer") || name.contains("maul")) return 4;
        if (name.contains("claws")) return 5;
        if (name.contains("axe")) return 6;
        if (name.contains("whip")) return 7;
        if (name.contains("blackjack")) return 8;
        return 9;
    }

    private static int ammoFamily(String name) {
        if (name.contains("arrow")) return 0;
        if (name.contains("bolt")) return 1;
        if (name.contains("dart")) return 2;
        if (name.contains("javelin") || name.contains("throwing")) return 3;
        if (name.contains("knife")) return 4;
        return 5;
    }

    private static int foodFamily(String name) {
        if (isFishName(name)) return 0;
        if (name.contains("bread") || name.contains("pie") || name.contains("cake")
                || name.contains("pizza") || name.contains("stew") || name.contains("kebab")) {
            return 1;
        }
        if (name.contains("cabbage") || name.contains("onion") || name.contains("tomato")
                || name.contains("apple") || name.contains("banana") || name.contains("spinach")) {
            return 2;
        }
        return 3;
    }

    private static boolean isFishName(String name) {
        return name.contains("shrimp") || name.contains("anchov") || name.contains("herring")
                || name.contains("trout") || name.contains("salmon") || name.contains("tuna")
                || name.contains("lobster") || name.contains("swordfish") || name.contains("shark")
                || name.contains("bass") || name.contains("pike") || name.contains("karambwan")
                || name.contains("monkfish") || name.contains("angler") || name.contains("manta")
                || name.contains("turtle") || name.contains("cod") || name.contains("sardine")
                || name.contains("mackerel") || name.contains("eel");
    }

    private static int seedFamily(String name) {
        if (name.contains("mithril seed") || name.contains("crystal acorn")) return 5;
        if (containsHerbSeed(name)) return 1;
        if (name.contains("marigold") || name.contains("rosemary") || name.contains("nasturtium")
                || name.contains("woad") || name.contains("limpwurt")) {
            return 2;
        }
        if (name.contains("barley") || name.contains("hammerstone") || name.contains("asgarnian")
                || name.contains("jute") || name.contains("yanillian") || name.contains("krandorian")
                || name.contains("wildblood")) {
            return 3;
        }
        if (name.contains("acorn") || name.contains("tree") || name.contains("willow") || name.contains("maple")
                || name.contains("yew") || name.contains("magic seed") || name.contains("teak")
                || name.contains("mahogany") || name.contains("calquat") || name.contains("redwood")
                || name.contains("spirit")) {
            return 4;
        }
        if (name.contains("potato") || name.contains("onion") || name.contains("cabbage") || name.contains("tomato")
                || name.contains("sweetcorn") || name.contains("strawberry") || name.contains("watermelon")
                || name.contains("snape")) {
            return 0;
        }
        return 5;
    }

    private static boolean containsHerbSeed(String name) {
        return name.contains("guam") || name.contains("marrentill") || name.contains("tarromin")
                || name.contains("harralander") || name.contains("ranarr") || name.contains("toadflax")
                || name.contains("irit") || name.contains("avantoe") || name.contains("kwuarm")
                || name.contains("snapdragon") || name.contains("cadantine") || name.contains("lantadyme")
                || name.contains("dwarf weed") || name.contains("torstol");
    }

    private static int boneFamily(String name) {
        if (name.contains("ensouled")) return 3;
        if (name.contains("dragon") || name.contains("wyvern")) return 2;
        if (name.contains("big bone")) return 1;
        if (name.equals("bones")) return 0;
        return 4;
    }

    private static int productionFamily(String name) {
        if (name.contains("nails")) return 0;
        if (name.contains("bucket")) return 1;
        if (name.contains("feather")) return 2;
        return 3;
    }

    private static int junkFamily(String name) {
        if (name.contains("book") || name.contains("note") || name.contains("journal") || name.contains("diary")) {
            return 0;
        }
        return 1;
    }

    private static Integer tierValue(BankSortItem item) {
        if ("Tool-Mining".equals(item.getCategory())) {
            return BankClassifier.TOOL_LEVELS_PICKAXE.get(item.getBaseName());
        }
        if ("Tool-Woodcutting".equals(item.getCategory())) {
            return BankClassifier.TOOL_LEVELS_AXE.get(item.getBaseName());
        }
        if (item.getItemTier() != null) {
            return BankClassifier.GEAR_TIERS.get(item.getItemTier());
        }
        return null;
    }

    private static int indexContains(List<String> order, String name) {
        for (int i = 0; i < order.size(); i++) {
            if (name.contains(order.get(i))) {
                return i;
            }
        }
        return order.size();
    }
}
