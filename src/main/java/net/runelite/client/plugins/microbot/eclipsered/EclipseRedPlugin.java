package net.runelite.client.plugins.microbot.eclipsered;

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
        name = PluginConstants.OPRA + "Eclipse Red",
        description = "Hops safe members worlds collecting the Hunter Guild Eclipse red spawn",
        tags = {"eclipse red", "wine", "ironman", "money", "opra"},
        authors = {"Opra Dev Team"},
        version = EclipseRedPlugin.version,
        minClientVersion = "1.9.6",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class EclipseRedPlugin extends Plugin {
    public static final String version = "1.2.4";
    static final String CONFIG = "eclipsered";

    public Instant scriptStartTime;

    @Inject
    EclipseRedScript script;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private EclipseRedConfig config;
    @Inject
    private EclipseRedOverlay overlay;

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
    EclipseRedConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(EclipseRedConfig.class);
    }

    String getTimeRunning() {
        return scriptStartTime != null
                ? TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now())
                : "";
    }
}
