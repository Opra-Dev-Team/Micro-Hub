package net.runelite.client.plugins.microbot.banksorter;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class BankSorterOverlay extends OverlayPanel {
    private static final int WIDTH = 230;
    private static final int TEXT_CHARS = 30;

    private final BankSorterPlugin plugin;

    @Inject
    private BankSorterOverlay(BankSorterPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        BankSorterScript script = plugin.script();
        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(WIDTH, 0));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Bank Sorter " + BankSorterPlugin.version)
                .color(script.isBusy() ? Color.GREEN : Color.ORANGE)
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Phase")
                .right(script.getPhase())
                .build());
        String detail = script.getDetail();
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status")
                .right(detail == null || detail.isEmpty() ? "-" : trim(detail))
                .build());
        if (script.isBusy()) {
            int total = script.getTotal();
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Moves")
                    .right(script.getDone() + (total > 0 ? " / " + total : "")
                            + (script.getFailures() > 0 ? "  skipped " + script.getFailures() : ""))
                    .build());
            long eta = script.etaMs();
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Time")
                    .right(BankSorterScript.formatDuration(script.elapsedMs())
                            + (eta >= 0 ? "  left ~" + BankSorterScript.formatDuration(eta) : ""))
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Esc or Stop to end")
                    .leftColor(Color.LIGHT_GRAY)
                    .build());
        } else if (!script.getSummary().isEmpty()) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(trim(script.getSummary(), 60))
                    .leftColor(script.getFailures() > 0 ? Color.YELLOW : Color.WHITE)
                    .build());
        } else {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Start from the plugin settings")
                    .leftColor(Color.LIGHT_GRAY)
                    .build());
        }
        return super.render(graphics);
    }

    private static String trim(String value) {
        return trim(value, TEXT_CHARS);
    }

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "...";
    }
}
