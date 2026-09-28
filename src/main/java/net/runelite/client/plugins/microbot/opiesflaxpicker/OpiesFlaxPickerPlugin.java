package net.runelite.client.plugins.microbot.opiesflaxpicker;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.time.Instant;

@PluginDescriptor(
        name = PluginConstants.OPIE + "Flax Picker",
        description = "Picks flax at Nemus Retreat and can spin bow strings or linen yarn",
        tags = {"flax", "crafting", "nemus", "bow string", "linen", "opie"},
        authors = {"Opie"},
        version = OpiesFlaxPickerPlugin.version,
        minClientVersion = "1.9.6",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class OpiesFlaxPickerPlugin extends Plugin {
    static final String CONFIG = "opiesflaxpicker";
    public static final String version = "1.0.0";

    public Instant scriptStartTime;

    @Inject
    OpiesFlaxPickerScript script;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private OpiesFlaxPickerConfig config;
    @Inject
    private OpiesFlaxPickerOverlay overlay;

    @Override
    protected void startUp() {
        scriptStartTime = Instant.now();
        script.scriptStartEpochMs = scriptStartTime.toEpochMilli();
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        script.run(config);
    }

    @Override
    protected void shutDown() {
        scriptStartTime = null;
        if (overlayManager != null) {
            overlayManager.remove(overlay);
        }
        script.shutdown();
    }

    @Provides
    OpiesFlaxPickerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(OpiesFlaxPickerConfig.class);
    }

    String getTimeRunning() {
        return scriptStartTime != null
                ? TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now())
                : "";
    }
}
