package net.runelite.client.plugins.microbot.opramotherlode;

import com.google.inject.Provides;
import java.awt.AWTException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = PluginConstants.OPRA + "Motherlode Mine",
	description = "Mines paydirt in the Motherlode Mine",
	tags = {"paydirt", "mine", "motherlode", "mlm", "opra"},
	authors = {"Opra Dev Team"},
	version = OpraMotherlodePlugin.version,
	minClientVersion = "1.9.8",
	enabledByDefault = PluginConstants.DEFAULT_ENABLED,
	isExternal = PluginConstants.IS_EXTERNAL
)
public class OpraMotherlodePlugin extends Plugin {

	public static final String version = "1.1.0";

    @Inject
    private OpraMotherlodeConfig config;
    @Inject
    private OverlayManager overlayManager;

    @Inject
    private OpraMotherlodeOverlay motherloadMineOverlay;
    @Inject
    private OpraMotherlodeScript motherloadMineScript;

	@Getter
	private List<WorldPoint> blacklistedCrates = new ArrayList<>();

    @Provides
	OpraMotherlodeConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(OpraMotherlodeConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
		log.info("Starting OpraMotherlode plugin v{}", version);
        overlayManager.add(motherloadMineOverlay);
        motherloadMineScript.run();
		log.info("OpraMotherlode startup complete");
    }

    @Override
    public void shutDown() {
		log.info("Starting OpraMotherlode shutdown");
        motherloadMineScript.shutdown();
        overlayManager.remove(motherloadMineOverlay);
		blacklistedCrates.clear();
		log.info("OpraMotherlode shutdown complete");
    }
}
