package net.runelite.client.plugins.microbot.banksorter;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class BankSorterScript extends Script {
    private static final int SEEDS_PER_TAB = 3;
    private static final int MAX_ITEM_FAILURES = 3;
    /** First-run guesses before any move has been timed. */
    private static final long DEFAULT_FAST_MOVE_MS = 900;
    private static final double MOVE_MS_SMOOTHING = 0.2;

    @Inject
    private BankSorterPlugin plugin;

    @Getter
    private volatile boolean busy;
    @Getter
    private volatile String phase = "Idle";
    @Getter
    private volatile String detail = "";
    @Getter
    private volatile int done;
    @Getter
    private volatile int total;
    @Getter
    private volatile int failures;
    /** Last finished run, kept on the overlay until the next run starts. */
    @Getter
    private volatile String summary = "";

    private volatile boolean stopRequested;
    private volatile String stopReason;
    private volatile long startedMs;
    private volatile long moveMsAverage = -1;
    private volatile int laterEstimate;
    private boolean loggedSkipTabCreation;
    private BankSorterConfig config;
    private BankOrganiserDebug debug;

    public boolean isStopRequested() {
        return stopRequested;
    }

    public void requestStop() {
        requestStop("Stopped by you");
    }

    /** The running move finishes first, so the mouse is never left holding an item. */
    public void requestStop(String reason) {
        if (!busy || stopRequested) {
            return;
        }
        stopRequested = true;
        stopReason = reason;
        detail = "Stopping after this move";
        Microbot.status = "Bank sorter stopping";
    }

    int moveDelay() {
        if (OrganizeOptions.current().fast()) {
            return 0;
        }
        int min = Math.min(config.minMoveDelayMs(), config.maxMoveDelayMs());
        int max = Math.max(config.minMoveDelayMs(), config.maxMoveDelayMs());
        return Rs2Random.betweenInclusive(min, max);
    }

    void pauseAfterMove() {
        int delay = moveDelay();
        if (delay > 0) {
            sleep(delay);
        }
    }

    void recordMove(long ms) {
        done++;
        long sample = ms + (OrganizeOptions.current().fast() ? 0 : averageDelay());
        moveMsAverage = moveMsAverage < 0 ? sample
                : Math.round(moveMsAverage * (1 - MOVE_MS_SMOOTHING) + sample * MOVE_MS_SMOOTHING);
    }

    void recordFailure() {
        failures++;
    }

    /**
     * Microbot drops every gesture while the player's own mouse or keys are in use, so wait
     * that out instead of counting it as a failed move.
     *
     * @return false when the run should stop
     */
    boolean waitForHandsOff() {
        if (!BankTabMover.humanInputActive()) {
            return true;
        }
        String before = detail;
        detail = "Paused: hands off the mouse";
        Microbot.status = "Bank sorter paused: hands off the mouse";
        debug.info("Paused while your mouse or keys are in use");
        while (BankTabMover.humanInputActive()) {
            if (shouldStop()) {
                return false;
            }
            sleep(200);
        }
        detail = before;
        Microbot.status = before;
        return !shouldStop();
    }

    /** A sort stage planned {@code moves}; later tabs keep their estimate. */
    void planStage(int moves) {
        total = done + moves + laterEstimate;
    }

    void replan(int movesLeft) {
        total = done + movesLeft + laterEstimate;
    }

    /** Milliseconds left at the measured pace, or -1 before there is anything to time. */
    public long etaMs() {
        int left = total - done;
        if (!busy || left <= 0) {
            return -1;
        }
        return left * estimatedMoveMs();
    }

    public long elapsedMs() {
        return busy ? System.currentTimeMillis() - startedMs : -1;
    }

    private long estimatedMoveMs() {
        if (moveMsAverage > 0) {
            return moveMsAverage;
        }
        boolean fast = config == null || OrganizeOptions.current().fast();
        return DEFAULT_FAST_MOVE_MS + (fast ? 0 : averageDelay());
    }

    private long averageDelay() {
        return config == null ? 0 : (config.minMoveDelayMs() + config.maxMoveDelayMs()) / 2;
    }

    public boolean organize(BankSorterConfig config) {
        return startTask(config, true);
    }

    public boolean sortCurrentTab(BankSorterConfig config) {
        return startTask(config, false);
    }

    private boolean startTask(BankSorterConfig config, boolean fullOrganize) {
        if (busy) {
            Microbot.log("[Opra] Bank sorter is already running.");
            return false;
        }
        this.config = config;
        this.debug = new BankOrganiserDebug(config.verboseDebug());
        stopRequested = false;
        stopReason = null;
        loggedSkipTabCreation = false;
        done = 0;
        total = 0;
        failures = 0;
        laterEstimate = 0;
        summary = "";
        startedMs = System.currentTimeMillis();
        busy = true;
        phase = fullOrganize ? "Organize" : "Sort tab";
        detail = "Starting";
        Microbot.status = phase;

        if (mainScheduledFuture != null && !mainScheduledFuture.isDone()) {
            mainScheduledFuture.cancel(true);
        }
        mainScheduledFuture = scheduledExecutorService.schedule(() -> run(fullOrganize), 0, TimeUnit.SECONDS);
        return true;
    }

    private void run(boolean fullOrganize) {
        boolean ranMoves = false;
        try {
            OrganizeOptions options = OrganizeOptions.from(config);
            OrganizeOptions.use(options);
            debug.startRun(BankOrganiserDebug.RUNELITE_DUMPS);
            debug.info("Start " + phase + " " + options);
            if (!prepareBank()) {
                return;
            }
            debug.info("Bank ready realTabs=" + BankTabMover.realTabCount());
            int sortTab = fullOrganize ? -1 : Rs2Bank.getCurrentTab();
            Preview preview = fullOrganize ? previewOrganize() : previewSortTab(sortTab);
            debug.info("Preview: " + preview.describe().replace('\n', ' '));
            if (preview.totalMoves() == 0) {
                phase = "Done";
                detail = fullOrganize ? "Bank already organized" : "Tab already sorted";
                return;
            }
            if (config.confirmBeforeSorting()) {
                phase = "Waiting";
                detail = "Confirm the plan in the dialog";
                if (!plugin.confirmPlan(preview.describe() + "\n\nEstimated time: "
                        + formatDuration(preview.totalMoves() * estimatedMoveMs()))) {
                    phase = "Cancelled";
                    detail = "Plan not confirmed";
                    debug.info("Plan cancelled in the dialog");
                    return;
                }
            }
            ranMoves = true;
            if (fullOrganize) {
                laterEstimate = preview.sortMoves();
                total = preview.routeMoves + laterEstimate;
                boolean routed = routeAll();
                if (routed && !shouldStop()) {
                    sortAllTabs(preview);
                }
            } else {
                total = preview.totalMoves();
                sortTab(sortTab);
            }
        } catch (Throwable ex) {
            if (!stopRequested) {
                log.error("Bank sorter failed", ex);
                phase = "Error";
                detail = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                debug.info("ERROR " + detail);
            }
        } finally {
            finish(ranMoves);
        }
    }

    private void finish(boolean ranMoves) {
        if (stopRequested || stopReason != null) {
            phase = "Stopped";
            detail = stopReason == null ? "Stopped" : stopReason;
        } else if (!"Error".equals(phase) && !"Cancelled".equals(phase) && ranMoves) {
            phase = "Done";
            detail = failures == 0 ? "Sorted" : "Finished with " + failures + " skipped";
        }
        String finishedPhase = phase;
        String finishedDetail = detail;
        long elapsed = System.currentTimeMillis() - startedMs;
        summary = finishedPhase + " in " + formatDuration(elapsed) + ": " + done + " moves"
                + (failures > 0 ? ", " + failures + " skipped" : "")
                + (finishedDetail.isEmpty() ? "" : ". " + finishedDetail);
        debug.info("Summary: " + summary);
        boolean interrupted = Thread.interrupted();
        try {
            if (ranMoves) {
                detail = "Writing debug dump";
                List<BankSortItem> finalItems = BankTabMover.snapshot();
                debug.snapshot(finalItems, "final");
                debug.writeRunDump(finalItems, plugin, config.captureScreenshots());
            }
        } catch (Exception dumpEx) {
            log.error("Debug dump failed", dumpEx);
        } finally {
            debug.closeRun();
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        phase = finishedPhase;
        detail = finishedDetail;
        Microbot.status = "Bank sorter: " + finishedDetail;
        busy = false;
    }

    /** Stop asked for, or the bank went away. Sets the reason the overlay shows. */
    private boolean shouldStop() {
        if (stopRequested) {
            return true;
        }
        if (!Rs2Bank.isOpen()) {
            stopReason = "Bank closed";
            return true;
        }
        return false;
    }

    private boolean prepareBank() {
        phase = "Setup";
        if (!Rs2Bank.isOpen()) {
            fail("Open your bank first");
            plugin.tell("Open your bank first, then press the button again.");
            return false;
        }
        detail = "Enabling Insert mode";
        if (!BankTabMover.ensureInsertMode()) {
            fail("Could not enable Insert mode");
            return false;
        }
        detail = "Enabling placeholders";
        if (!BankTabMover.ensurePlaceholders()) {
            fail("Could not enable placeholders");
            return false;
        }
        return true;
    }

    private void fail(String message) {
        phase = "Error";
        detail = message;
        Microbot.status = message;
    }

    // ---- Preview ------------------------------------------------------------------------

    static final class Preview {
        final int routeMoves;
        /** Tab index to estimated sort moves, in sort order. */
        final Map<Integer, Integer> sortMoves = new LinkedHashMap<>();
        final Map<Integer, Integer> tabSizes = new HashMap<>();

        Preview(int routeMoves) {
            this.routeMoves = routeMoves;
        }

        int sortMoves() {
            return sortMoves.values().stream().mapToInt(Integer::intValue).sum();
        }

        int totalMoves() {
            return routeMoves + sortMoves();
        }

        String describe() {
            StringBuilder text = new StringBuilder();
            if (routeMoves > 0) {
                text.append("Move ").append(routeMoves).append(" items to their home tab.\n");
            }
            for (Map.Entry<Integer, Integer> entry : sortMoves.entrySet()) {
                if (entry.getValue() == 0) {
                    continue;
                }
                text.append("Sort ").append(BankHomeTab.fromIndex(entry.getKey()).label)
                        .append(" (tab ").append(entry.getKey()).append(", ")
                        .append(tabSizes.getOrDefault(entry.getKey(), 0)).append(" items): ")
                        .append(entry.getValue()).append(" moves.\n");
            }
            text.append("Total: ").append(totalMoves()).append(" moves.");
            return text.toString();
        }
    }

    private Preview previewSortTab(int tab) {
        detail = "Planning";
        List<BankSortItem> items = tabItems(tab);
        Preview preview = new Preview(0);
        if (items.size() >= 2) {
            preview.sortMoves.put(tab, BankInTabSorter.movesNeeded(tab, BankInTabSorter.sortItems(items)));
            preview.tabSizes.put(tab, items.size());
        }
        return preview;
    }

    /**
     * Routing appends each moved item to the end of its home tab, so the tabs after routing
     * are the items that stay, in order, then the arrivals in routing order.
     */
    private Preview previewOrganize() {
        detail = "Planning";
        List<BankSortItem> items = BankTabMover.snapshot();
        int realTabs = BankTabMover.realTabCount();
        int maxTabs = config.createMissingTabs() ? 8 : realTabs;
        List<BankSortItem> misplaced = misplaced(items, maxTabs, Set.of());
        Set<Integer> moving = misplaced.stream().map(BankSortItem::getId).collect(Collectors.toSet());
        Map<Integer, List<BankSortItem>> after = new HashMap<>();
        items.stream()
                .sorted(Comparator.comparingInt(BankSortItem::getOriginalIndex))
                .filter(item -> !moving.contains(item.getId()))
                .forEach(item -> after.computeIfAbsent(item.getCurrentTab(), t -> new ArrayList<>()).add(item));
        for (BankSortItem item : orderForRouting(misplaced)) {
            after.computeIfAbsent(item.getHomeTab().index, t -> new ArrayList<>()).add(item);
        }
        Preview preview = new Preview(misplaced.size());
        for (int tab : sortOrder(Math.max(realTabs, maxTabs))) {
            List<BankSortItem> tabItems = after.getOrDefault(tab, List.of());
            if (tabItems.size() < 2) {
                continue;
            }
            List<Integer> current = tabItems.stream().map(BankSortItem::getId).collect(Collectors.toList());
            List<Integer> desired = BankInTabSorter.sortItems(new ArrayList<>(tabItems)).stream()
                    .map(BankSortItem::getId).collect(Collectors.toList());
            preview.sortMoves.put(tab, BankSortPlanner.movesNeeded(current, desired));
            preview.tabSizes.put(tab, tabItems.size());
        }
        return preview;
    }

    private List<Integer> sortOrder(int tabCount) {
        List<Integer> order = new ArrayList<>();
        for (int tab = 1; tab <= Math.min(8, tabCount); tab++) {
            order.add(tab);
        }
        if (config.sortMainTab()) {
            order.add(0);
        }
        return order;
    }

    // ---- Routing ------------------------------------------------------------------------

    private static List<BankSortItem> misplaced(List<BankSortItem> items, int maxHomeTab, Set<Integer> skipped) {
        return items.stream()
                .filter(item -> !skipped.contains(item.getId()))
                .filter(item -> {
                    int cur = item.getCurrentTab();
                    int home = item.getHomeTab().index;
                    return cur >= 0 && cur != home && home <= maxHomeTab;
                })
                .collect(Collectors.toList());
    }

    /** Items in the open tab first so routing starts without a tab switch, then by slot. */
    private static List<BankSortItem> orderForRouting(List<BankSortItem> misplaced) {
        int open = Rs2Bank.getCurrentTab();
        List<BankSortItem> ordered = new ArrayList<>(misplaced);
        ordered.sort(Comparator.comparingInt((BankSortItem item) -> item.getCurrentTab() == open ? 0 : 1)
                .thenComparingInt(BankSortItem::getOriginalIndex));
        return ordered;
    }

    private boolean routeAll() {
        phase = "Routing";
        Set<Integer> permSkipped = new HashSet<>();
        Map<Integer, Integer> failCount = new HashMap<>();

        while (!shouldStop()) {
            List<BankSortItem> items = BankTabMover.snapshot();
            debug.verbose("route snapshot " + items.size());

            if (config.createMissingTabs()) {
                if (!ensureTabsExist(items)) {
                    return false;
                }
                if (shouldStop()) {
                    return false;
                }
                items = BankTabMover.snapshot();
            }

            int realTabs = BankTabMover.realTabCount();
            List<BankSortItem> misplaced = orderForRouting(misplaced(items, realTabs, permSkipped));
            total = done + misplaced.size() + laterEstimate;

            if (misplaced.isEmpty()) {
                detail = permSkipped.isEmpty() ? "Routing finished" : "Routing finished (" + permSkipped.size() + " skipped)";
                debug.info(detail + " realTabs=" + realTabs);
                return true;
            }

            BankSortItem next = misplaced.get(0);
            int dest = next.getHomeTab().index;
            debug.trace("Misplaced " + misplaced.size() + " next=" + next.getOriginalName()
                    + " cur=" + next.getCurrentTab() + " home=" + dest + " cat=" + next.getCategory());

            detail = "Moving " + next.getBaseName() + " to " + next.getHomeTab().label;
            Microbot.status = detail;
            if (!waitForHandsOff()) {
                return false;
            }

            long started = System.currentTimeMillis();
            boolean retry = failCount.containsKey(next.getId());
            boolean moved = BankTabMover.dragToTab(next, dest, retry);
            debug.drag(next, next.getCurrentTab(), dest, moved, next.getCategory());
            if (moved) {
                recordMove(System.currentTimeMillis() - started);
                failCount.remove(next.getId());
                pauseAfterMove();
            } else {
                int f = failCount.getOrDefault(next.getId(), 0) + 1;
                if (f >= MAX_ITEM_FAILURES) {
                    permSkipped.add(next.getId());
                    failures++;
                    debug.info("Giving up on " + next.getOriginalName() + " after " + f + " failures");
                } else {
                    failCount.put(next.getId(), f);
                    debug.info("Move failed for " + next.getOriginalName() + " (attempt " + f + ")");
                }
                sleep(OrganizeOptions.current().fast() ? 50 : 400);
            }
        }
        return false;
    }

    private boolean ensureTabsExist(List<BankSortItem> items) {
        int existing = BankTabMover.realTabCount();
        if (existing >= 8) {
            if (!loggedSkipTabCreation) {
                debug.info("Skip tab creation: already have " + existing + " real tabs");
                loggedSkipTabCreation = true;
            }
            return true;
        }
        int needed = Math.min(8,
                items.stream()
                        .filter(i -> i.getHomeTab().index > 0)
                        .mapToInt(i -> i.getHomeTab().index)
                        .max().orElse(0));

        while (BankTabMover.realTabCount() < needed && BankTabMover.realTabCount() < 8 && !shouldStop()) {
            int nextTab = BankTabMover.realTabCount() + 1;
            List<BankSortItem> seeds = pickSeedsForNewTab(items, nextTab);
            if (seeds.isEmpty()) {
                debug.info("No main-tab item available to seed tab " + nextTab);
                fail("Could not create tab " + nextTab);
                return false;
            }
            boolean created = false;
            for (BankSortItem seed : seeds) {
                if (shouldStop()) {
                    return false;
                }
                detail = "Creating tab " + nextTab;
                Microbot.status = detail;
                debug.info("Creating tab " + nextTab + " with seed " + seed.getOriginalName());
                long started = System.currentTimeMillis();
                if (BankTabMover.dragToNewTab(seed)) {
                    recordMove(System.currentTimeMillis() - started);
                    created = true;
                    break;
                }
                debug.info("Seed failed for tab " + nextTab + ": " + seed.getOriginalName());
            }
            if (!created) {
                if (shouldStop()) {
                    return false;
                }
                failures++;
                debug.info("Could not create tab " + nextTab);
                fail("Could not create tab " + nextTab);
                return false;
            }
            pauseAfterMove();
            items = BankTabMover.snapshot();
        }
        return !shouldStop();
    }

    private List<BankSortItem> pickSeedsForNewTab(List<BankSortItem> items, int newTabIndex) {
        List<BankSortItem> mainItems = items.stream()
                .filter(item -> item.getCurrentTab() == 0)
                .filter(item -> item.getQuantity() > 0)
                .collect(Collectors.toList());
        List<BankSortItem> seeds = new ArrayList<>();
        addSeeds(seeds, mainItems.stream()
                .filter(item -> item.getHomeTab().index == newTabIndex)
                .collect(Collectors.toList()));
        int realTabs = BankTabMover.realTabCount();
        addSeeds(seeds, mainItems.stream()
                .filter(item -> item.getHomeTab().index > realTabs)
                .collect(Collectors.toList()));
        addSeeds(seeds, mainItems);
        return seeds;
    }

    private static void addSeeds(List<BankSortItem> seeds, List<BankSortItem> candidates) {
        for (BankSortItem candidate : candidates) {
            if (seeds.size() >= SEEDS_PER_TAB) {
                return;
            }
            boolean already = seeds.stream().anyMatch(seed -> seed.getId() == candidate.getId());
            if (!already) {
                seeds.add(candidate);
            }
        }
    }

    // ---- Sorting ------------------------------------------------------------------------

    private void sortAllTabs(Preview preview) {
        phase = "Sorting";
        int maxTab = Math.min(8, Math.max(1, BankTabMover.realTabCount()));
        List<Integer> order = sortOrder(maxTab);
        for (int i = 0; i < order.size(); i++) {
            if (shouldStop()) {
                return;
            }
            int tab = order.get(i);
            laterEstimate = 0;
            for (int j = i + 1; j < order.size(); j++) {
                laterEstimate += preview.sortMoves.getOrDefault(order.get(j), 0);
            }
            if (tab > 0 && BankTabMover.tabCount(tab) <= 0) {
                continue;
            }
            sortTab(tab);
        }
        laterEstimate = 0;
    }

    private List<BankSortItem> tabItems(int tab) {
        return BankTabMover.snapshot().stream()
                .filter(item -> item.getCurrentTab() == tab)
                .sorted(Comparator.comparingInt(BankSortItem::getOriginalIndex))
                .collect(Collectors.toList());
    }

    private void sortTab(int tabIndex) {
        if (shouldStop()) {
            return;
        }
        BankHomeTab home = BankHomeTab.fromIndex(tabIndex);
        if (!BankTabMover.openTab(tabIndex)) {
            if (!shouldStop()) {
                failures++;
                debug.info("Could not open tab " + tabIndex + " " + home.label);
            }
            return;
        }
        phase = "Sorting";
        detail = "Sorting " + home.label;
        Microbot.status = detail;
        List<BankSortItem> items = tabItems(tabIndex);
        if (items.size() < 2) {
            debug.info("Skip sort tab " + tabIndex + ": " + items.size() + " items");
            return;
        }
        List<BankSortItem> sorted = BankInTabSorter.sortItems(items);
        debug.info("Sorting tab " + tabIndex + " " + home.label + " (" + items.size() + " items)");
        BankInTabSorter.rearrange(tabIndex, sorted, this, debug);
    }

    static String formatDuration(long ms) {
        long seconds = Math.max(0, Math.round(ms / 1000.0));
        if (seconds < 60) {
            return seconds + "s";
        }
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    @Override
    public void shutdown() {
        if (busy && stopReason == null) {
            stopReason = "Plugin turned off";
        }
        stopRequested = true;
        busy = false;
        super.shutdown();
    }
}
