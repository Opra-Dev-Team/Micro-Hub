package net.runelite.client.plugins.microbot.opiesflaxpicker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigInformation(
        "Start at Nemus Retreat, at the bank (1386, 3309) or the flax field (1373, 3320). "
                + "Anything already in the inventory is banked so all 28 slots are free. "
                + "Pick flax only fills the inventory and banks. Bow strings and linen yarn spin a full inventory at the wheel (1372, 3314) before banking. "
                + "One Nemus flax plant can give several flax. The script stays on it until it is depleted, then picks the next closest plant."
)
@ConfigGroup(OpiesFlaxPickerPlugin.CONFIG)
public interface OpiesFlaxPickerConfig extends Config {

    @ConfigItem(
            keyName = "mode",
            name = "Mode",
            description = "Pick flax only, or pick flax and spin bow strings or linen yarn.",
            position = 0
    )
    default FlaxMode mode() {
        return FlaxMode.PICK_ONLY;
    }

    @ConfigItem(
            keyName = "stopAfter",
            name = "Stop after",
            description = "Stop after this many flax, bow strings, or linen yarn have been banked, depending on the mode. 0 means no limit.",
            position = 1
    )
    @Range(min = 0, max = 1_000_000)
    default int stopAfter() {
        return 0;
    }

    @ConfigItem(
            keyName = "bankPin",
            name = "Bank PIN",
            description = "Your 4-digit bank PIN. Leave empty if you have no PIN. Hidden by default.",
            secret = true,
            position = 2
    )
    default String bankPin() {
        return "";
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
