package net.runelite.client.plugins.microbot.banksorter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigButton;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigInformation("Open your bank, then press Organize bank or Sort open tab below. A plan with the number of moves and an estimated time is shown first. Press Stop, or Esc in game, to stop after the current move. Tabs of any size are sorted with the fewest possible drags and checked when done. Iron 8-tab is the default layout. Tight iron groups families inside each tab. Insert mode and placeholders are turned on automatically. Run logs go to .runelite/bank-sorter, keeping the last 10.")
@ConfigGroup(BankSorterPlugin.CONFIG)
public interface BankSorterConfig extends Config {

    @ConfigSection(
            name = "Controls",
            description = "Start and stop the sorter. The bank must be open.",
            position = -1
    )
    String controlsSection = "controlsSection";

    @ConfigItem(
            keyName = BankSorterPlugin.ORGANIZE_BUTTON,
            name = "Organize bank",
            description = "Move every item to its home tab, then sort each tab.",
            position = 0,
            section = "controlsSection"
    )
    default ConfigButton organizeBank() {
        return new ConfigButton();
    }

    @ConfigItem(
            keyName = BankSorterPlugin.SORT_TAB_BUTTON,
            name = "Sort open tab",
            description = "Sort only the bank tab that is open now.",
            position = 1,
            section = "controlsSection"
    )
    default ConfigButton sortOpenTab() {
        return new ConfigButton();
    }

    @ConfigItem(
            keyName = BankSorterPlugin.STOP_BUTTON,
            name = "Stop",
            description = "Stop after the current move. Esc in game does the same.",
            position = 2,
            section = "controlsSection"
    )
    default ConfigButton stopSorting() {
        return new ConfigButton();
    }

    @ConfigItem(
            keyName = "confirmBeforeSorting",
            name = "Confirm before sorting",
            description = "Show the plan, move count and estimated time, and wait for OK before any item moves.",
            position = 3,
            section = "controlsSection"
    )
    default boolean confirmBeforeSorting() {
        return true;
    }

    @ConfigItem(
            keyName = "speed",
            name = "Speed",
            description = "Fast confirms each drag and moves on. Careful waits the min and max delay after every drag. Saved delay values are ignored while Fast is selected.",
            position = 0
    )
    default OrganizeOptions.Speed speed() {
        return OrganizeOptions.Speed.FAST;
    }

    @ConfigItem(
            keyName = "minMoveDelayMs",
            name = "Min move delay (ms)",
            description = "Minimum wait after a successful drag. Used only when Speed is Careful.",
            position = 1
    )
    @Range(min = 0, max = 5000)
    default int minMoveDelayMs() {
        return 350;
    }

    @ConfigItem(
            keyName = "maxMoveDelayMs",
            name = "Max move delay (ms)",
            description = "Maximum wait after a successful drag. Used only when Speed is Careful.",
            position = 2
    )
    @Range(min = 0, max = 8000)
    default int maxMoveDelayMs() {
        return 600;
    }

    @ConfigItem(
            keyName = "createMissingTabs",
            name = "Create missing tabs",
            description = "Create tabs 1-8 only when fewer than 8 real tabs exist. Never creates extras on a full bank.",
            position = 3
    )
    default boolean createMissingTabs() {
        return true;
    }

    @ConfigItem(
            keyName = "sortMainTab",
            name = "Sort main tab",
            description = "After routing, sort leftover currency on the main tab.",
            position = 4
    )
    default boolean sortMainTab() {
        return true;
    }

    @ConfigItem(
            keyName = "verboseDebug",
            name = "Verbose debug logs",
            description = "Write every item to the run log in .runelite/bank-sorter. The client log still only shows the summary lines.",
            position = 5
    )
    default boolean verboseDebug() {
        return false;
    }

    @ConfigItem(
            keyName = "captureScreenshots",
            name = "Capture tab screenshots",
            description = "After a run, screenshot each tab into the run folder in .runelite/bank-sorter.",
            position = 6
    )
    default boolean captureScreenshots() {
        return false;
    }

    @ConfigSection(
            name = "Layout",
            description = "Where categories go, and how tightly each tab is grouped",
            position = 7
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

    @ConfigItem(
            keyName = "fileLeftovers",
            name = "File leftovers",
            description = "Move spores, bird houses, nettles, unpowered orbs, and waterskins out of junk. Off leaves them in Misc.",
            position = 4,
            section = "layoutSection"
    )
    default boolean fileLeftovers() {
        return true;
    }

    @ConfigItem(
            keyName = "rawFish",
            name = "Raw fish",
            description = "Gathering keeps uncooked fish with other materials. With food puts them on Supplies next to cooked food.",
            position = 5,
            section = "layoutSection"
    )
    default OrganizeOptions.RawFishHome rawFish() {
        return OrganizeOptions.RawFishHome.GATHERING;
    }
}
