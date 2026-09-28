package net.runelite.client.plugins.microbot.eclipsered;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class EclipseRedOverlay extends OverlayPanel {

    private final EclipseRedPlugin plugin;
    private final EclipseRedConfig config;

    @Inject
    public EclipseRedOverlay(EclipseRedPlugin plugin, EclipseRedConfig config) {
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
            panelComponent.setPreferredSize(new Dimension(220, 220));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Eclipse Red")
                    .color(Color.RED)
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
                    .right(plugin.script.state == null ? "-" : plugin.script.state.name())
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Wealth")
                    .right(plugin.script.wealthText())
                    .rightColor(Color.YELLOW)
                    .build());

            // wines/hr is recalculated once per bank trip for accuracy (not every render tick)
            int winesPerHour = plugin.script.winesPerHourSnapshot;

            int stopAfter = EclipseRedScript.targetWines(config);
            String wineCount = stopAfter > 0
                    ? plugin.script.winesCollected + "/" + stopAfter
                    : String.valueOf(plugin.script.winesCollected);
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Wines")
                    .right(wineCount + " (" + winesPerHour + "/hr)")
                    .rightColor(Color.YELLOW)
                    .build());
            if (config.stopGoal() == EclipseRedConfig.StopGoal.GP && EclipseRedScript.targetGp(config) > 0) {
                long collectedGp = (long) plugin.script.winesCollected * EclipseRedScript.WINE_VALUE_GP;
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("GP")
                        .right(EclipseRedScript.formatGp(collectedGp) + " / " + EclipseRedScript.formatGp(EclipseRedScript.targetGp(config)))
                        .rightColor(Color.YELLOW)
                        .build());
            }
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Hops")
                    .right(String.valueOf(plugin.script.hopCount))
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("World")
                    .right(String.valueOf(plugin.script.currentWorld))
                    .build());
        } catch (Exception ex) {
            Microbot.log(ex.getMessage());
        }
        return super.render(graphics);
    }
}
