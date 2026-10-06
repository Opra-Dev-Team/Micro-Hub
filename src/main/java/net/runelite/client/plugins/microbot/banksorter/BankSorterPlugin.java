package net.runelite.client.plugins.microbot.banksorter;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.ClientTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;

@PluginDescriptor(
        name = PluginConstants.OPRA + "Bank Sorter",
        description = "Organizes the whole bank into a strict eight-tab iron layout with debug dumps",
        tags = {"bank", "sort", "tab", "opra", "organize"},
        authors = {"Opra Dev Team"},
        version = BankSorterPlugin.version,
        minClientVersion = "2.0.7",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class BankSorterPlugin extends Plugin {
    static final String CONFIG = "banksorter";
    public static final String version = "2.4.0";

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private BankSorterOverlay overlay;
    @Inject
    private MouseManager mouseManager;
    @Inject
    private BankSorterScript script;
    @Inject
    private BankSorterConfig config;
    @Inject
    @Getter
    private DrawManager drawManager;

    private Rectangle organizeBounds = new Rectangle();
    private Rectangle sortBounds = new Rectangle();
    private Rectangle stopBounds = new Rectangle();
    @Getter
    private boolean hoveringOrganize;
    @Getter
    private boolean hoveringSort;
    @Getter
    private boolean hoveringStop;

    private final MouseAdapter mouseAdapter = new MouseAdapter() {
        @Override
        public MouseEvent mousePressed(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e)) {
                return e;
            }
            if (stopBounds.contains(e.getPoint()) && script.isBusy()) {
                log.info("Stop button pressed");
                script.requestStop();
                e.consume();
                return e;
            }
            if (script.isBusy()) {
                return e;
            }
            if (organizeBounds.contains(e.getPoint())) {
                log.info("Organize button pressed");
                startOrganize();
                e.consume();
            } else if (sortBounds.contains(e.getPoint())) {
                log.info("Sort Tab button pressed");
                startSortTab();
                e.consume();
            }
            return e;
        }

        @Override
        public MouseEvent mouseMoved(MouseEvent e) {
            hoveringOrganize = organizeBounds.contains(e.getPoint());
            hoveringSort = sortBounds.contains(e.getPoint());
            hoveringStop = stopBounds.contains(e.getPoint());
            return e;
        }
    };

    @Override
    protected void startUp() {
        overlayManager.add(overlay);
        // Run before the game and overlay listeners so our bank-title controls
        // receive and consume the exact click that was rendered.
        mouseManager.registerMouseListener(0, mouseAdapter);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
        mouseManager.unregisterMouseListener(mouseAdapter);
    }

    @Provides
    BankSorterConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BankSorterConfig.class);
    }

    public void setButtonBounds(Rectangle organize, Rectangle sort, Rectangle stop) {
        this.organizeBounds = new Rectangle(organize);
        this.sortBounds = new Rectangle(sort);
        this.stopBounds = new Rectangle(stop);
    }

    public BankSorterScript script() {
        return script;
    }

    public BankSorterConfig config() {
        return config;
    }

    private void startOrganize() {
        script.organize(config);
    }

    private void startSortTab() {
        script.sortCurrentTab(config);
    }

    @Subscribe
    public void onClientTick(ClientTick event) {
        if (Rs2Bank.isOpen()) {
            if (!overlayManager.anyMatch(o -> o == overlay)) {
                overlayManager.add(overlay);
            }
        } else if (overlayManager.anyMatch(o -> o == overlay)) {
            overlayManager.remove(overlay);
        }
    }
}
