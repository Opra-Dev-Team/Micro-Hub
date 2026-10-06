package net.runelite.client.plugins.microbot.banksorter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankClassifierTest {
    private int nextId = 1;

    @AfterEach
    void resetOptions() {
        OrganizeOptions.reset();
    }

    @Test
    void routesRepresentativeItemsToEightTabLayout() {
        assertHome("Shark", BankHomeTab.SUPPLIES);
        assertHome("Super restore(4)", BankHomeTab.SUPPLIES);
        assertHome("Raw shark", BankHomeTab.GATHERING);
        assertHome("Raw beef", BankHomeTab.GATHERING);
        assertHome("Abyssal whip", BankHomeTab.COMBAT);
        assertHome("Iron ore", BankHomeTab.GATHERING);
        assertHome("Gold bar", BankHomeTab.PRODUCTION);
        assertHome("Oak plank", BankHomeTab.PRODUCTION);
        assertHome("Amulet mould", BankHomeTab.PRODUCTION);
        assertHome("Needle", BankHomeTab.PRODUCTION);
        assertHome("Water rune", BankHomeTab.RUNES);
        assertHome("Ranarr weed", BankHomeTab.FARMING);
        assertHome("Ranarr seed", BankHomeTab.FARMING);
        assertHome("Limpwurt root", BankHomeTab.FARMING);
        assertHome("Clue scroll (hard)", BankHomeTab.MAIN);
        assertHome("Reward casket (hard)", BankHomeTab.MAIN);
        assertHome("Mark of grace", BankHomeTab.MAIN);
        assertHome("Spade", BankHomeTab.FARMING);
        assertHome("Bucket", BankHomeTab.PRODUCTION);
        assertHome("Bucket of water", BankHomeTab.PRODUCTION);
        assertHome("Burnt shark", BankHomeTab.JUNK);
        assertHome("Coins", BankHomeTab.MAIN);
        assertHome("Empty bucket", BankHomeTab.PRODUCTION);
    }

    @Test
    void routesBlueWizardSetToCombat() {
        assertHome("Blue wizard hat", BankHomeTab.COMBAT);
        assertHome("Blue wizard robe", BankHomeTab.COMBAT);
        assertHome("Blue skirt", BankHomeTab.COMBAT);
        assertHome("Black wizard hat", BankHomeTab.COMBAT);
        assertHome("Mystic hat", BankHomeTab.COMBAT);
        assertHome("Mystic robe top", BankHomeTab.COMBAT);
        assertHome("Ancestral hat", BankHomeTab.COMBAT);
        assertEquals("Weapon-Magic", item("Mystic staff").getCategory());
        assertHome("Mystic staff", BankHomeTab.COMBAT);
    }

    @Test
    void dumpReviewRoutesMisplacedItems() {
        assertHome("Herring", BankHomeTab.SUPPLIES);
        assertHome("Watering can(8)", BankHomeTab.FARMING);
        assertHome("Wizard's mind bomb", BankHomeTab.SUPPLIES);
        assertHome("Goblin symbol book", BankHomeTab.CLUES);
        assertHome("Plank", BankHomeTab.PRODUCTION);
        assertHome("Acorn", BankHomeTab.FARMING);
        assertHome("Mithril seeds", BankHomeTab.FARMING);
        assertHome("Ensouled dragon head", BankHomeTab.GATHERING);
        assertHome("Small pouch", BankHomeTab.RUNES);
        assertHome("Medium pouch", BankHomeTab.RUNES);
        assertHome("Open fish barrel", BankHomeTab.GATHERING);
        assertHome("Empty sack", BankHomeTab.FARMING);
        assertHome("Arrow shaft", BankHomeTab.PRODUCTION);
        assertHome("Ball of wool", BankHomeTab.PRODUCTION);
        assertHome("Bone key", BankHomeTab.JUNK);
        assertHome("Chef's hat", BankHomeTab.CLUES);
        assertHome("Beekeeper's hat", BankHomeTab.CLUES);
        assertHome("Dramen staff", BankHomeTab.MAIN);
        assertHome("Camo top", BankHomeTab.CLUES);
        assertHome("Priest gown", BankHomeTab.CLUES);
        assertHome("Pyromancer garb", BankHomeTab.CLUES);
        assertHome("Khazard armour", BankHomeTab.CLUES);
        assertHome("Warm gloves", BankHomeTab.CLUES);
        assertHome("Ring of Charos(a)", BankHomeTab.JUNK);
        assertHome("Darklight", BankHomeTab.COMBAT);
        assertHome("Excalibur", BankHomeTab.COMBAT);
        assertHome("Ogre bow", BankHomeTab.COMBAT);
        assertHome("Shrimps", BankHomeTab.SUPPLIES);
        assertHome("Tuna", BankHomeTab.SUPPLIES);
        assertHome("Cabbage", BankHomeTab.SUPPLIES);
        assertHome("Cabbage seed", BankHomeTab.FARMING);
        assertHome("Scroll box (easy)", BankHomeTab.MAIN);
        assertHome("Loop half of key", BankHomeTab.MAIN);
        assertHome("Shears", BankHomeTab.FARMING);
        assertHome("Box trap", BankHomeTab.PRODUCTION);
        assertHome("Blue wizard hat", BankHomeTab.COMBAT);
        assertHome("Onion seed", BankHomeTab.FARMING);
        assertHome("Tomato", BankHomeTab.JUNK);
    }

    @Test
    void doesNotStealToolsAndSecondariesAsFood() {
        assertHome("Wine of zamorak", BankHomeTab.FARMING);
        assertEquals("Farming-Secondary", item("Wine of zamorak").getCategory());
        assertHome("Cake tin", BankHomeTab.PRODUCTION);
        assertEquals("Tool-Cooking", item("Cake tin").getCategory());
        assertHome("Pie dish", BankHomeTab.PRODUCTION);
        assertHome("Imcando hammer", BankHomeTab.PRODUCTION);
        assertEquals("Tool-Smithing", item("Imcando hammer").getCategory());
        assertHome("Feather", BankHomeTab.PRODUCTION);
    }

    @Test
    void alreadyCorrectHomeTabsAreNotMisplaced() {
        BankSortItem shark = new BankSortItem(1, "Shark", 0, 1, 1);
        BankSortItem whip = new BankSortItem(2, "Abyssal whip", 20, 1, 2);
        BankSortItem coins = new BankSortItem(3, "Coins", 80, 100, 0);
        assertEquals(1, shark.getHomeTab().index);
        assertEquals(1, shark.getCurrentTab());
        assertEquals(2, whip.getHomeTab().index);
        assertEquals(2, whip.getCurrentTab());
        assertEquals(0, coins.getHomeTab().index);
        assertEquals(0, coins.getCurrentTab());
        long misplaced = Arrays.asList(shark, whip, coins).stream()
                .filter(item -> item.getCurrentTab() != item.getHomeTab().index)
                .count();
        assertEquals(0, misplaced);
    }

    @Test
    void usesCanonicalUnnotedNameForClassification() {
        BankSortItem notedOre = new BankSortItem(
                9999, "Note", 0, 100, 0, 440, "Iron ore");

        assertEquals(440, notedOre.getClassificationId());
        assertEquals("Gathering-Ore", notedOre.getCategory());
        assertEquals(BankHomeTab.GATHERING, notedOre.getHomeTab());
    }

    @Test
    void keepsHerbsAndSeedsInSeparateContiguousBlocks() {
        List<BankSortItem> sorted = BankInTabSorter.sortItems(Arrays.asList(
                item("Ranarr seed"),
                item("Grimy ranarr weed"),
                item("Guam seed"),
                item("Ranarr weed"),
                item("Grimy guam leaf"),
                item("Guam leaf")
        ));

        assertEquals(Arrays.asList(
                "grimy guam leaf", "guam leaf", "grimy ranarr weed",
                "ranarr weed", "guam seed", "ranarr seed"
        ), names(sorted));
        assertEquals(Arrays.asList(
                "Farming-Herb", "Farming-Herb", "Farming-Herb",
                "Farming-Herb", "Farming-Seed", "Farming-Seed"
        ), categories(sorted));
    }

    @Test
    void sortsPotionDosesHighToLowWithoutMixingFood() {
        List<BankSortItem> sorted = BankInTabSorter.sortItems(Arrays.asList(
                item("Shark"),
                item("Prayer potion(1)"),
                item("Prayer potion(4)"),
                item("Prayer potion(2)")
        ));

        assertEquals(Arrays.asList(
                "prayer potion(4)", "prayer potion(2)", "prayer potion(1)", "shark"
        ), names(sorted));
    }

    @Test
    void keepsProductionSkillsAndMaterialsInStableBlocks() {
        List<BankSortItem> sorted = BankInTabSorter.sortItems(Arrays.asList(
                item("Oak plank"),
                item("Gold bar"),
                item("Hammer"),
                item("Amulet mould"),
                item("Iron bar"),
                item("Needle")
        ));

        assertEquals(Arrays.asList(
                "needle", "hammer", "amulet mould", "iron bar", "gold bar", "oak plank"
        ), names(sorted));
        assertEquals(Arrays.asList(
                "Tool-Crafting", "Tool-Smithing", "Production-Mould",
                "Production-Bar", "Production-Bar", "Production-Plank"
        ), categories(sorted));
    }

    @Test
    void alreadyInOrderSkipsRearrange() {
        List<BankSortItem> items = Arrays.asList(item("Needle"), item("Hammer"));
        List<BankSortItem> sorted = BankInTabSorter.sortItems(items);
        assertTrue(BankInTabSorter.alreadyInOrder(sorted, BankInTabSorter.sortItems(sorted)));
    }

    @Test
    void clueToggleKeepsTheSetTogether() {
        assertHome("Scroll box (easy)", BankHomeTab.MAIN);
        assertHome("Clue hunter boots", BankHomeTab.MAIN);
        assertHome("Casket", BankHomeTab.MAIN);

        OrganizeOptions.use(OrganizeOptions.defaults().withClues(OrganizeOptions.ClueHome.CLUES));
        assertHome("Clue scroll (easy)", BankHomeTab.CLUES);
        assertHome("Scroll box (easy)", BankHomeTab.CLUES);
        assertHome("Reward casket (hard)", BankHomeTab.CLUES);
        assertHome("Casket", BankHomeTab.CLUES);
        assertHome("Loop half of key", BankHomeTab.CLUES);
        assertHome("Clue hunter boots", BankHomeTab.CLUES);
        assertHome("Chronicle", BankHomeTab.SUPPLIES);
    }

    @Test
    void teleportJewelleryFollowsTheToggle() {
        assertEquals("Teleportation", item("Necklace of passage(4)").getCategory());
        assertHome("Explorer's ring 1", BankHomeTab.SUPPLIES);
        assertHome("Lyre", BankHomeTab.SUPPLIES);
        assertHome("Chronicle", BankHomeTab.SUPPLIES);

        OrganizeOptions.use(OrganizeOptions.defaults().withJewellery(OrganizeOptions.JewelleryHome.COMBAT));
        assertEquals("Armour-Amulet", item("Necklace of passage(4)").getCategory());
        assertEquals("Armour-Ring", item("Explorer's ring 1").getCategory());
        assertEquals("Armour-Ring", item("Ring of dueling(8)").getCategory());
        assertHome("Lyre", BankHomeTab.COMBAT);
        assertEquals("Teleportation", item("Chronicle").getCategory());
    }

    @Test
    void drinksCanSitInTheirOwnBlock() {
        assertEquals("Food-Cooked", item("Beer").getCategory());
        assertEquals("Food-Cooked", item("Cup of tea").getCategory());
        assertEquals("Farming-Secondary", item("Wine of zamorak").getCategory());

        OrganizeOptions.use(OrganizeOptions.defaults().withDrinks(OrganizeOptions.Drinks.OWN_BLOCK));
        List<BankSortItem> sorted = BankInTabSorter.sortItems(Arrays.asList(
                item("Beer"),
                item("Shark"),
                item("Wizard's mind bomb")
        ));
        assertEquals(Arrays.asList("shark", "beer", "wizard's mind bomb"), names(sorted));
        assertEquals(Arrays.asList("Food-Cooked", "Drinks", "Drinks"), categories(sorted));
    }

    @Test
    void tightLayoutPullsKnownLeftovers() {
        OrganizeOptions.use(OrganizeOptions.defaults().withLayout(OrganizeOptions.Layout.TIGHT));
        assertEquals("Food-Cooked", item("Tomato").getCategory());
        assertHome("Banana", BankHomeTab.SUPPLIES);
        assertHome("Triangle sandwich", BankHomeTab.SUPPLIES);
        assertEquals("Farming-Potion", item("Compost potion(4)").getCategory());
        assertEquals("Farming-Potion", item("Tarromin potion (unf)").getCategory());
        assertEquals("Armour-Body", item("Goblin mail").getCategory());
        assertEquals("Combat-Misc", item("Looting bag").getCategory());
        assertEquals("Production-Other", item("Black dye").getCategory());
        assertHome("Mark of grace", BankHomeTab.PRODUCTION);
        assertHome("Sawmill coupon (oak plank)", BankHomeTab.PRODUCTION);
        assertHome("Dramen staff", BankHomeTab.MAIN);
    }

    @Test
    void gearWordsDoNotPromoteSeedsOrIronmanHelms() {
        assertEquals(null, item("Mithril seeds").getItemTier());
        assertEquals(null, item("Group ironman helm").getItemTier());
        assertEquals("rune", item("Rune dagger").getItemTier());
        List<BankSortItem> sorted = BankInTabSorter.sortItems(Arrays.asList(
                item("Mithril seeds"),
                item("Guam seed"),
                item("Onion seed")
        ));
        assertEquals(Arrays.asList("onion seed", "guam seed", "mithril seeds"), names(sorted));
    }

    @Test
    void tightLayoutGroupsFamiliesInsideTheTab() {
        OrganizeOptions.use(OrganizeOptions.defaults().withLayout(OrganizeOptions.Layout.TIGHT));

        assertEquals(Arrays.asList(
                "rune dagger", "bronze dagger", "mithril spear", "adamant warhammer"
        ), names(BankInTabSorter.sortItems(Arrays.asList(
                item("Adamant warhammer"),
                item("Bronze dagger"),
                item("Mithril spear"),
                item("Rune dagger")
        ))));

        assertEquals(Arrays.asList(
                "rune arrow", "steel arrow", "bone bolts", "bronze dart"
        ), names(BankInTabSorter.sortItems(Arrays.asList(
                item("Bronze dart"),
                item("Bone bolts"),
                item("Steel arrow"),
                item("Rune arrow")
        ))));

        assertEquals(Arrays.asList(
                "shark", "shrimps", "bread", "pie", "cabbage", "tomato"
        ), names(BankInTabSorter.sortItems(Arrays.asList(
                item("Cabbage"),
                item("Pie"),
                item("Tomato"),
                item("Shrimps"),
                item("Bread"),
                item("Shark")
        ))));

        assertEquals(Arrays.asList(
                "onion seed", "guam seed", "ranarr seed", "hammerstone seed", "acorn", "mithril seeds"
        ), names(BankInTabSorter.sortItems(Arrays.asList(
                item("Mithril seeds"),
                item("Acorn"),
                item("Ranarr seed"),
                item("Hammerstone seed"),
                item("Guam seed"),
                item("Onion seed")
        ))));

        assertEquals(Arrays.asList(
                "bones", "big bones", "baby dragon bone", "dragon bones", "ensouled dragon head", "ashes"
        ), names(BankInTabSorter.sortItems(Arrays.asList(
                item("Ashes"),
                item("Ensouled dragon head"),
                item("Dragon bones"),
                item("Baby dragon bone"),
                item("Big bones"),
                item("Bones")
        ))));

        assertEquals(Arrays.asList(
                "mithril nails", "bronze nails", "bucket", "feather", "rope"
        ), names(BankInTabSorter.sortItems(Arrays.asList(
                item("Rope"),
                item("Feather"),
                item("Bronze nails"),
                item("Bucket"),
                item("Mithril nails")
        ))));

        assertEquals(Arrays.asList("diary", "journal", "flyer"), names(BankInTabSorter.sortItems(Arrays.asList(
                item("Flyer"),
                item("Journal"),
                item("Diary")
        ))));
    }

    @Test
    void falseFoodAndDrinkMatchesStayOutOfSupplies() {
        assertEquals("Miscellaneous", item("Shark paint").getCategory());
        assertEquals("Production-Glass", item("Beer glass").getCategory());
        assertEquals("Farming-Secondary", item("Blue dragon scale").getCategory());
        assertEquals("Food-Cooked", item("Nettle tea").getCategory());
        assertHome("Herring", BankHomeTab.SUPPLIES);
        assertHome("Shrimps", BankHomeTab.SUPPLIES);
        assertHome("Cup of tea", BankHomeTab.SUPPLIES);
    }

    @Test
    void uncookedFishGoesToGatheringUnlessPlacedWithFood() {
        assertHome("Mackerel", BankHomeTab.GATHERING);
        assertHome("Cod", BankHomeTab.GATHERING);
        assertHome("Giant crab meat", BankHomeTab.GATHERING);
        assertHome("Raw shark", BankHomeTab.GATHERING);

        OrganizeOptions.use(OrganizeOptions.defaults().withRawFish(OrganizeOptions.RawFishHome.WITH_FOOD));
        assertEquals("Food-Cooked", item("Mackerel").getCategory());
        assertHome("Raw shark", BankHomeTab.SUPPLIES);
        assertHome("Cod", BankHomeTab.SUPPLIES);
    }

    @Test
    void leftoversLeaveJunkWhenEnabled() {
        assertEquals("Farming-Seed", item("Mushroom spore").getCategory());
        assertHome("Maple bird house", BankHomeTab.FARMING);
        assertHome("Nettles", BankHomeTab.FARMING);
        assertEquals("Production-Glass", item("Unpowered orb").getCategory());
        assertEquals("Supplies-Other", item("Waterskin(4)").getCategory());
        assertHome("Waterskin(4)", BankHomeTab.SUPPLIES);

        OrganizeOptions.use(OrganizeOptions.defaults().withLeftovers(false));
        assertHome("Mushroom spore", BankHomeTab.JUNK);
        assertHome("Maple bird house", BankHomeTab.JUNK);
        assertHome("Nettles", BankHomeTab.JUNK);
        assertHome("Unpowered orb", BankHomeTab.JUNK);
        assertHome("Waterskin(4)", BankHomeTab.JUNK);
    }

    @Test
    void metalsSortAheadOfUntieredArmour() {
        List<BankSortItem> sorted = BankInTabSorter.sortItems(Arrays.asList(
                item("Coif"),
                item("Steel med helm"),
                item("Black med helm")
        ));
        assertEquals(Arrays.asList("black med helm", "steel med helm", "coif"), names(sorted));
    }

    private void assertHome(String name, BankHomeTab expected) {
        BankSortItem it = item(name);
        assertEquals(expected, it.getHomeTab(), name + " cat=" + it.getCategory());
    }

    private BankSortItem item(String name) {
        return new BankSortItem(nextId++, name, nextId, 1, 0);
    }

    private static List<String> names(List<BankSortItem> items) {
        return items.stream().map(BankSortItem::getProcessedName).collect(Collectors.toList());
    }

    private static List<String> categories(List<BankSortItem> items) {
        return items.stream().map(BankSortItem::getCategory).collect(Collectors.toList());
    }
}
