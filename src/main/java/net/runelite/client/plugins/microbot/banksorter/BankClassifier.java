package net.runelite.client.plugins.microbot.banksorter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BankClassifier {
    static final Map<String, Integer> TOOL_LEVELS_PICKAXE = new LinkedHashMap<>();
    static final Map<String, Integer> TOOL_LEVELS_AXE = new LinkedHashMap<>();
    static final Map<String, Integer> GEAR_TIERS = new LinkedHashMap<>();
    static final Map<String, Integer> GEM_LEVELS = new HashMap<>();
    static final Map<String, Integer> LOG_LEVELS = new HashMap<>();
    static final Map<String, Integer> ORE_LEVELS = new HashMap<>();
    static final Map<String, Integer> BAR_LEVELS = new HashMap<>();
    static final Map<String, Integer> HERB_LEVELS = new HashMap<>();
    static final Map<String, Integer> SEED_LEVELS_FARMING = new HashMap<>();
    static final Map<String, List<String>> ITEM_SETS = new LinkedHashMap<>();
    static final List<String> RUNE_TYPES_ORDER = Arrays.asList(
            "air rune", "mind rune", "water rune", "earth rune", "fire rune", "body rune",
            "cosmic rune", "chaos rune", "astral rune", "nature rune", "law rune", "death rune",
            "blood rune", "soul rune", "wrath rune"
    );
    static final List<String> CATEGORY_ORDER = Arrays.asList(
            "Currency", "Main-Extra",
            "Teleportation", "Potions", "Food-Cooked", "Drinks",
            "Armour-Set-Graceful", "Armour-Set-Void", "Armour-Set-Barrows", "Armour-Set-Magic",
            "Weapon-Melee", "Weapon-Ranged", "Weapon-Magic",
            "Armour-Helmet", "Armour-Cape", "Armour-Amulet", "Armour-Body", "Armour-Legs",
            "Armour-Shield", "Armour-Gloves", "Armour-Boots", "Armour-Ring",
            "Ammunition", "Combat-Misc",
            "Gathering-Raw-Fish", "Gathering-Raw-Meat", "Gathering-Ore", "Gathering-Log",
            "Gathering-Uncut-Gem", "Gathering-Bones", "Gathering-Hides", "Gathering-Other",
            "Tool-Mining", "Tool-Woodcutting", "Tool-Fishing", "Tool-Crafting", "Tool-Cooking",
            "Tool-Smithing", "Tool-Fletching", "Tool-Construction", "Tool-Other",
            "Production-Mould", "Production-Bar", "Production-Plank", "Production-Leather",
            "Production-Glass", "Production-Textile", "Production-Cut-Gem", "Production-Jewellery",
            "Production-Other",
            "Runes", "Magic-Pouch", "Magic-Essence", "Magic-Tablet", "Magic-Other",
            "Farming-Herb", "Farming-Seed", "Farming-Sapling", "Farming-Secondary", "Farming-Potion", "Farming-Supply",
            "Clue-Scrolls", "Clue-Tools", "Unique", "Cosmetic/Holiday",
            "Quest-Items", "Miscellaneous"
    );

    private static final String[] FOOD = {
            "shark", "monkfish", "karambwan", "anglerfish", "manta ray", "tuna potato", "dark crab",
            "sea turtle", "lobster", "swordfish", "bass", "salmon", "trout", "cake", "pie", "stew",
            "pizza", "bread", "wine", "beer", "jug of wine",
            "eclipse red", "purple sweets", "summer pie", "wild pie", "botanical pie", "mushroom potato",
            "cooked karambwan", "blighted", "potato with", "cooked meat", "cooked chicken", "cooked rabbit",
            "cooked oomlie", "cooked crab", "cooked jubbly", "cooked chompy",
            "herring", "shrimp", "anchovies", "pike", "tuna", "kebab", "spinach roll", "cheese",
            "wizard's mind bomb"
    };
    private static final String[] POTION_TERMS = {
            "potion", "brew", "restore", "serum", "remedy", "antipoison", "antidote",
            "antifire", "anti-venom", "stamina", "energy", "prayer", "super attack",
            "super strength", "super defence", "super combat", "ranging mix", "magic mix",
            "bastion", "battlemage", "guthix rest", "combat mix", "agility mix",
            "hunter mix", "fishing mix", "zamorak mix"
    };
    private static final String[] RAW_MEATS = {
            "raw beef", "raw chicken", "raw rat meat", "raw bear meat", "raw rabbit",
            "raw bird meat", "raw yak meat", "raw oomlie", "raw chompy", "raw jubbly",
            "raw beast meat", "raw wolf meat", "raw snail", "raw crab meat"
    };
    private static final String[] FARMING_SECONDARIES = {
            "eye of newt", "limpwurt root", "snape grass", "white berries", "potato cactus",
            "red spiders' eggs", "mort myre fungus", "toad's legs", "goat horn dust",
            "crushed nest", "unicorn horn dust", "chocolate dust", "blue dragon scale",
            "wine of zamorak", "amylase crystal", "lava scale shard", "nail beast nails",
            "jangerberries", "cactus spine", "coconut milk", "vial of water",
            "garlic", "redberries", "cadava berries", "gnome spice"
            // swamp tar removed; now routes to Production-Other (Tab 4).
    };

    static {
        TOOL_LEVELS_PICKAXE.put("bronze pickaxe", 1);
        TOOL_LEVELS_PICKAXE.put("iron pickaxe", 1);
        TOOL_LEVELS_PICKAXE.put("steel pickaxe", 6);
        TOOL_LEVELS_PICKAXE.put("black pickaxe", 11);
        TOOL_LEVELS_PICKAXE.put("mithril pickaxe", 21);
        TOOL_LEVELS_PICKAXE.put("adamant pickaxe", 31);
        TOOL_LEVELS_PICKAXE.put("rune pickaxe", 41);
        TOOL_LEVELS_PICKAXE.put("dragon pickaxe", 61);
        TOOL_LEVELS_PICKAXE.put("infernal pickaxe", 61);
        TOOL_LEVELS_PICKAXE.put("crystal pickaxe", 71);
        TOOL_LEVELS_PICKAXE.put("3rd age pickaxe", 61);

        TOOL_LEVELS_AXE.put("bronze axe", 1);
        TOOL_LEVELS_AXE.put("iron axe", 1);
        TOOL_LEVELS_AXE.put("steel axe", 6);
        TOOL_LEVELS_AXE.put("black axe", 11);
        TOOL_LEVELS_AXE.put("mithril axe", 21);
        TOOL_LEVELS_AXE.put("adamant axe", 31);
        TOOL_LEVELS_AXE.put("rune axe", 41);
        TOOL_LEVELS_AXE.put("dragon axe", 61);
        TOOL_LEVELS_AXE.put("infernal axe", 61);
        TOOL_LEVELS_AXE.put("crystal axe", 71);
        TOOL_LEVELS_AXE.put("3rd age axe", 61);

        GEAR_TIERS.put("bronze", 1);
        GEAR_TIERS.put("iron", 10);
        GEAR_TIERS.put("steel", 20);
        GEAR_TIERS.put("black", 25);
        GEAR_TIERS.put("mithril", 30);
        GEAR_TIERS.put("adamant", 40);
        GEAR_TIERS.put("rune", 50);
        GEAR_TIERS.put("dragon", 60);
        GEAR_TIERS.put("barrows", 70);
        GEAR_TIERS.put("bandos", 75);
        GEAR_TIERS.put("armadyl", 75);
        GEAR_TIERS.put("ancestral", 75);
        GEAR_TIERS.put("justiciar", 75);
        GEAR_TIERS.put("inquisitor", 75);
        GEAR_TIERS.put("crystal armour", 75);
        GEAR_TIERS.put("torva", 80);
        GEAR_TIERS.put("masori", 80);
        GEAR_TIERS.put("virtus", 80);

        GEM_LEVELS.putAll(Map.of("uncut opal", 1, "opal", 1, "uncut jade", 13, "jade", 13, "uncut red topaz", 16, "red topaz", 16));
        GEM_LEVELS.putAll(Map.of("uncut sapphire", 20, "sapphire", 20, "uncut emerald", 27, "emerald", 27, "uncut ruby", 34, "ruby", 34));
        GEM_LEVELS.putAll(Map.of("uncut diamond", 43, "diamond", 43, "uncut dragonstone", 55, "dragonstone", 55));
        GEM_LEVELS.putAll(Map.of("uncut onyx", 67, "onyx", 67, "uncut zenyte", 89, "zenyte", 89));

        LOG_LEVELS.putAll(Map.of("logs", 1, "oak logs", 15, "willow logs", 30, "teak logs", 35, "arctic pine logs", 42));
        LOG_LEVELS.putAll(Map.of("maple logs", 45, "mahogany logs", 50, "yew logs", 60, "magic logs", 75, "redwood logs", 90));

        ORE_LEVELS.putAll(Map.of("copper ore", 1, "tin ore", 1, "iron ore", 15, "silver ore", 20, "coal", 30, "gold ore", 40));
        ORE_LEVELS.putAll(Map.of("mithril ore", 55, "adamantite ore", 70, "runite ore", 85, "amethyst", 92));

        BAR_LEVELS.putAll(Map.of("bronze bar", 1, "iron bar", 15, "steel bar", 30, "gold bar", 40));
        BAR_LEVELS.putAll(Map.of("mithril bar", 50, "adamantite bar", 70, "runite bar", 85));

        HERB_LEVELS.putAll(Map.of("grimy guam leaf", 3, "guam leaf", 3, "grimy marrentill", 5, "marrentill", 5, "grimy tarromin", 11, "tarromin", 11));
        HERB_LEVELS.putAll(Map.of("grimy harralander", 20, "harralander", 20, "grimy ranarr weed", 25, "ranarr weed", 25));
        HERB_LEVELS.putAll(Map.of("grimy toadflax", 30, "toadflax", 30, "grimy irit leaf", 40, "irit leaf", 40));
        HERB_LEVELS.putAll(Map.of("grimy avantoe", 48, "avantoe", 48, "grimy kwuarm", 54, "kwuarm", 54));
        HERB_LEVELS.putAll(Map.of("grimy snapdragon", 59, "snapdragon", 59, "grimy cadantine", 65, "cadantine", 65));
        HERB_LEVELS.putAll(Map.of("grimy lantadyme", 67, "lantadyme", 67, "grimy dwarf weed", 70, "dwarf weed", 70));
        HERB_LEVELS.putAll(Map.of("grimy torstol", 75, "torstol", 75));

        putLevels(SEED_LEVELS_FARMING,
                "potato seed", 1, "onion seed", 5, "cabbage seed", 7, "guam seed", 9,
                "tomato seed", 12, "marrentill seed", 14, "oak seed", 15, "tarromin seed", 19,
                "sweetcorn seed", 20, "harralander seed", 26, "apple tree seed", 27,
                "ranarr seed", 32, "willow seed", 30, "banana tree seed", 33,
                "toadflax seed", 38, "orange tree seed", 39, "curry tree seed", 42,
                "irit seed", 44, "maple seed", 45, "watermelon seed", 47,
                "avantoe seed", 50, "pineapple seed", 51, "kwuarm seed", 56,
                "papaya tree seed", 57, "yew seed", 60, "snapdragon seed", 62,
                "cadantine seed", 67, "palm tree seed", 68, "lantadyme seed", 73,
                "dwarf weed seed", 79, "magic seed", 75, "torstol seed", 85,
                "dragonfruit tree seed", 81, "redwood tree seed", 90);

        ITEM_SETS.put("graceful", Arrays.asList("graceful hood", "graceful cape", "graceful top", "graceful legs", "graceful gloves", "graceful boots"));
        ITEM_SETS.put("void", Arrays.asList("void knight helm", "void mage helm", "void ranger helm", "void melee helm", "void knight top", "elite void top", "void knight robe", "elite void robe", "void knight gloves"));
        ITEM_SETS.put("barrows-dharok", Arrays.asList("dharok's helm", "dharok's platebody", "dharok's platelegs", "dharok's greataxe"));
        ITEM_SETS.put("barrows-guthan", Arrays.asList("guthan's helm", "guthan's platebody", "guthan's chainskirt", "guthan's warspear"));
        ITEM_SETS.put("barrows-torag", Arrays.asList("torag's helm", "torag's platebody", "torag's platelegs", "torag's hammers"));
        ITEM_SETS.put("barrows-verac", Arrays.asList("verac's helm", "verac's brassard", "verac's plateskirt", "verac's flail"));
        ITEM_SETS.put("barrows-karil", Arrays.asList("karil's coif", "karil's leathertop", "karil's leatherskirt", "karil's crossbow"));
        ITEM_SETS.put("barrows-ahrim", Arrays.asList("ahrim's hood", "ahrim's robetop", "ahrim's robeskirt", "ahrim's staff"));
        ITEM_SETS.put("wizard-blue", Arrays.asList("blue wizard hat", "blue wizard robe", "blue skirt"));
        ITEM_SETS.put("wizard-black", Arrays.asList("black wizard hat", "black robe", "black skirt"));
        ITEM_SETS.put("mystic", Arrays.asList("mystic hat", "mystic robe top", "mystic robe bottom", "mystic gloves", "mystic boots"));
    }

    private BankClassifier() {
    }

    static BankHomeTab homeTab(BankSortItem item) {
        String cat = item.getCategory();

        if (cat.equals("Currency") || cat.equals("Main-Extra")) return BankHomeTab.MAIN;
        if (cat.equals("Teleportation") || cat.equals("Potions") || cat.equals("Food-Cooked") || cat.equals("Drinks")) {
            return BankHomeTab.SUPPLIES;
        }
        if (cat.startsWith("Weapon-") || cat.startsWith("Armour-") || cat.equals("Ammunition")
                || cat.equals("Combat-Misc")) {
            return BankHomeTab.COMBAT;
        }
        if (cat.startsWith("Gathering-")) {
            return BankHomeTab.GATHERING;
        }
        if (cat.startsWith("Tool-") || cat.startsWith("Production-")) {
            return BankHomeTab.PRODUCTION;
        }
        if (cat.equals("Runes") || cat.startsWith("Magic-")) {
            return BankHomeTab.RUNES;
        }
        if (cat.startsWith("Farming-")) {
            return BankHomeTab.FARMING;
        }
        if (cat.startsWith("Clue-") || cat.equals("Unique") || cat.equals("Cosmetic/Holiday")) {
            return BankHomeTab.CLUES;
        }
        return BankHomeTab.JUNK;
    }

    static boolean isWorkflowCategory(String category) {
        return category.equals("Potions") || category.equals("Farming-Herb")
                || category.equals("Farming-Seed") || category.equals("Farming-Sapling")
                || category.equals("Gathering-Uncut-Gem") || category.equals("Gathering-Ore")
                || category.equals("Gathering-Log") || category.equals("Production-Bar")
                || category.equals("Production-Plank") || category.equals("Production-Cut-Gem");
    }

    static String classifyItem(BankSortItem item) {
        OrganizeOptions options = OrganizeOptions.current();
        String name = item.getBaseName();
        String processedName = item.getProcessedName();

        if ("graceful".equals(item.getItemSetType())) return "Armour-Set-Graceful";
        if ("void".equals(item.getItemSetType())) return "Armour-Set-Void";
        if (item.getItemSetType() != null && item.getItemSetType().startsWith("barrows-")) return "Armour-Set-Barrows";
        if (item.getItemSetType() != null && (item.getItemSetType().startsWith("wizard")
                || "mystic".equals(item.getItemSetType())
                || "infinity".equals(item.getItemSetType())
                || "ghostly".equals(item.getItemSetType())
                || "ancestral".equals(item.getItemSetType())
                || item.getItemSetType().contains("bark"))) {
            return "Armour-Set-Magic";
        }
        String magicPiece = magicArmourPiece(name);
        if (magicPiece != null) {
            return magicPiece;
        }

        if (isNonCombatOutfit(name)) return "Cosmetic/Holiday";
        if (name.contains("magic gold feather") || name.equals("bone key")) {
            return "Quest-Items";
        }
        if (name.equals("acorn") || name.endsWith(" seeds")) return "Farming-Seed";
        if (isEssencePouch(name)) return "Magic-Pouch";
        if (name.contains("fish barrel")) return "Gathering-Other";
        if (name.equals("plank") || name.endsWith(" plank")) return "Production-Plank";
        if (name.contains("ball of wool")) return "Production-Textile";
        if (name.contains("arrow shaft") || name.contains("headless arrow")) return "Production-Other";
        if (isHunterTool(name)) return "Tool-Other";
        if (name.contains("watering can")) return "Farming-Supply";

        if (isCurrency(processedName, name)) return "Currency";
        if (options.tight() && isTightProductionExtra(name)) return "Production-Other";
        if (isPinnedMain(name, options)) return "Main-Extra";
        // Burnt food is junk, not supplies. This has to run before the cooked-food check.
        if (processedName.startsWith("burnt ")) return "Miscellaneous";
        if (isClueFamily(processedName)) {
            return options.cluesOnMain() ? "Main-Extra" : "Clue-Scrolls";
        }
        if (isHoliday(processedName)) return "Cosmetic/Holiday";
        if (isTeleport(processedName, options)) return "Teleportation";
        if (options.tight() && isHerblorePotion(processedName)) return "Farming-Potion";
        if (isDrink(processedName)) {
            return options.drinksWithFood() ? "Food-Cooked" : "Drinks";
        }
        if (isPotion(item)) return "Potions";
        if (isRawFish(name)) return "Gathering-Raw-Fish";
        if (containsAny(name, RAW_MEATS)) return "Gathering-Raw-Meat";
        if (name.contains("wine of zamorak")) return "Farming-Secondary";
        if (isFood(processedName, options)) return "Food-Cooked";
        if (name.equals("cake tin") || name.equals("pie dish")) return productionToolCategory(name);
        if (name.contains("imcando hammer") || name.equals("hammer")) return "Tool-Smithing";
        if (isProductionTool(name)) return productionToolCategory(name);
        if (processedName.endsWith(" rune")) return "Runes";
        if (name.contains("rune pouch") || name.contains("divine rune pouch") || isEssencePouch(name)) {
            return "Magic-Pouch";
        }
        if (name.contains("essence") && !name.contains("potion")) return "Magic-Essence";
        if (isMagicTablet(name)) return "Magic-Tablet";
        if (name.contains("talisman") || name.endsWith(" tiara") || name.contains("runecraft")) {
            return "Magic-Other";
        }

        // --- Tab 3: Gathering storage items ---
        if (name.contains("fish barrel") || name.equals("tackle box")) return "Gathering-Other";

        // --- Tab 6: Empty vials belong with herblore ---
        if (name.equals("vial") || (name.startsWith("vial") && name.contains("empty"))
                || processedName.equals("vial")) {
            return "Farming-Secondary";
        }

        // --- Tab 4 specific overrides ---
        // Barbarian rod doesn't contain "fishing rod" so we catch it explicitly.
        if (name.equals("barbarian rod")) return "Tool-Fishing";
        // Bird snare is a hunter tool; goes with production tools.
        if (name.equals("bird snare") || isHunterTool(name)) return "Tool-Other";
        // Buckets (all variants) are general-purpose production containers.
        if (isBucket(name)) return "Production-Other";
        // Rope, swamp paste, and pot of flour are production/processing materials.
        if (name.equals("rope")) return "Production-Other";
        if (name.contains("swamp paste")) return "Production-Other";
        if (name.equals("pot of flour") || processedName.equals("pot of flour")) return "Production-Other";
        // Silk is a textile; lives with flax/wool in Production-Textile.
        if (name.equals("silk")) return "Production-Textile";

        if (isCraftingJewellery(name)) return "Production-Jewellery";
        if (name.contains("arrow shaft") || name.contains("headless arrow")) return "Production-Other";
        if (name.contains("dart") || name.contains("arrow") || name.contains("bolts") || name.contains("javelin")
                || name.contains("throwing axe") || name.contains("knife") || name.contains("chinchompa")
                || name.endsWith(" shot") || name.endsWith(" shell")) {
            return "Ammunition";
        }
        if (name.contains("pickaxe")) return "Tool-Mining";
        if (name.contains(" axe") && !name.contains("battleaxe") && !name.contains("greataxe") && !name.contains("throwing axe")) {
            return "Tool-Woodcutting";
        }
        if (name.contains("harpoon") || name.contains("fishing rod") || name.contains("lobster pot")
                || name.contains("fishing net") || name.contains("fishing bait")) {
            return "Tool-Fishing";
        }
        if (name.contains("dramen staff")) return "Main-Extra";
        if (name.contains("staff") || name.contains("wand") || name.contains("trident") || name.contains("sceptre")
                || name.contains("crozier") || name.contains("book of") || name.contains("ancient sceptre")) {
            return "Weapon-Magic";
        }
        if (name.contains("shortbow") || name.contains("longbow") || name.contains("comp bow") || name.contains("crossbow")
                || name.contains("c'bow") || name.contains("ballista") || name.contains("blowpipe")
                || name.contains("crystal bow") || name.contains("dark bow") || name.contains("ogre bow")) {
            return "Weapon-Ranged";
        }
        if (name.contains("scimitar") || name.contains("sword") || name.contains("longsword") || name.contains("dagger")
                || name.contains("mace") || name.contains("warhammer") || name.contains("battleaxe") || name.contains("halberd")
                || name.contains("spear") || name.contains("hasta") || name.contains("whip") || name.contains("bludgeon")
                || name.contains("rapier") || name.contains("greataxe") || name.contains("greatsword") || name.contains("maul")
                || name.contains("flail") || name.contains("claws") || name.contains("blackjack")
                || name.equals("darklight") || name.equals("excalibur") || name.equals("wolfbane")
                || name.contains("silver sickle")) {
            return "Weapon-Melee";
        }
        if (name.contains("expeditious bracelet") || name.contains("bracelet of slaughter")) {
            return "Armour-Gloves";
        }
        if (!options.jewelleryOnSupplies() && isTeleportJewellery(name)) {
            if (name.contains("ring")) return "Armour-Ring";
            return "Armour-Amulet";
        }
        if (name.contains("varrock armour") || name.contains("kandarin headgear")
                || name.contains("morytania legs") || name.contains("desert amulet")
                || name.contains("fremennik sea boots") || name.contains("karamja gloves")
                || name.contains("wilderness sword")) {
            if (name.contains("headgear") || name.contains("helm")) return "Armour-Helmet";
            if (name.contains("legs") || name.contains("boots") || name.contains("gloves")) {
                return name.contains("legs") ? "Armour-Legs" : (name.contains("boots") ? "Armour-Boots" : "Armour-Gloves");
            }
            if (name.contains("sword")) return "Weapon-Melee";
            if (name.contains("amulet")) return "Armour-Amulet";
            return "Armour-Body";
        }
        if (name.contains("helm") || name.contains("coif") || name.contains("hood") || name.contains("mask")
                || name.contains("circlet") || name.contains("sallet") || name.endsWith(" hat")) {
            return "Armour-Helmet";
        }
        if (name.contains("cape") || name.contains("cloak") || name.contains("avas accumulator") || name.contains("avas assembler")) {
            return "Armour-Cape";
        }
        if ((name.contains("amulet") || name.contains("necklace") || name.contains("symbol") || name.contains("stole"))
                && !name.contains("book") && !name.contains("unstrung")) {
            return "Armour-Amulet";
        }
        if (name.contains("platebody") || name.contains("chainbody") || (name.contains("body") && !name.contains("body rune"))
                || name.contains("chestplate") || name.contains("hauberk") || name.contains("robe top")
                || name.contains("d'hide body") || name.contains("leather body")
                || (name.contains("robe") && !name.contains("wardrobe"))) {
            return "Armour-Body";
        }
        if (name.contains("platelegs") || name.contains("plateskirt") || name.contains("chaps") || name.contains("tassets")
                || name.contains("robe bottom") || name.contains("d'hide chaps")
                || name.endsWith(" skirt")) {
            return "Armour-Legs";
        }
        if (name.contains("kiteshield") || name.contains("sq shield") || (name.contains("shield") && !name.contains("dragonfire ward"))
                || name.contains("defender") || name.contains("toktz-ket-xil") || (name.contains("ward") && !name.contains("dwarf weed"))) {
            return "Armour-Shield";
        }
        if (name.contains("gloves") || name.contains("gauntlets") || name.contains("vambraces") || name.contains("bracers")) {
            return "Armour-Gloves";
        }
        if (name.contains("boots")) return "Armour-Boots";
        if (name.contains("ring") && !name.contains("slayer") && !name.contains("ring of dueling")
                && !name.contains("herring") && !name.contains("watering") && !name.contains("charos")) {
            return "Armour-Ring";
        }
        if (name.contains("ring of charos") || name.contains("charos")) return "Quest-Items";

        if (name.contains("grimy") || HERB_LEVELS.containsKey(processedName)) return "Farming-Herb";
        if (name.endsWith(" sapling")) return "Farming-Sapling";
        if (SEED_LEVELS_FARMING.containsKey(processedName) || name.endsWith(" seed")
                || name.endsWith(" seeds") || name.equals("acorn")) {
            return "Farming-Seed";
        }
        if (containsAny(name, FARMING_SECONDARIES)) return "Farming-Secondary";
        if (isFarmingSupply(name)) return "Farming-Supply";

        if (name.contains("uncut") || GEM_LEVELS.containsKey(processedName) && processedName.startsWith("uncut")) {
            return "Gathering-Uncut-Gem";
        }
        if (LOG_LEVELS.containsKey(processedName) || name.endsWith(" logs")) return "Gathering-Log";
        if (ORE_LEVELS.containsKey(processedName) || name.endsWith(" ore") || name.equals("coal")) return "Gathering-Ore";
        if (name.equals("bone key")) return "Quest-Items";
        if (name.contains("bone") || name.contains("ashes")
                || (name.contains("ensouled") && name.contains("head"))) {
            return "Gathering-Bones";
        }
        if (name.contains("ball of wool")) return "Production-Textile";
        if (name.contains("hide") || name.contains("fleece") || (name.contains("wool") && !name.contains("ball of wool"))) {
            return "Gathering-Hides";
        }
        if (isGatheredMaterial(name)) return "Gathering-Other";

        if (name.endsWith(" mould") || name.endsWith(" mold")) return "Production-Mould";
        if (BAR_LEVELS.containsKey(processedName) || name.endsWith(" bar")) return "Production-Bar";
        if (name.equals("plank") || name.endsWith(" plank")) return "Production-Plank";
        if (name.contains("leather") || name.contains("dragonhide")) return "Production-Leather";
        if (name.contains("molten glass") || name.contains("glassblowing") || name.contains("soda ash")
                || name.contains("seaweed")) return "Production-Glass";
        // Buckets of sand go to Production-Other (general bucket rule above handles all buckets).
        if (name.contains("flax") || name.contains("bow string") || name.contains("ball of wool")
                || name.contains("linen") || name.equals("silk")) return "Production-Textile";
        // Swamp tar and processing materials
        if (name.contains("swamp tar")) return "Production-Other";
        if (GEM_LEVELS.containsKey(processedName)) return "Production-Cut-Gem";
        if (isProductionMaterial(name)) return "Production-Other";

        if (name.contains("sacred oil")) return "Potions";
        if (name.equals("enchanted gem")) return "Unique";
        if (name.equals("nuggets") || name.contains("gold nugget")) return "Gathering-Other";
        if (name.contains("construction guide")) return "Tool-Construction";
        if (name.equals("cocktail shaker")) return "Tool-Cooking";
        if (name.equals("crowbar")) return "Tool-Other";
        if (name.equals("druid pouch")) return "Farming-Supply";
        if (name.contains("red vine worm")) return "Tool-Fishing";
        if (isUnique(name)) return "Unique";
        if (options.tight() && name.equals("goblin mail")) return "Armour-Body";
        if (options.tight() && name.contains("looting bag")) return "Combat-Misc";
        if (options.tight() && isDye(name)) return "Production-Other";
        if (name.contains("quest") || (name.contains("key") && !name.contains("crystal key"))) return "Quest-Items";
        return "Miscellaneous";
    }

    static String determineItemSetType(String baseName) {
        if (baseName.contains("graceful")) return "graceful";
        if (baseName.contains("void knight") || baseName.contains("void mage") || baseName.contains("void ranger")
                || baseName.contains("void melee") || baseName.contains("elite void")) {
            return "void";
        }
        if (baseName.contains("dharok")) return "barrows-dharok";
        if (baseName.contains("guthan")) return "barrows-guthan";
        if (baseName.contains("torag")) return "barrows-torag";
        if (baseName.contains("verac")) return "barrows-verac";
        if (baseName.contains("karil")) return "barrows-karil";
        if (baseName.contains("ahrim")) return "barrows-ahrim";
        if (baseName.contains("mind bomb")) {
            return null;
        }
        if (baseName.equals("blue skirt") || baseName.contains("blue wizard")) return "wizard-blue";
        if (baseName.equals("black skirt") || baseName.contains("black wizard") || baseName.equals("black robe")) {
            return "wizard-black";
        }
        if (baseName.equals("pink skirt") || (baseName.contains("wizard")
                && (baseName.contains("hat") || baseName.contains("robe") || baseName.contains("skirt")))) {
            return "wizard";
        }
        if (baseName.contains("mystic") && !baseName.contains("staff")) return "mystic";
        if (baseName.contains("infinity")) return "infinity";
        if (baseName.contains("ghostly")) return "ghostly";
        if (baseName.contains("ancestral")) return "ancestral";
        if (baseName.contains("splitbark")) return "splitbark";
        if (baseName.contains("swampbark")) return "swampbark";
        if (baseName.contains("bloodbark")) return "bloodbark";
        return null;
    }

    static String metalTierWord(String baseName) {
        for (Map.Entry<String, Integer> entry : GEAR_TIERS.entrySet()) {
            if (containsWord(baseName, entry.getKey())) {
                return entry.getKey();
            }
        }
        return null;
    }

    static String determineItemTier(String baseName) {
        if (!acceptsGearTier(baseName)) {
            return null;
        }
        String metal = metalTierWord(baseName);
        if (metal != null) {
            return metal;
        }
        if (baseName.contains("crystal pickaxe") || baseName.contains("crystal axe") || baseName.contains("crystal bow")) {
            return "crystal";
        }
        if (containsWord(baseName, "3rd age")) {
            return "3rd age";
        }
        return null;
    }

    static int[] getWorkflowData(BankSortItem item) {
        int level = 0;
        int stage = 99;
        switch (item.getCategory()) {
            case "Farming-Herb":
                level = HERB_LEVELS.getOrDefault(item.getProcessedName(), 0);
                stage = item.getProcessedName().startsWith("grimy") ? 0 : 1;
                break;
            case "Potions":
                level = HERB_LEVELS.entrySet().stream()
                        .filter(entry -> !entry.getKey().startsWith("grimy"))
                        .filter(entry -> {
                            String herbName = entry.getKey().replace(" leaf", "").replace(" weed", "");
                            return item.getBaseName().contains(herbName);
                        })
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElse(0);
                stage = item.getBaseName().contains("(unf)") ? 2 : 4;
                break;
            case "Gathering-Uncut-Gem":
            case "Production-Cut-Gem":
                level = GEM_LEVELS.getOrDefault(item.getProcessedName(), 0);
                stage = item.getProcessedName().startsWith("uncut") ? 0 : 1;
                break;
            case "Gathering-Ore":
                level = ORE_LEVELS.getOrDefault(item.getProcessedName(), 0);
                stage = 0;
                break;
            case "Production-Bar":
                level = BAR_LEVELS.getOrDefault(item.getProcessedName(), 0);
                stage = 1;
                break;
            case "Production-Plank":
                String logName = item.getProcessedName().equals("plank")
                        ? "logs" : item.getProcessedName().replace(" plank", " logs");
                level = LOG_LEVELS.getOrDefault(logName, 0);
                stage = 1;
                break;
            case "Gathering-Log":
                level = LOG_LEVELS.getOrDefault(item.getProcessedName(), 0);
                stage = 0;
                break;
            case "Farming-Seed":
                level = SEED_LEVELS_FARMING.getOrDefault(item.getProcessedName(), 10_000);
                stage = 0;
                break;
            case "Farming-Sapling":
                String seedName = item.getProcessedName().replace(" sapling", " seed");
                level = SEED_LEVELS_FARMING.getOrDefault(seedName, 0);
                stage = 1;
                break;
            default:
                break;
        }
        return new int[]{level, stage};
    }

    private static boolean isCurrency(String processed, String base) {
        return processed.contains("coins") || processed.equals("platinum token") || processed.contains("old school bond")
                || base.equals("bond") || processed.contains("platinum token");
    }

    private static boolean isTeleport(String processed, OrganizeOptions options) {
        if (processed.contains("teleport") || processed.contains("ectophial") || processed.contains("chronicle")
                || processed.contains("royal seed pod") || processed.contains("dorgesh-kaan")) {
            return true;
        }
        return options.jewelleryOnSupplies() && isTeleportJewellery(processed);
    }

    private static boolean isTeleportJewellery(String processed) {
        return processed.contains("amulet of glory")
                || processed.contains("ring of dueling")
                || processed.contains("games necklace")
                || processed.contains("ring of wealth")
                || processed.contains("skills necklace")
                || processed.contains("combat bracelet")
                || processed.contains("burning amulet")
                || processed.contains("digsite pendant")
                || processed.contains("slayer ring")
                || processed.contains("necklace of passage")
                || processed.contains("explorer's ring")
                || processed.contains("ring of returning")
                || processed.contains("necklace of faith")
                || processed.equals("lyre")
                || processed.contains("enchanted lyre");
    }

    private static boolean isFood(String processed, OrganizeOptions options) {
        if (processed.contains("wine of zamorak") || processed.equals("cake tin") || processed.equals("pie dish")
                || processed.contains("seed") || processed.contains("sapling") || processed.contains("potion")) {
            return false;
        }
        if (processed.equals("cabbage") || processed.equals("onion") || processed.startsWith("apples")) {
            return true;
        }
        if (options.tight() && isMissedFood(processed)) {
            return true;
        }
        for (String food : FOOD) {
            if (processed.contains(food)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMissedFood(String processed) {
        return processed.equals("tomato")
                || processed.equals("banana")
                || processed.equals("baguette")
                || processed.contains("triangle sandwich");
    }

    private static boolean isDrink(String processed) {
        if (processed.contains("wine of zamorak") || processed.contains("shaker") || processed.contains("guide")) {
            return false;
        }
        return processed.contains("beer")
                || processed.contains("ale")
                || processed.contains("cider")
                || processed.contains("grog")
                || processed.contains("wine")
                || processed.contains("mind bomb")
                || processed.contains("eclipse red")
                || processed.contains("cup of tea")
                || processed.contains("cocktail");
    }

    private static boolean isHerblorePotion(String name) {
        return name.contains("compost potion")
                || name.contains("(unf)")
                || name.contains("unfinished potion");
    }

    private static boolean isDye(String name) {
        return name.equals("dye") || name.endsWith(" dye");
    }

    private static boolean isTightProductionExtra(String name) {
        return name.contains("mark of grace") || name.contains("sawmill coupon");
    }

    private static boolean isPinnedMain(String name, OrganizeOptions options) {
        if (name.contains("dramen staff") || name.contains("soaked page") || name.contains("burnt page")) {
            return true;
        }
        return !options.tight() && isTightProductionExtra(name);
    }

    private static boolean acceptsGearTier(String name) {
        if (name.contains("seed") || name.contains(" ore") || name.endsWith(" bar")
                || name.contains("bone") || name.contains("nails") || name.contains("potion")
                || name.endsWith(" rune")) {
            return false;
        }
        return containsAny(name, GEAR_NOUNS);
    }

    private static final String[] GEAR_NOUNS = {
            "pickaxe", "axe", "sword", "scimitar", "dagger", "mace", "spear", "hasta",
            "warhammer", "halberd", "claws", "blackjack", "whip", "bow", "crossbow",
            "staff", "wand", "helm", "coif", "hood", "platebody", "chainbody", "platelegs",
            "plateskirt", "shield", "boots", "gloves", "gauntlets", "vambraces",
            "arrow", "bolt", "dart", "javelin", "knife", "chaps"
    };

    static boolean containsWord(String name, String word) {
        int from = 0;
        while (from < name.length()) {
            int i = name.indexOf(word, from);
            if (i < 0) {
                return false;
            }
            boolean start = i == 0 || !Character.isLetter(name.charAt(i - 1));
            int end = i + word.length();
            boolean endOk = end >= name.length() || !Character.isLetter(name.charAt(end));
            if (start && endOk) {
                return true;
            }
            from = i + 1;
        }
        return false;
    }

    private static String magicArmourPiece(String name) {
        if (name.contains("staff") || name.contains("wand") || name.contains("mind bomb")) {
            return null;
        }
        if (!isMagicArmourName(name)) {
            return null;
        }
        if (name.contains("hat") || name.contains("hood")) {
            return "Armour-Helmet";
        }
        if (name.contains("skirt") || name.contains("robe bottom") || name.contains("bottoms")) {
            return "Armour-Legs";
        }
        if (name.contains("boots")) {
            return "Armour-Boots";
        }
        if (name.contains("gloves")) {
            return "Armour-Gloves";
        }
        if (name.contains("robe") || name.contains("top")) {
            return "Armour-Body";
        }
        return "Armour-Set-Magic";
    }

    private static boolean isMagicArmourName(String name) {
        if (name.contains("mind bomb")) {
            return false;
        }
        return name.contains("wizard")
                || name.contains("mystic")
                || name.contains("splitbark")
                || name.contains("swampbark")
                || name.contains("bloodbark")
                || name.contains("infinity")
                || name.contains("ghostly")
                || name.contains("ancestral")
                || name.equals("blue skirt")
                || name.equals("black skirt")
                || name.equals("pink skirt")
                || name.equals("black robe");
    }

    private static boolean isPotion(BankSortItem item) {
        String name = item.getProcessedName();
        if (name.contains("prayer book") || name.contains("page")) {
            return false;
        }
        return (item.getDoseOrCharge() > 0 && containsAny(name, POTION_TERMS))
                || name.contains("(unf)")
                || name.contains("unfinished potion")
                || containsAny(name, POTION_TERMS);
    }

    private static boolean isRawFish(String name) {
        if (!name.startsWith("raw ")) {
            return false;
        }
        return containsAny(name, new String[]{
                "shrimp", "anchovies", "sardine", "herring", "mackerel", "trout", "cod", "pike",
                "salmon", "tuna", "lobster", "bass", "swordfish", "monkfish", "shark", "eel",
                "karambwan", "anglerfish", "dark crab", "sea turtle", "manta ray"
        });
    }

    private static boolean isClueFamily(String processed) {
        return processed.contains("clue") || processed.contains("casket") || processed.contains("stash")
                || processed.contains("master scroll book") || processed.contains("scroll box")
                || processed.contains("loop half of key") || processed.contains("tooth half of key");
    }

    private static boolean isHoliday(String name) {
        return name.contains("partyhat") || name.contains("santa hat") || name.contains("halloween")
                || name.contains("easter") || name.contains("birthday") || name.contains("cracker")
                || name.contains("holiday") || name.contains("cosmetic") || name.contains("rainbow");
    }

    private static boolean isMagicTablet(String name) {
        return name.contains(" tablet") || name.contains("bones to peaches")
                || name.startsWith("enchant ") || name.contains("alchemy tablet");
    }

    private static boolean isProductionTool(String name) {
        return name.endsWith(" mould") || name.endsWith(" mold")
                || name.equals("needle") || name.equals("thread") || name.equals("chisel")
                || name.equals("hammer") || name.contains("imcando hammer") || name.equals("knife") || name.equals("saw")
                || name.contains("glassblowing pipe") || name.contains("pottery")
                || name.contains("pestle and mortar") || name.equals("tinderbox")
                || name.equals("cooking pot") || name.equals("pot") || name.equals("bowl")
                || name.equals("pie dish") || name.equals("cake tin") || name.contains("skewer")
                || name.contains("meat tenderiser");
    }

    private static String productionToolCategory(String name) {
        if (name.endsWith(" mould") || name.endsWith(" mold")) return "Production-Mould";
        if (name.equals("needle") || name.equals("thread") || name.equals("chisel")
                || name.contains("glassblowing") || name.contains("pottery")
                || name.contains("pestle and mortar")) return "Tool-Crafting";
        if (name.equals("cooking pot") || name.equals("pot") || name.equals("bowl")
                || name.equals("pie dish") || name.equals("cake tin") || name.contains("skewer")
                || name.contains("meat tenderiser")) return "Tool-Cooking";
        if (name.equals("hammer") || name.contains("imcando hammer")) return "Tool-Smithing";
        if (name.equals("knife")) return "Tool-Fletching";
        if (name.equals("saw")) return "Tool-Construction";
        return "Tool-Other";
    }

    private static boolean isFarmingSupply(String name) {
        return name.equals("spade") || name.equals("rake") || name.contains("seed dibber")
                || name.contains("secateurs") || name.contains("watering can")
                || name.contains("gardening trowel") || name.contains("plant cure")
                || name.contains("compost") || name.contains("plant pot")
                || name.equals("basket") || name.equals("sack") || name.equals("empty sack")
                || name.equals("shears") || name.contains("willow branch")
                || name.contains("bird nest") || name.equals("weeds");
    }

    private static boolean isBucket(String name) {
        return name.equals("bucket") || name.equals("empty bucket")
                || name.equals("bucket of water") || name.equals("bucket of milk")
                || name.equals("bucket of slime") || name.equals("bucket of sand")
                || name.equals("bucket of wax") || name.equals("bucket of sap")
                || name.startsWith("bucket");
    }

    private static boolean isProductionMaterial(String name) {
        return name.contains("nails") || name.contains("soft clay") || name.equals("clay")
                || name.contains("unfired") || name.contains("limestone")
                || name.contains("papyrus") || name.contains("bolt of cloth")
                || name.contains("feather") || name.contains("headless arrow")
                || name.contains("arrow shaft") || name.contains("unfinished bow")
                || name.contains("cannonball") || name.contains("sinew") || name.contains("yak hair")
                || name.contains("blurite limbs");
    }

    private static boolean isGatheredMaterial(String name) {
        return name.equals("clay") || name.contains("granite") || name.contains("sandstone")
                || name.contains("saltpetre") || name.contains("volcanic ash")
                || name.contains("pay-dirt") || name.contains("stardust")
                || name.contains("unidentified minerals") || name.contains("daeyalt shard")
                || name.endsWith(" leaves") || name.equals("leaves") || name.equals("dynamite");
        // Note: swamp tar intentionally excluded here; it routes to Production-Other.
    }

    private static boolean isCraftingJewellery(String name) {
        boolean material = name.startsWith("gold ") || name.startsWith("silver ")
                || name.startsWith("opal ") || name.startsWith("jade ")
                || name.startsWith("topaz ") || name.startsWith("sapphire ")
                || name.startsWith("emerald ") || name.startsWith("ruby ")
                || name.startsWith("diamond ") || name.startsWith("dragonstone ")
                || name.startsWith("onyx ") || name.startsWith("zenyte ");
        return material && (name.endsWith(" ring") || name.endsWith(" necklace")
                || name.endsWith(" bracelet") || name.endsWith(" amulet"));
    }

    private static boolean isUnique(String name) {
        return name.contains("ornament kit") || name.contains("champion's scroll")
                || name.contains("champion scroll") || name.contains("torn prayer scroll")
                || name.contains("ancient page") || name.contains("god page")
                || name.contains("music cape hood");
    }

    private static boolean isNonCombatOutfit(String name) {
        return name.contains("beekeeper")
                || name.contains("chef's hat")
                || name.contains("mime")
                || name.contains("slave robe")
                || name.contains("slave boots")
                || name.contains("slave shirt")
                || name.contains("gas mask")
                || name.contains("ghostspeak")
                || name.contains("gnome amulet")
                || name.contains("goblin symbol")
                || name.contains("unstrung symbol")
                || name.equals("fancy boots")
                || name.equals("fancier boots")
                || name.equals("fighting boots")
                || name.contains("zombie boots")
                || name.contains("zombie shirt")
                || name.contains("butler's uniform")
                || name.contains("lederhosen")
                || name.contains("flower crown")
                || name.contains("camo top")
                || name.contains("camo bottoms")
                || name.contains("plague jacket")
                || name.contains("plague trousers")
                || name.contains("priest gown")
                || name.contains("pyromancer")
                || name.contains("khazard")
                || name.contains("bearhead")
                || name.contains("desert disguise")
                || name.contains("white apron")
                || name.contains("rainbow")
                || name.contains("warm gloves");
    }

    private static boolean isEssencePouch(String name) {
        if (name.contains("druid")) {
            return false;
        }
        return name.equals("small pouch")
                || name.equals("medium pouch")
                || name.equals("large pouch")
                || name.equals("giant pouch")
                || name.equals("colossal pouch")
                || name.contains("essence pouch");
    }

    private static boolean isHunterTool(String name) {
        return name.equals("box trap")
                || name.contains("butterfly net")
                || name.contains("butterfly jar")
                || name.equals("teasing stick")
                || name.equals("bird snare")
                || name.contains("rabbit snare");
    }

    private static boolean containsAny(String value, String[] terms) {
        for (String term : terms) {
            if (value.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private static void putLevels(Map<String, Integer> map, Object... entries) {
        for (int i = 0; i + 1 < entries.length; i += 2) {
            map.put((String) entries[i], (Integer) entries[i + 1]);
        }
    }
}
