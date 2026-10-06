package net.runelite.client.plugins.microbot.banksorter;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

    static boolean alreadyInOrder(List<BankSortItem> current, List<BankSortItem> sorted) {
        if (current.size() != sorted.size()) {
            return false;
        }
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i).getId() != sorted.get(i).getId()) {
                return false;
            }
        }
        return true;
    }

    static boolean rearrange(List<BankSortItem> sortedItems, List<Integer> targetSlots, BankSorterScript script) {
        if (sortedItems.isEmpty() || sortedItems.size() != targetSlots.size()) {
            return true;
        }
        for (int i = 0; i < sortedItems.size(); i++) {
            if (script.isStopRequested()) {
                return false;
            }
            if (!Rs2Bank.isOpen()) {
                return false;
            }
            BankSortItem desired = sortedItems.get(i);
            int targetSlot = targetSlots.get(i);
            if (itemIdAtSlot(targetSlot) == desired.getId()) {
                continue;
            }
            if (!moveDesiredToSlot(desired, targetSlot, sortedItems, targetSlots, i)) {
                script.recordFailure();
            } else {
                script.recordMove();
            }
            script.pauseAfterMove();
        }
        return true;
    }

    private static boolean moveDesiredToSlot(BankSortItem desired, int targetSlot,
                                             List<BankSortItem> sortedItems, List<Integer> targetSlots, int relative) {
        for (int attempt = 0; attempt < 2; attempt++) {
            int sourceSlot = findSourceSlot(desired, sortedItems, targetSlots, relative);
            if (sourceSlot < 0) {
                continue;
            }
            if (sourceSlot == targetSlot) {
                return true;
            }
            if (!revealSlot(sourceSlot, desired.getId())) {
                continue;
            }
            Rectangle from = itemBounds(sourceSlot, desired.getId());
            Rectangle to = itemBounds(targetSlot, -1);
            if (from == null || to == null) {
                continue;
            }
            Microbot.drag(from, to);
            boolean ok = BankTabMover.waitUntil(() ->
                    itemIdAtSlot(targetSlot) == desired.getId(), 4000);
            if (ok) {
                return true;
            }
        }
        return false;
    }

    private static int findSourceSlot(BankSortItem desired, List<BankSortItem> sortedItems,
                                      List<Integer> targetSlots, int relative) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            int fallback = -1;
            for (net.runelite.api.widgets.Widget widget : Rs2Bank.getItems()) {
                if (widget.getItemId() != desired.getId() || widget.isHidden()) {
                    continue;
                }
                int slot = widget.getIndex();
                int rel = targetSlots.indexOf(slot);
                boolean alreadyCorrect = rel >= 0 && rel < relative
                        && sortedItems.get(rel).getId() == desired.getId();
                if (alreadyCorrect) {
                    continue;
                }
                if (slot == desired.getOriginalIndex()) {
                    return slot;
                }
                if (fallback < 0) {
                    fallback = slot;
                }
            }
            return fallback;
        }).orElse(-1);
    }

    private static int itemIdAtSlot(int slot) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            net.runelite.api.widgets.Widget item = Rs2Bank.getItemWidget(slot);
            return item == null || item.isHidden() ? -1 : item.getItemId();
        }).orElse(-1);
    }

    private static Rectangle itemBounds(int slot, int expectedItemId) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            net.runelite.api.widgets.Widget item = Rs2Bank.getItemWidget(slot);
            if (item == null || item.isHidden()
                    || (expectedItemId >= 0 && item.getItemId() != expectedItemId)) {
                return null;
            }
            Rectangle bounds = item.getBounds();
            return bounds == null ? null : new Rectangle(bounds);
        }).orElse(null);
    }

    private static boolean revealSlot(int slot, int itemId) {
        Rectangle bounds = itemBounds(slot, itemId);
        if (bounds != null && Rs2UiHelper.isRectangleWithinCanvas(bounds)) {
            return true;
        }
        if (!scrollToSlot(slot)) {
            return false;
        }
        if (!OrganizeOptions.current().fast()) {
            Global.sleep(80);
        }
        return true;
    }

    private static boolean scrollToSlot(int slot) {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Rs2Bank.scrollBankToSlot(slot)).orElse(false);
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
