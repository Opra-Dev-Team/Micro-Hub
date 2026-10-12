package net.runelite.client.plugins.microbot.oprablastfurnace;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;

@Slf4j
final class OpraBlastFurnaceDebug {
    static final String PREFIX = "[BF] ";

    private String context = "startup";
    private long contextSinceMs = System.currentTimeMillis();
    private long lastStallWarnAtMs;
    private String lastEarlyReason = "";
    private long lastEarlyLogAtMs;

    void mark(OpraBlastFurnaceConfig config, String newContext) {
        if (newContext.equals(context)) {
            return;
        }
        context = newContext;
        contextSinceMs = System.currentTimeMillis();
        lastStallWarnAtMs = 0;
        if (config.debugLogging()) {
            log.debug("{}{}", PREFIX, newContext);
            Microbot.log(PREFIX + newContext);
        }
    }

    void logDebug(OpraBlastFurnaceConfig config, String message, Object... args) {
        if (!config.debugLogging()) {
            return;
        }
        log.debug(PREFIX + message, args);
    }

    void logEarlyReturn(OpraBlastFurnaceConfig config, String reason) {
        mark(config, "early:" + reason);
        if (!config.debugLogging()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (reason.equals(lastEarlyReason) && now - lastEarlyLogAtMs < 1000L) {
            return;
        }
        lastEarlyReason = reason;
        lastEarlyLogAtMs = now;
        Microbot.log(PREFIX + "early return: " + reason);
    }

    void warnIfStalled(OpraBlastFurnaceConfig config, String snapshot) {
        int stallSeconds = config.stallWarningSeconds();
        if (stallSeconds <= 0) {
            return;
        }
        long idleMs = System.currentTimeMillis() - contextSinceMs;
        if (idleMs < stallSeconds * 1000L) {
            return;
        }
        long now = System.currentTimeMillis();
        if (lastStallWarnAtMs > 0 && now - lastStallWarnAtMs < 5000L) {
            return;
        }
        lastStallWarnAtMs = now;
        String message = String.format(
                "STALL idle=%ds step=%s | %s",
                idleMs / 1000,
                context,
                snapshot);
        log.warn("{}{}", PREFIX, message);
        Microbot.log(PREFIX + message);
    }

    String getContext() {
        return context;
    }

    long getIdleSeconds() {
        return (System.currentTimeMillis() - contextSinceMs) / 1000L;
    }

    void reset() {
        context = "shutdown";
        contextSinceMs = System.currentTimeMillis();
        lastStallWarnAtMs = 0;
    }
}
