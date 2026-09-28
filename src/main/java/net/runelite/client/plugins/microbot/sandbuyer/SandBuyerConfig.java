package net.runelite.client.plugins.microbot.sandbuyer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigInformation(
        "Start near the Catherby charter ship with coins in your inventory. "
        + "The script buys 10 Buckets of sand and 10 Soda ash per world, deposits at "
        + "the nearby deposit box, hops, and repeats. Each item costs 5 coins (100 per round). "
        + "Set targets to 0 for unlimited."
)
@ConfigGroup(SandBuyerPlugin.CONFIG)
public interface SandBuyerConfig extends Config {

    @ConfigItem(
            keyName = "totalSandTarget",
            name = "Sand target (total)",
            description = "Stop after buying this many Buckets of sand total. 0 = unlimited.",
            position = 0
    )
    @Range(min = 0, max = 1000000)
    default int totalSandTarget() {
        return 0;
    }

    @ConfigItem(
            keyName = "totalAshTarget",
            name = "Soda ash target (total)",
            description = "Stop after buying this many Soda ash total. 0 = unlimited.",
            position = 1
    )
    @Range(min = 0, max = 1000000)
    default int totalAshTarget() {
        return 0;
    }

    @ConfigItem(
            keyName = "avoidEmptyWorlds",
            name = "Avoid empty worlds",
            description = "Skip worlds with very low population.",
            position = 2
    )
    default boolean avoidEmptyWorlds() {
        return true;
    }

    @ConfigItem(
            keyName = "avoidOvercrowdedWorlds",
            name = "Avoid overcrowded worlds",
            description = "Skip worlds with very high population.",
            position = 3
    )
    default boolean avoidOvercrowdedWorlds() {
        return true;
    }

    @ConfigItem(
            keyName = "worldCooldownSeconds",
            name = "World cooldown (seconds)",
            description = "Do not hop back to a world until this many seconds have passed.",
            position = 4
    )
    @Range(min = 0, max = 600)
    default int worldCooldownSeconds() {
        return 90;
    }

    @ConfigItem(
            keyName = "hideOverlay",
            name = "Hide overlay",
            description = "Hide the stats overlay.",
            position = 5
    )
    default boolean hideOverlay() {
        return false;
    }
}
