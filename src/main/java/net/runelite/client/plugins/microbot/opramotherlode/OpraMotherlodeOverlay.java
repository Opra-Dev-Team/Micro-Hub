package net.runelite.client.plugins.microbot.opramotherlode;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.opramotherlode.enums.MLMMiningSpot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

@Slf4j
public class OpraMotherlodeOverlay extends OverlayPanel {
    @Inject
    OpraMotherlodeOverlay(OpraMotherlodePlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setSnappable(true);
    }

    @Inject private OpraMotherlodeScript script;

    @Override
    public Dimension render(Graphics2D graphics) {
        try {

            panelComponent.setPreferredSize(new Dimension(275, 900));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Motherlode Mine")
                    .color(Color.ORANGE)
                    .build());


            if (Rs2AntibanSettings.devDebug)
                Rs2Antiban.renderAntibanOverlayComponents(panelComponent);

            addEmptyLine();


            if (script.miningSpot != MLMMiningSpot.IDLE) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Mining Location: " + script.miningSpot.name())
                        .build());
                addEmptyLine();
            }

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Sack: " + script.currentSackCount() + "/" + OpraMotherlodeScript.SACK_SIZE)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .right("Version: " + OpraMotherlodePlugin.version)
                    .build());
        } catch (Exception ex) {
            log.error("Error rendering Motherload Mine overlay: ", ex);
        }
        return super.render(graphics);
    }

    private void addEmptyLine() {
        panelComponent.getChildren().add(LineComponent.builder().build());
    }
}
