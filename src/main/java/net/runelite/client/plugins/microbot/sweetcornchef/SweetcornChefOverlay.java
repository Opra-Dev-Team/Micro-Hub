package net.runelite.client.plugins.microbot.sweetcornchef;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class SweetcornChefOverlay extends OverlayPanel {

    private final SweetcornChefPlugin plugin;
    private final SweetcornChefConfig config;

    @Inject
    public SweetcornChefOverlay(SweetcornChefPlugin plugin, SweetcornChefConfig config) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (config.hideOverlay()) {
            return null;
        }
        try {
            SweetcornChefScript script = plugin.script;
            panelComponent.setPreferredSize(new Dimension(220, 200));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Sweetcorn Chef")
                    .color(Color.ORANGE)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Runtime")
                    .right(plugin.getTimeRunning())
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status")
                    .right(plainStatus(Microbot.status))
                    .rightColor(Color.GREEN)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State")
                    .right(script.state == null ? "-" : script.state.name())
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Pick speed")
                    .right(config.pickSpeed() == null ? "-" : config.pickSpeed().toString())
                    .build());

            int stopAfter = config.stopAfter();
            String trips = stopAfter > 0
                    ? script.tripsCompleted + "/" + stopAfter
                    : String.valueOf(script.tripsCompleted);
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Trips")
                    .right(trips)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Cooked")
                    .right(script.cooked + " (" + script.cookedPerHourSnapshot + "/hr)")
                    .rightColor(Color.YELLOW)
                    .build());
        } catch (Exception ex) {
            Microbot.log(ex.getMessage());
        }
        return super.render(graphics);
    }

    static String plainStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return "-";
        }
        String text = raw.replaceAll("<[^>]*>", "");
        text = text.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
        text = text.replaceAll("([a-z])([A-Z])", "$1 $2");
        int space = text.indexOf(' ');
        if (space > 0) {
            String first = text.substring(0, space);
            String second = text.substring(space + 1);
            int secondSpace = second.indexOf(' ');
            String secondWord = secondSpace < 0 ? second : second.substring(0, secondSpace);
            if (first.equalsIgnoreCase(secondWord)) {
                text = second;
            }
        }
        return text.isEmpty() ? "-" : text;
    }
}
