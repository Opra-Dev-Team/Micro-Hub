package net.runelite.client.plugins.microbot.eclipsered;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigInformation("Banks at the Hunter Guild first, then shows total wealth for coins plus Eclipse red at 700 gp each. After that it hops safe members worlds, loots the spawn at 1555, 3035, 2, and banks again when the inventory fills. Requires membership and Hunter Guild access.<br><br>Stop after gp is in thousands: 100 = 100k, 1000 = 1m. If a hop hits the login limit, the script waits and logs back in.")
@ConfigGroup(EclipseRedPlugin.CONFIG)
public interface EclipseRedConfig extends Config {

    @ConfigItem(
            keyName = "wineThreshold",
            name = "Wine threshold",
            description = "Bank when this many Eclipse reds are in the inventory.",
            position = 0
    )
    @Range(min = 1, max = 28)
    default int wineThreshold() {
        return 28;
    }

    @ConfigItem(
            keyName = "stopGoal",
            name = "Stop goal",
            description = "Stop after a wine count, or after a gold target. Gold is entered in thousands: 100 = 100k, 1000 = 1m. Each wine is worth 700 gp.",
            position = 1
    )
    default StopGoal stopGoal() {
        return StopGoal.WINES;
    }

    @ConfigItem(
            keyName = "stopAfterWines",
            name = "Stop after wines",
            description = "Used when Stop goal is WINES. Stop after this many Eclipse reds this session. 0 means no wine cap.",
            position = 2
    )
    @Range(min = 0, max = 100000)
    default int stopAfterWines() {
        return 0;
    }

    @ConfigItem(
            keyName = "stopAfterGpK",
            name = "Stop after gp (in k)",
            description = "Used when Stop goal is GP. Enter thousands of gp: 100 = 100k, 1000 = 1m. Wines needed = ceil(gp / 700). 0 means no gp cap.",
            position = 3
    )
    @Range(min = 0, max = 100000000)
    default int stopAfterGpK() {
        return 0;
    }

    @ConfigItem(
            keyName = "afterBanking",
            name = "After banking",
            description = "Resume collecting or stop the script after depositing.",
            position = 4
    )
    default AfterBanking afterBanking() {
        return AfterBanking.RESUME;
    }

    @ConfigItem(
            keyName = "worldCooldownSeconds",
            name = "World cooldown (seconds)",
            description = "Do not hop back to a world until this many seconds have passed.",
            position = 5
    )
    @Range(min = 0, max = 600)
    default int worldCooldownSeconds() {
        return 90;
    }

    @ConfigItem(
            keyName = "avoidEmptyWorlds",
            name = "Avoid empty worlds",
            description = "Skip worlds with very low population.",
            position = 6
    )
    default boolean avoidEmptyWorlds() {
        return true;
    }

    @ConfigItem(
            keyName = "avoidOvercrowdedWorlds",
            name = "Avoid overcrowded worlds",
            description = "Skip worlds with very high population.",
            position = 7
    )
    default boolean avoidOvercrowdedWorlds() {
        return true;
    }

    @ConfigItem(
            keyName = "maxWaitForWineMs",
            name = "Max wait for wine (ms)",
            description = "How long to wait for the spawn before hopping to another world.",
            position = 8
    )
    @Range(min = 0, max = 10000)
    default int maxWaitForWineMs() {
        return 1500;
    }

    @ConfigItem(
            keyName = "hopLimitWaitMinutes",
            name = "Hop limit wait (minutes)",
            description = "After a hop-limit logout or a stuck login screen, wait this long before logging in again.",
            position = 9
    )
    @Range(min = 1, max = 120)
    default int hopLimitWaitMinutes() {
        return 15;
    }

    @ConfigItem(
            keyName = "bankPin",
            name = "Bank PIN",
            description = "Your 4-digit bank PIN. Leave empty if you have no PIN. Hidden by default.",
            secret = true,
            position = 10
    )
    default String bankPin() {
        return "";
    }

    @ConfigItem(
            keyName = "hideOverlay",
            name = "Hide overlay",
            description = "Hide the stats overlay.",
            position = 11
    )
    default boolean hideOverlay() {
        return false;
    }

    enum AfterBanking {
        RESUME,
        STOP
    }

    enum StopGoal {
        WINES,
        GP
    }
}
