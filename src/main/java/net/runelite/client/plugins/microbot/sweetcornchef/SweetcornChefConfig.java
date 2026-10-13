package net.runelite.client.plugins.microbot.sweetcornchef;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigInformation(
        "Start at the Kastori oven (1379, 3053) with an empty inventory. "
                + "Cooking level 28+ is required for sweetcorn. "
                + "Only outer plants within pick range are used; the field cannot be walked into. "
                + "The oven is clicked from outside the hut (camera turns to it). "
                + "Pick until invent is full of raw sweetcorn (27 if keeping seeds), cook all, "
                + "drop cooked and burnt, then repeat. "
                + "Seeds can be kept or dropped in config."
)
@ConfigGroup(SweetcornChefPlugin.CONFIG)
public interface SweetcornChefConfig extends Config {

    @ConfigItem(
            keyName = "pickSpeed",
            name = "Pick speed",
            description = "Spam pick clicks the same plant until it is gone. Fast click is quicker than normal. Normal click waits between picks.",
            position = 0
    )
    default PickSpeed pickSpeed() {
        return PickSpeed.SPAM;
    }

    @ConfigItem(
            keyName = "seedHandling",
            name = "Seeds",
            description = "Keep sweetcorn seeds in the inventory (pick target becomes 27), or drop them with each batch.",
            position = 1
    )
    default SeedHandling seedHandling() {
        return SeedHandling.DROP;
    }

    @ConfigItem(
            keyName = "stopAfter",
            name = "Stop after",
            description = "Stop after this many cook/drop trips. 0 means no limit.",
            position = 2
    )
    @Range(min = 0, max = 1_000_000)
    default int stopAfter() {
        return 0;
    }

    @ConfigItem(
            keyName = "hideOverlay",
            name = "Hide overlay",
            description = "Hide the stats overlay.",
            position = 3
    )
    default boolean hideOverlay() {
        return false;
    }
}
