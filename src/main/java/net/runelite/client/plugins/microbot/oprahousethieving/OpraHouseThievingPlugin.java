package net.runelite.client.plugins.microbot.oprahousethieving;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;


@PluginDescriptor(
        name = PluginConstants.OPRA + "House Thieving",
        description = "Pickpockets wealthy citizens and thieves houses in Varlamore",
        tags = {"thieving", "house thieving", "varlamore", "opra"},
        authors = {"Opra Dev Team"},
        version = OpraHouseThievingPlugin.version,
        minClientVersion = "2.0.7",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class OpraHouseThievingPlugin extends Plugin {
    public static final String version = "1.0.0";
    @Inject
    private OpraHouseThievingConfig config;

    @Provides
    OpraHouseThievingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(OpraHouseThievingConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private OpraHouseThievingOverlay houseThievingOverlay;

    OpraHouseThievingScript houseThievingScript;

    @Override
    protected void startUp() throws AWTException {
        log.info("Starting OpraHouseThieving plugin v{}", version);
        if (overlayManager != null) {
            overlayManager.add(houseThievingOverlay);
        }

        houseThievingScript = new OpraHouseThievingScript(this);
        houseThievingScript.run(config);
        log.info("OpraHouseThieving startup complete");
    }

    @Override
    protected void shutDown() {
        log.info("Starting OpraHouseThieving shutdown");
        new Thread(() -> houseThievingScript.shutdown()).start();
        overlayManager.remove(houseThievingOverlay);
        log.info("OpraHouseThieving shutdown complete");
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        // "You can't spot anything else worth taking from the <furniture>." => current piece is emptied, switch.
        if (houseThievingScript != null && event.getMessage().toLowerCase().contains("worth taking")) {
            houseThievingScript.onValuablesExhausted();
        }
    }
}
