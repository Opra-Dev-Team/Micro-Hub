package net.runelite.client.plugins.microbot.flaxpicker;

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
        name = PluginConstants.OPRA + "Flax Picker",
        description = "Picks flax at Nemus Retreat and can spin bow strings or linen yarn",
        tags = {"flax", "crafting", "nemus", "bow string", "linen", "opra"},
        authors = {"Opra Dev Team"},
        version = FlaxPickerPlugin.version,
        minClientVersion = "1.9.6",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class FlaxPickerPlugin extends Plugin {
    static final String CONFIG = "flaxpicker";
    public static final String version = "1.0.2";

    public Instant scriptStartTime;

    @Inject
    FlaxPickerScript script;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private FlaxPickerConfig config;
    @Inject
    private FlaxPickerOverlay overlay;

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
    FlaxPickerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(FlaxPickerConfig.class);
    }

    String getTimeRunning() {
        return scriptStartTime != null
                ? TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now())
                : "";
    }
}
