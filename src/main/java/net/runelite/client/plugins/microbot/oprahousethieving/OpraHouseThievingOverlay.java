package net.runelite.client.plugins.microbot.oprahousethieving;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class OpraHouseThievingOverlay extends OverlayPanel {
    private final OpraHouseThievingPlugin plugin;
    private final OpraHouseThievingConfig config;

    @Inject
    OpraHouseThievingOverlay(OpraHouseThievingPlugin plugin, OpraHouseThievingConfig config) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Opra House Thieving " + OpraHouseThievingPlugin.version)
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(Microbot.status)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State: " + (plugin.houseThievingScript != null && plugin.houseThievingScript.getCurrentState() != null
                            ? plugin.houseThievingScript.getCurrentState()
                            : "N/A"))
                    .build());

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
