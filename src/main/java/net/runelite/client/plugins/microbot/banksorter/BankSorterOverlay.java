package net.runelite.client.plugins.microbot.banksorter;

import net.runelite.api.Client;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;

public class BankSorterOverlay extends OverlayPanel {

    private final BankSorterPlugin plugin;
    private final Client client;
    private final Color BUTTON_COLOR = new Color(60, 60, 60, 220);
    private final Color BUTTON_HOVER_COLOR = new Color(80, 80, 80, 240);
    private final Color STOP_COLOR = new Color(150, 60, 60, 220);
    private final Color BUTTON_TEXT_COLOR = new Color(255, 223, 0);

    @Inject
    private BankSorterOverlay(BankSorterPlugin plugin, Client client) {
        super(plugin);
        this.plugin = plugin;
        this.client = client;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.HIGH);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        Widget bankContainer = client.getWidget(ComponentID.BANK_CONTAINER);
        if (bankContainer == null || bankContainer.isHidden() || !Rs2Bank.isOpen()) {
            plugin.setButtonBounds(new Rectangle(), new Rectangle(), new Rectangle());
            return null;
        }

        Widget titleBar = client.getWidget(ComponentID.BANK_TITLE_BAR);
        if (titleBar == null || titleBar.isHidden()) {
            return null;
        }
        Rectangle titleBounds = titleBar.getBounds();
        int buttonHeight = Math.max(18, titleBounds.height - 2);
        int organizeWidth = 70;
        int sortWidth = 70;
        int stopWidth = 54;
        int gap = 2;
        int x = titleBounds.x + 2;
        int y = titleBounds.y + (titleBounds.height - buttonHeight) / 2;

        BankSorterScript script = plugin.script();
        Rectangle organize = new Rectangle(x, y, organizeWidth, buttonHeight);
        Rectangle sort = new Rectangle(x + organizeWidth + gap, y, sortWidth, buttonHeight);
        Rectangle stop = script.isBusy()
                ? new Rectangle(x + organizeWidth + sortWidth + gap * 2, y, stopWidth, buttonHeight)
                : new Rectangle();
        plugin.setButtonBounds(organize, sort, stop);

        drawButton(graphics, organize, "Organize", plugin.isHoveringOrganize() && !script.isBusy(), false);
        drawButton(graphics, sort, "Sort Tab", plugin.isHoveringSort() && !script.isBusy(), false);
        if (script.isBusy()) {
            drawButton(graphics, stop, "Stop", plugin.isHoveringStop(), true);
        }

        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(230, 90));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Bank Sorter")
                .color(Color.RED)
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Phase")
                .right(script.getPhase())
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status")
                .right(script.getDetail() == null || script.getDetail().isEmpty() ? "-" : trim(script.getDetail(), 28))
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Moves")
                .right(script.getDone() + (script.getTotal() > 0 ? "/" + script.getTotal() : "")
                        + " fail " + script.getFailures())
                .build());
        super.render(graphics);
        return new Dimension(organizeWidth + sortWidth + stopWidth + gap * 2, buttonHeight);
    }

    private void drawButton(Graphics2D graphics, Rectangle bounds, String text, boolean hover, boolean stop) {
        Stroke originalStroke = graphics.getStroke();
        Font originalFont = graphics.getFont();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(stop ? (hover ? new Color(180, 70, 70, 240) : STOP_COLOR)
                : (hover ? BUTTON_HOVER_COLOR : BUTTON_COLOR));
        graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        graphics.setColor(Color.BLACK);
        graphics.setStroke(new BasicStroke(2));
        graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        graphics.setColor(BUTTON_TEXT_COLOR);
        graphics.setFont(graphics.getFont().deriveFont(Font.BOLD, 12f));
        FontMetrics fm = graphics.getFontMetrics();
        int textX = bounds.x + (bounds.width - fm.stringWidth(text)) / 2;
        int textY = bounds.y + ((bounds.height - fm.getHeight()) / 2) + fm.getAscent();
        graphics.drawString(text, textX, textY);
        graphics.setStroke(originalStroke);
        graphics.setFont(originalFont);
    }

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "...";
    }
}
