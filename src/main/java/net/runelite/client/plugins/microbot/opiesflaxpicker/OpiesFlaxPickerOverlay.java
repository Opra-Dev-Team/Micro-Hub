package net.runelite.client.plugins.microbot.opiesflaxpicker;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class OpiesFlaxPickerOverlay extends OverlayPanel {

    private final OpiesFlaxPickerPlugin plugin;
    private final OpiesFlaxPickerConfig config;

    @Inject
    public OpiesFlaxPickerOverlay(OpiesFlaxPickerPlugin plugin, OpiesFlaxPickerConfig config) {
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
            OpiesFlaxPickerScript script = plugin.script;
            FlaxMode mode = config.mode();
            panelComponent.setPreferredSize(new Dimension(220, 220));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Flax Picker")
                    .color(Color.ORANGE)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Runtime")
                    .right(plugin.getTimeRunning())
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status")
                    .right(Microbot.status)
                    .rightColor(Color.GREEN)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State")
                    .right(script.state == null ? "-" : script.state.name())
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Mode")
                    .right(mode == null ? "-" : mode.toString())
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Pick speed")
                    .right(config.pickSpeed() == null ? "-" : config.pickSpeed().toString())
                    .build());

            int stopAfter = config.stopAfter();
            String banked = stopAfter > 0
                    ? script.banked + "/" + stopAfter
                    : String.valueOf(script.banked);
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(OpiesFlaxPickerScript.productLabel(mode))
                    .right(banked + " (" + script.perHourSnapshot + "/hr)")
                    .rightColor(Color.YELLOW)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Trips")
                    .right(String.valueOf(script.tripsCompleted))
                    .build());
        } catch (Exception ex) {
            Microbot.log(ex.getMessage());
        }
        return super.render(graphics);
    }
}
