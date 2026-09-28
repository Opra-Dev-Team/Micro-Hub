package net.runelite.client.plugins.microbot.banksorter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigInformation("Open your bank and click Organize. Iron 8-tab is the default layout. Tight iron groups families inside each tab and pulls common leftovers out of junk. Clues, teleport jewellery, and drinks have their own placement settings. Changing a setting moves items on the next Organize. Placeholders are dragged with the real item so the empty slot waits in the right place. Insert mode is turned on automatically.")
@ConfigGroup(BankSorterPlugin.CONFIG)
public interface BankSorterConfig extends Config {

    @ConfigItem(
            keyName = "minMoveDelayMs",
            name = "Min move delay (ms)",
            description = "Minimum wait after a successful drag.",
            position = 0
    )
    @Range(min = 0, max = 5000)
    default int minMoveDelayMs() {
        return 350;
    }

    @ConfigItem(
            keyName = "maxMoveDelayMs",
            name = "Max move delay (ms)",
            description = "Maximum wait after a successful drag.",
            position = 1
    )
    @Range(min = 0, max = 8000)
    default int maxMoveDelayMs() {
        return 600;
    }

    @ConfigItem(
            keyName = "createMissingTabs",
            name = "Create missing tabs",
            description = "Create tabs 1-8 only when fewer than 8 real tabs exist. Never creates extras on a full bank.",
            position = 2
    )
    default boolean createMissingTabs() {
        return true;
    }

    @ConfigItem(
            keyName = "sortMainTab",
            name = "Sort main tab",
            description = "After routing, sort leftover currency on the main tab.",
            position = 3
    )
    default boolean sortMainTab() {
        return true;
    }

    @ConfigItem(
            keyName = "verboseDebug",
            name = "Verbose debug logs",
            description = "Log every snapshot item, mismatch, and drag.",
            position = 4
    )
    default boolean verboseDebug() {
        return true;
    }

    @ConfigItem(
            keyName = "captureScreenshots",
            name = "Capture tab screenshots",
            description = "After Organize, screenshot each tab into the dump folder.",
            position = 5
    )
    default boolean captureScreenshots() {
        return true;
    }

    @ConfigSection(
            name = "Layout",
            description = "Where categories go, and how tightly each tab is grouped",
            position = 6
    )
    String layoutSection = "layoutSection";

    @ConfigItem(
            keyName = "layout",
            name = "Layout",
            description = "Iron 8-tab keeps the current tab rules. Tight iron also groups families inside each tab and moves the known leftovers.",
            position = 0,
            section = "layoutSection"
    )
    default OrganizeOptions.Layout layout() {
        return OrganizeOptions.Layout.IRON;
    }

    @ConfigItem(
            keyName = "clueHome",
            name = "Clues",
            description = "Scrolls, scroll boxes, caskets, key halves, and clue hunter gear stay together.",
            position = 1,
            section = "layoutSection"
    )
    default OrganizeOptions.ClueHome clueHome() {
        return OrganizeOptions.ClueHome.MAIN;
    }

    @ConfigItem(
            keyName = "teleportJewellery",
            name = "Teleport jewellery",
            description = "Charged jewellery such as glories, dueling rings, passage necklaces, and explorer's rings. Chronicle stays on Supplies either way.",
            position = 2,
            section = "layoutSection"
    )
    default OrganizeOptions.JewelleryHome teleportJewellery() {
        return OrganizeOptions.JewelleryHome.SUPPLIES;
    }

    @ConfigItem(
            keyName = "drinks",
            name = "Drinks",
            description = "Beer, ale, wine, tea, mind bombs, and eclipse red. Own block keeps them on Supplies after cooked food. Wine of zamorak stays with herblore.",
            position = 3,
            section = "layoutSection"
    )
    default OrganizeOptions.Drinks drinks() {
        return OrganizeOptions.Drinks.WITH_FOOD;
    }
}
