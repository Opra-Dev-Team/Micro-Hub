package net.runelite.client.plugins.microbot.banksorter;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
final class BankOrganiserDebug {
    static final File RUNELITE_DUMPS = new File(RuneLite.RUNELITE_DIR, "bank-sorter");
    static final int KEEP_RUNS = 10;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final boolean verbose;
    private final List<String> lines = new ArrayList<>();
    private File runDir;
    private PrintWriter runLog;

    BankOrganiserDebug(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Makes this run's folder now and appends run.log as lines arrive, so a run that is
     * cancelled, for example by turning the plugin off, still leaves its log behind.
     */
    synchronized File startRun(File root) {
        runDir = new File(root, LocalDateTime.now().format(STAMP));
        runDir.mkdirs();
        try {
            runLog = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(new File(runDir, "run.log"), true), StandardCharsets.UTF_8));
            for (String line : lines) {
                runLog.println(line);
            }
            runLog.flush();
        } catch (IOException e) {
            log.error("Could not open run log in {}", runDir, e);
            runLog = null;
        }
        pruneOldRuns(root, KEEP_RUNS);
        return runDir;
    }

    synchronized void closeRun() {
        if (runLog != null) {
            runLog.close();
            runLog = null;
        }
    }

    private synchronized void append(String line) {
        lines.add(line);
        if (runLog != null) {
            runLog.println(line);
            runLog.flush();
        }
    }

    void info(String message) {
        append(message);
        log.info("[Opra] {}", message);
        Microbot.log("[Opra] " + message);
    }

    /** Run log and client log, kept out of the Microbot log so per-move lines do not flood it. */
    void trace(String message) {
        append(message);
        log.info("[Opra] {}", message);
    }

    void verbose(String message) {
        if (!verbose) {
            return;
        }
        append(message);
    }

    void snapshot(List<BankSortItem> items, String label) {
        int mismatches = 0;
        for (BankSortItem item : items) {
            boolean wrong = item.getCurrentTab() != item.getHomeTab().index;
            if (wrong) {
                mismatches++;
            }
            verbose(String.format(
                    "%s id=%d slot=%d curTab=%d home=%d(%s) cat=%s %s %s",
                    label,
                    item.getId(),
                    item.getOriginalIndex(),
                    item.getCurrentTab(),
                    item.getHomeTab().index,
                    item.getHomeTab().label,
                    item.getCategory(),
                    wrong ? "MISMATCH" : "ok",
                    item.getOriginalName()));
        }
        info(label + " snapshot: " + items.size() + " items, " + mismatches + " tab mismatches");
    }

    void drag(BankSortItem item, int from, int to, boolean ok, String extra) {
        info(String.format(
                "DRAG %s id=%d %s tab %d -> %d %s%s",
                item.getOriginalName(),
                item.getId(),
                ok ? "OK" : "FAIL",
                from,
                to,
                extra == null ? "" : extra,
                ""));
    }

    File writeRunDump(List<BankSortItem> items, BankSorterPlugin plugin, boolean screenshots) {
        File dir = runDir;
        if (dir == null) {
            dir = new File(RUNELITE_DUMPS, LocalDateTime.now().format(STAMP));
            dir.mkdirs();
        }

        writeLayout(dir, items);
        if (screenshots && plugin != null && plugin.getDrawManager() != null && Rs2Bank.isOpen()) {
            captureTabs(plugin, dir);
        }
        info("Dump written to " + dir.getAbsolutePath());
        if (runLog == null) {
            writeLog(dir);
        }
        pruneOldRuns(RUNELITE_DUMPS, KEEP_RUNS);
        return dir;
    }

    /** Run folders are named by timestamp, so name order is age order. */
    static void pruneOldRuns(File root, int keep) {
        File[] runs = root.listFiles(File::isDirectory);
        if (runs == null || runs.length <= keep) {
            return;
        }
        Arrays.sort(runs, Comparator.comparing(File::getName));
        for (int i = 0; i < runs.length - keep; i++) {
            deleteTree(runs[i]);
        }
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteTree(child);
            }
        }
        if (!file.delete()) {
            log.debug("Could not delete {}", file);
        }
    }

    private void writeLayout(File dir, List<BankSortItem> items) {
        Map<Integer, List<BankSortItem>> byTab = items.stream()
                .collect(Collectors.groupingBy(BankSortItem::getCurrentTab, TreeMap::new, Collectors.toList()));
        StringBuilder txt = new StringBuilder();
        StringBuilder json = new StringBuilder();
        StringBuilder mismatches = new StringBuilder();
        json.append("[\n");
        boolean first = true;
        int wrong = 0;
        for (int tab = 0; tab <= 8; tab++) {
            List<BankSortItem> tabItems = byTab.getOrDefault(tab, List.of()).stream()
                    .sorted(Comparator.comparingInt(BankSortItem::getOriginalIndex))
                    .collect(Collectors.toList());
            txt.append("=== TAB ").append(tab).append(" ").append(BankHomeTab.fromIndex(tab).label)
                    .append(" (").append(tabItems.size()).append(") ===\n");
            for (BankSortItem item : tabItems) {
                boolean mismatch = item.getCurrentTab() != item.getHomeTab().index;
                if (mismatch) {
                    wrong++;
                    mismatches.append(item.getOriginalName())
                            .append(" id=").append(item.getId())
                            .append(" slot=").append(item.getOriginalIndex())
                            .append(" cur=").append(item.getCurrentTab())
                            .append(" home=").append(item.getHomeTab().index)
                            .append(" ").append(item.getHomeTab().label)
                            .append(" cat=").append(item.getCategory())
                            .append('\n');
                }
                txt.append(mismatch ? "WRONG " : "ok    ")
                        .append("slot=").append(item.getOriginalIndex())
                        .append(" id=").append(item.getId())
                        .append(" cur=").append(item.getCurrentTab())
                        .append(" home=").append(item.getHomeTab().index)
                        .append(" cat=").append(item.getCategory())
                        .append(" ").append(item.getOriginalName())
                        .append('\n');
                if (!first) {
                    json.append(",\n");
                }
                first = false;
                json.append("  {\"id\":").append(item.getId())
                        .append(",\"slot\":").append(item.getOriginalIndex())
                        .append(",\"name\":\"").append(escape(item.getOriginalName())).append('"')
                        .append(",\"currentTab\":").append(item.getCurrentTab())
                        .append(",\"homeTab\":").append(item.getHomeTab().index)
                        .append(",\"category\":\"").append(escape(item.getCategory())).append('"')
                        .append(",\"wrong\":").append(mismatch)
                        .append('}');
            }
        }
        json.append("\n]\n");
        txt.append("\nMismatches: ").append(wrong).append('\n');
        writeText(new File(dir, "layout.txt"), txt.toString());
        writeText(new File(dir, "layout.json"), json.toString());
        writeText(new File(dir, "mismatches.txt"),
                wrong == 0 ? "None. All items are in their home tabs.\n" : mismatches.toString());
    }

    private synchronized void writeLog(File dir) {
        writeText(new File(dir, "run.log"), String.join("\n", lines) + "\n");
    }

    private void captureTabs(BankSorterPlugin plugin, File dir) {
        int maxTab = Math.min(8, Math.max(0, BankTabMover.realTabCount()));
        for (int tab = 0; tab <= maxTab; tab++) {
            if (!BankTabMover.openTab(tab)) {
                info("Could not open tab " + tab + " for screenshot");
                continue;
            }
            Global.sleep(400);
            BufferedImage image = grabFrame(plugin);
            if (image == null) {
                info("Screenshot failed for tab " + tab);
                continue;
            }
            String name = ("tab-" + tab + "-" + BankHomeTab.fromIndex(tab).label + ".png")
                    .replace(' ', '-')
                    .replaceAll("[\\\\/:*?\"<>|]", "-");
            writePng(new File(dir, name), image);
            info("Screenshot " + name);
        }
    }

    private BufferedImage grabFrame(BankSorterPlugin plugin) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<BufferedImage> ref = new AtomicReference<>();
        plugin.getDrawManager().requestNextFrameListener(img -> {
            if (img instanceof BufferedImage) {
                ref.set((BufferedImage) img);
            } else {
                BufferedImage copy = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
                copy.getGraphics().drawImage(img, 0, 0, null);
                ref.set(copy);
            }
            latch.countDown();
        });
        try {
            if (!latch.await(4, TimeUnit.SECONDS)) {
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        return ref.get();
    }

    private static void writePng(File file, BufferedImage image) {
        try {
            ImageIO.write(image, "PNG", file);
        } catch (IOException e) {
            log.error("Failed writing {}", file, e);
        }
    }

    /** A plain file stream, since NIO channels refuse to write on an interrupted thread. */
    private static void writeText(File file, String content) {
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write(content);
        } catch (IOException e) {
            log.error("Failed writing {}", file, e);
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
