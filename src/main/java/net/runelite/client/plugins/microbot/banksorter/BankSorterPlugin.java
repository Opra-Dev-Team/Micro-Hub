package net.runelite.client.plugins.microbot.banksorter;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.ClientTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;

@PluginDescriptor(
        name = PluginConstants.OPRA + "Bank Sorter",
        description = "Organizes the whole bank into a strict eight-tab iron layout and sorts tabs of any size",
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
    static final String ORGANIZE_BUTTON = "organizeBank";
    static final String SORT_TAB_BUTTON = "sortOpenTab";
    static final String STOP_BUTTON = "stopSorting";
    private static final String TITLE = "Bank Sorter";
    public static final String version = "1.2.2";

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private BankSorterOverlay overlay;
    @Inject
    private KeyManager keyManager;
    @Inject
    private BankSorterScript script;
    @Inject
    private BankSorterConfig config;
    @Inject
    @Getter
    private DrawManager drawManager;

    /** Esc is only taken while a run is going, so it still closes the bank otherwise. */
    private final KeyListener escToStop = new KeyListener() {
        @Override
        public void keyTyped(KeyEvent e) {
        }

        @Override
        public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ESCAPE && script.isBusy()) {
                log.info("Esc pressed, stopping bank sorter");
                script.requestStop("Stopped with Esc");
                e.consume();
            }
        }

        @Override
        public void keyReleased(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ESCAPE && script.isBusy()) {
                e.consume();
            }
        }
    };

    @Override
    protected void startUp() {
        overlayManager.add(overlay);
        keyManager.registerKeyListener(escToStop);
    }

    @Override
    protected void shutDown() {
        script.requestStop("Plugin turned off");
        script.shutdown();
        overlayManager.remove(overlay);
        keyManager.unregisterKeyListener(escToStop);
    }

    @Provides
    BankSorterConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BankSorterConfig.class);
    }

    public BankSorterScript script() {
        return script;
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!CONFIG.equals(event.getGroup()) || event.getKey() == null) {
            return;
        }
        switch (event.getKey()) {
            case ORGANIZE_BUTTON:
                log.info("Organize bank pressed");
                script.organize(config);
                break;
            case SORT_TAB_BUTTON:
                log.info("Sort open tab pressed");
                script.sortCurrentTab(config);
                break;
            case STOP_BUTTON:
                log.info("Stop pressed");
                script.requestStop();
                break;
            default:
                break;
        }
    }

    /** Blocks the calling script thread until the dialog is answered. */
    boolean confirmPlan(String plan) {
        AtomicBoolean ok = new AtomicBoolean(false);
        try {
            SwingUtilities.invokeAndWait(() -> ok.set(JOptionPane.showConfirmDialog(
                    ClientUI.getFrame(),
                    plan + "\n\nStart now? Esc in game stops after the current move.",
                    TITLE,
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.INFORMATION_MESSAGE) == JOptionPane.OK_OPTION));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (InvocationTargetException e) {
            log.error("Bank sorter confirm dialog failed", e);
            return false;
        }
        return ok.get();
    }

    /** The overlay only shows with the bank open, so problems before that need a dialog. */
    void tell(String message) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                ClientUI.getFrame(), message, TITLE, JOptionPane.INFORMATION_MESSAGE));
    }

    @Subscribe
    public void onClientTick(ClientTick event) {
        boolean show = Rs2Bank.isOpen() || script.isBusy();
        boolean shown = overlayManager.anyMatch(o -> o == overlay);
        if (show && !shown) {
            overlayManager.add(overlay);
        } else if (!show && shown) {
            overlayManager.remove(overlay);
        }
    }
}
