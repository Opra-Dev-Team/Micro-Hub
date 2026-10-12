



package net.runelite.client.plugins.microbot.oprablastfurnace;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.oprablastfurnace.enums.Bars;

@ConfigGroup("opra-blast-furnace")
@ConfigInformation("Must have Ice Gloves or smiths gloves (i) equipped<br /><br />If not doing gold bars, a coal bag is required.<br /><br />Stamina and energy potions are optional; if they are in the bank they will be used when run energy is low.<br /><br />Handles Foreman for &lt; 60 Smithing.<br /><br />If doing gold bars you must have Goldsmiths Gauntlets and bank your coal bag.<br /><br />Coffer: when the coffer drops below Top Up At, Top Up Amount coins are taken from the bank and deposited into it. Below 60 Smithing the foreman fee is paid first.<br /><br />")
public interface OpraBlastFurnaceConfig extends Config {
    @ConfigSection(
            name = "Blast Furnace Settings",
            description = "Blast Furnace Settings",
            position = 0,
            closedByDefault = false
    )
    String bFSettingsSection = "bFSettings";

    default boolean useStamina() {
        return true;
    }


    @ConfigItem(
            keyName = "Bars",
            name = "Bars",
            description = "Bars",
            position = 1,
            section = "bFSettings"
    )
    default Bars getBars() {
        return Bars.STEEL_BAR;
    }

    @ConfigSection(
            name = "Coffer",
            description = "Automatic coffer restocking",
            position = 1,
            closedByDefault = false
    )
    String cofferSection = "coffer";

    @ConfigItem(
            keyName = "cofferTopUpAmount",
            name = "Top Up Amount",
            description = "Coins deposited into the coffer each time it is topped up",
            position = 0,
            section = cofferSection
    )
    default int cofferTopUpAmount() {
        return 50000;
    }

    @ConfigItem(
            keyName = "cofferTopUpAt",
            name = "Top Up At",
            description = "Top up the coffer once it drops below this many coins",
            position = 1,
            section = cofferSection
    )
    default int cofferTopUpAt() {
        return 20000;
    }

    @ConfigSection(
            name = "Debug",
            description = "Logging and stall detection for test runs",
            position = 2,
            closedByDefault = true
    )
    String debugSection = "debug";

    @ConfigItem(
            keyName = "debugLogging",
            name = "Debug logging",
            description = "Log step transitions and early returns to the client log and Microbot.log",
            position = 0,
            section = debugSection
    )
    default boolean debugLogging() {
        return false;
    }

    @ConfigItem(
            keyName = "stallWarningSeconds",
            name = "Stall warning seconds",
            description = "Warn when the script stays on the same step for this many seconds without progress",
            position = 1,
            section = debugSection
    )
    default int stallWarningSeconds() {
        return 15;
    }
}