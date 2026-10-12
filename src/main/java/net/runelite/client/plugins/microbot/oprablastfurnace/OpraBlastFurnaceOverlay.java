package net.runelite.client.plugins.microbot.oprablastfurnace;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

import static net.runelite.client.plugins.microbot.oprablastfurnace.OpraBlastFurnaceScript.state;

public class OpraBlastFurnaceOverlay extends OverlayPanel {
    private final OpraBlastFurnacePlugin plugin;
    private final OpraBlastFurnaceConfig config;

    @Inject
    OpraBlastFurnaceOverlay(OpraBlastFurnacePlugin plugin, OpraBlastFurnaceConfig config) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(280, config.debugLogging() ? 420 : 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Blast Furnace " + OpraBlastFurnacePlugin.version)
                    .color(Color.GREEN)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder().build());
            panelComponent.getChildren().add(LineComponent.builder().left(Microbot.status).build());
            if (plugin.blastFurnaceScript != null) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Coffer:")
                        .right(String.format("%,d", plugin.blastFurnaceScript.cofferCoins()))
                        .build());
                String foreman = plugin.blastFurnaceScript.foremanStatus();
                if (foreman != null) {
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Foreman:")
                            .right(foreman)
                            .build());
                }
            }

            if (config.debugLogging() && plugin.blastFurnaceScript != null) {
                OpraBlastFurnaceDebug debug = plugin.blastFurnaceScript.getDebug();
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("State: " + (state != null ? state : "N/A"))
                        .build());
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Step: " + debug.getContext())
                        .build());
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Idle: " + debug.getIdleSeconds() + "s")
                        .build());
                String snapshot = plugin.blastFurnaceScript.buildDebugSnapshot();
                if (snapshot.length() > 120) {
                    snapshot = snapshot.substring(0, 117) + "...";
                }
                panelComponent.getChildren().add(LineComponent.builder()
                        .left(snapshot)
                        .build());
            }
        } catch (Exception ex) {
            Microbot.logStackTrace(getClass().getSimpleName(), ex);
        }

        return super.render(graphics);
    }
}
