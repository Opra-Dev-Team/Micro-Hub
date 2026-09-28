package net.runelite.client.plugins.microbot.sandbuyer;

import com.google.inject.Provides;
import lombok.Getter;
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
        name = PluginConstants.OPRA + "Sand Buyer",
        description = "Buys Buckets of sand and Soda ash from the Catherby Trader Crewmember, hops worlds and deposits",
        tags = {"sand", "soda ash", "charter", "glassblowing", "opra"},
        authors = {"Opra Dev Team"},
        version = SandBuyerPlugin.version,
        minClientVersion = "1.9.6",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class SandBuyerPlugin extends Plugin {
    static final String CONFIG = "sandbuyer";
    public static final String version = "1.0.3";

    public Instant scriptStartTime;

    @Inject SandBuyerScript script;
    @Inject private OverlayManager overlayManager;
    @Inject private SandBuyerConfig config;
    @Inject private SandBuyerOverlay overlay;

    @Override
    protected void startUp() {
        scriptStartTime = Instant.now();
        overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown() {
        scriptStartTime = null;
        overlayManager.remove(overlay);
        script.shutdown();
    }

    @Provides
    SandBuyerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(SandBuyerConfig.class);
    }

    String getTimeRunning() {
        return scriptStartTime != null
                ? TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now())
                : "";
    }
}
