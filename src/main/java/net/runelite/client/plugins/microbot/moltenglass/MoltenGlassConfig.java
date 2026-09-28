package net.runelite.client.plugins.microbot.moltenglass;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigInformation(
        "Start at or near Edgeville bank (3096, 3494). Keep Buckets of sand and Soda ash in the bank. "
                + "Each trip withdraws 14 of each, smelts at the furnace (3109, 3499), then deposits everything and repeats."
)
@ConfigGroup(MoltenGlassPlugin.CONFIG)
public interface MoltenGlassConfig extends Config {

    @ConfigItem(
            keyName = "stopAfterGlass",
            name = "Stop after molten glass",
            description = "Stop after smelting this many molten glass this session. 0 means no limit.",
            position = 0
    )
    @Range(min = 0, max = 1_000_000)
    default int stopAfterGlass() {
        return 0;
    }

    @ConfigItem(
            keyName = "bankPin",
            name = "Bank PIN",
            description = "Your 4-digit bank PIN. Leave empty if you have no PIN.",
            secret = true,
            position = 1
    )
    default String bankPin() {
        return "";
    }

    @ConfigItem(
            keyName = "hideOverlay",
            name = "Hide overlay",
            description = "Hide the stats overlay.",
            position = 2
    )
    default boolean hideOverlay() {
        return false;
    }
}
