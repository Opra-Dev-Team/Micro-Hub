package net.runelite.client.plugins.microbot.oprablastfurnace;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.oprablastfurnace.enums.State;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.runelite.client.plugins.microbot.oprablastfurnace.OpraBlastFurnaceScript.*;

@PluginDescriptor(
        name = PluginConstants.OPRA + "Blast Furnace",
        description = "Smelts bars at the Blast Furnace in Keldagrim",
        tags = {"smithing", "bar", "ore", "blast furnace", "keldagrim", "opra"},
        authors = {"Opra Dev Team"},
        version = OpraBlastFurnacePlugin.version,
        minClientVersion = "2.1.0",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class OpraBlastFurnacePlugin extends Plugin {
    public static final String version = "1.0.13";
    private static final Pattern COAL_BAG_CONTAINS = Pattern.compile("contains (\\d+)");

    @Inject
    private OpraBlastFurnaceConfig config;

    @Provides
    OpraBlastFurnaceConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(OpraBlastFurnaceConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private OpraBlastFurnaceOverlay blastFurnaceOverlay;

    @Inject
    public OpraBlastFurnaceScript blastFurnaceScript;

    @Override
    protected void startUp() throws AWTException {
        log.info("Starting OpraBlastFurnace plugin v{}", version);
        if (overlayManager != null) {
            overlayManager.add(blastFurnaceOverlay);
        }
        blastFurnaceScript.run();
        log.info("OpraBlastFurnace startup complete");
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        String message = chatMessage.getMessage();
        if (message != null && message.toLowerCase().contains("foreman's permission")) {
            foremanPermissionNeeded = true;
            foremanPermissionChatMs = System.currentTimeMillis();
            log.info("[BF] Foreman permission required (chat)");
            return;
        }
        if (chatMessage.getType() != ChatMessageType.GAMEMESSAGE) {
            return;
        }
        if (message == null || !message.toLowerCase().contains("coal bag")) {
            return;
        }
        Boolean full = parseCoalBagMessage(message);
        if (full == null) {
            return;
        }
        coalBagFull = full;
        coalBagKnown = true;
        lastCoalBagChatMs = System.currentTimeMillis();
        if (config.debugLogging()) {
            log.debug("[BF] coal bag chat full={} msg='{}'", full, message);
        }
    }

    static Boolean parseCoalBagMessage(String message) {
        String lower = message.toLowerCase();
        if (lower.contains("is now empty") || lower.contains("coal bag is empty")) {
            return false;
        }
        Matcher m = COAL_BAG_CONTAINS.matcher(lower);
        if (m.find()) {
            return Integer.parseInt(m.group(1)) > 0;
        }
        if (lower.contains("contains one piece")) {
            return true;
        }
        if (lower.contains("is full") || lower.contains("can't hold any more")
                || lower.contains("cannot hold any more") || lower.contains("no more room")) {
            return true;
        }
        return null;
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged inventory) {
        if (inventory.getItemContainer().getId() != 93) {
            return;
        }

        final ItemContainer inv = inventory.getItemContainer();
        final boolean hasPrimary = inv.contains(config.getBars().getPrimaryOre());
        final boolean hasSecondary = inv.contains(config.getBars().getSecondaryOre());

        if (state != State.SMITHING && hasPrimary && primaryOreEmpty) {
            primaryOreEmpty = false;
        }
        if (state != State.BANKING && !hasPrimary && !primaryOreEmpty) {
            primaryOreEmpty = true;
        }

        if (state != State.SMITHING && hasSecondary && secondaryOreEmpty) {
            secondaryOreEmpty = false;
            if (config.debugLogging()) {
                log.debug("[BF] secondary ore flag cleared");
            }
        }
        if (state != State.BANKING && !hasSecondary && !secondaryOreEmpty) {
            secondaryOreEmpty = true;
            if (config.debugLogging()) {
                log.debug("[BF] secondary ore flag set empty");
            }
        }
    }

    @Override
    protected void shutDown() {
        log.info("Starting OpraBlastFurnace shutdown");
        blastFurnaceScript.shutdown();
        overlayManager.remove(blastFurnaceOverlay);
        log.info("OpraBlastFurnace shutdown complete");
    }
}
