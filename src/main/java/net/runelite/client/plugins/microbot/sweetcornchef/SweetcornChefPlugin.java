package net.runelite.client.plugins.microbot.sweetcornchef;

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
        name = PluginConstants.OPRA + "Sweetcorn Chef",
        description = "Picks outer sweetcorn at Kastori, cooks a full inventory on the oven, drops it, and repeats",
        tags = {"sweetcorn", "cooking", "kastori", "chef", "opra"},
        authors = {"Opra Dev Team"},
        version = SweetcornChefPlugin.version,
        minClientVersion = "1.9.6",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class SweetcornChefPlugin extends Plugin {
    static final String CONFIG = "sweetcornchef";
    public static final String version = "1.0.0";

    public Instant scriptStartTime;

    @Inject
    SweetcornChefScript script;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private SweetcornChefConfig config;
    @Inject
    private SweetcornChefOverlay overlay;

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
    SweetcornChefConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(SweetcornChefConfig.class);
    }

    String getTimeRunning() {
        return scriptStartTime != null
                ? TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now())
                : "";
    }
}
