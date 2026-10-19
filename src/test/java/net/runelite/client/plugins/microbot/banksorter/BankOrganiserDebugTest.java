package net.runelite.client.plugins.microbot.banksorter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankOrganiserDebugTest {

    @Test
    void pruneKeepsTheNewestRuns(@TempDir Path root) throws IOException {
        for (int i = 1; i <= 13; i++) {
            Path run = Files.createDirectory(root.resolve(String.format("20261019-1200%02d", i)));
            Files.writeString(run.resolve("run.log"), "x");
        }

        BankOrganiserDebug.pruneOldRuns(root.toFile(), 10);

        String[] left = root.toFile().list();
        Arrays.sort(left);
        String[] expected = new String[10];
        for (int i = 0; i < 10; i++) {
            expected[i] = String.format("20261019-1200%02d", i + 4);
        }
        assertArrayEquals(expected, left);
    }

    @Test
    void runLogIsOnDiskBeforeTheRunEnds(@TempDir Path root) throws IOException {
        BankOrganiserDebug debug = new BankOrganiserDebug(false);
        debug.trace("before start");
        File dir = debug.startRun(root.toFile());
        debug.trace("MOVE tab=2 Adamant mace from=40 to=3 FAIL target-not-shown 900ms");
        Thread.currentThread().interrupt();
        try {
            debug.trace("after interrupt");
        } finally {
            Thread.interrupted();
        }

        String log = Files.readString(new File(dir, "run.log").toPath());
        assertTrue(log.contains("before start"));
        assertTrue(log.contains("FAIL target-not-shown"));
        assertTrue(log.contains("after interrupt"));
        debug.closeRun();
    }

    @Test
    void pruneIgnoresAMissingFolder(@TempDir Path root) {
        BankOrganiserDebug.pruneOldRuns(new File(root.toFile(), "missing"), 10);
    }
}
