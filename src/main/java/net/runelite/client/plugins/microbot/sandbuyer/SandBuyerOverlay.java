package net.runelite.client.plugins.microbot.sandbuyer;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Instant;

public class SandBuyerOverlay extends OverlayPanel {

    private final SandBuyerPlugin plugin;
    private final SandBuyerConfig config;

    @Inject
    public SandBuyerOverlay(SandBuyerPlugin plugin, SandBuyerConfig config) {
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
            SandBuyerScript script = plugin.script;
            panelComponent.setPreferredSize(new Dimension(220, 220));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Sand Buyer")
                    .color(Color.YELLOW)
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

            int sandTarget = config.totalSandTarget();
            int ashTarget = config.totalAshTarget();

            String sandStr = sandTarget > 0
                    ? script.sandBought + "/" + sandTarget
                    : String.valueOf(script.sandBought);
            String ashStr = ashTarget > 0
                    ? script.ashBought + "/" + ashTarget
                    : String.valueOf(script.ashBought);

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Sand bought")
                    .right(sandStr)
                    .rightColor(Color.YELLOW)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Ash bought")
                    .right(ashStr)
                    .rightColor(Color.YELLOW)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("GP spent")
                    .right(formatGp((long) (script.sandBought + script.ashBought)
                            * SandBuyerScript.ITEM_COST))
                    .rightColor(Color.YELLOW)
                    .build());

            // Per-hour calculations
            long runtimeMs = plugin.scriptStartTime != null
                    ? Instant.now().toEpochMilli() - plugin.scriptStartTime.toEpochMilli()
                    : 1;
            double hours = runtimeMs / 3_600_000.0;
            int sandPh = hours > 0 ? (int) (script.sandBought / hours) : 0;
            int ashPh = hours > 0 ? (int) (script.ashBought / hours) : 0;
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Sand/hr")
                    .right(String.valueOf(sandPh))
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Ash/hr")
                    .right(String.valueOf(ashPh))
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Hops")
                    .right(String.valueOf(script.hopCount))
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("World")
                    .right(String.valueOf(script.currentWorld))
                    .build());
        } catch (Exception ex) {
            Microbot.log(ex.getMessage());
        }
        return super.render(graphics);
    }

    private static String formatGp(long gp) {
        if (gp >= 1_000_000) return String.format("%.1fm", gp / 1_000_000.0);
        if (gp >= 1_000) return (gp / 1_000) + "k";
        return String.valueOf(gp);
    }
}
