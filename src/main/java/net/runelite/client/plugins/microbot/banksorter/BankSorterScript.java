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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class BankSorterScript extends Script {

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

    private volatile boolean stopRequested;
    private BankSorterConfig config;
    private BankOrganiserDebug debug;

    public boolean isStopRequested() {
        return stopRequested;
    }

    public void requestStop() {
        stopRequested = true;
        phase = "Stopped";
        detail = "Cancelled";
        Microbot.status = "Bank sorter stopped";
        if (mainScheduledFuture != null && !mainScheduledFuture.isDone()) {
            mainScheduledFuture.cancel(true);
        }
        busy = false;
    }

    int moveDelay() {
        int min = Math.min(config.minMoveDelayMs(), config.maxMoveDelayMs());
        int max = Math.max(config.minMoveDelayMs(), config.maxMoveDelayMs());
        return Rs2Random.betweenInclusive(min, max);
    }

    void recordMove() {
        done++;
    }

    void recordFailure() {
        failures++;
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
        done = 0;
        total = 0;
        failures = 0;
        busy = true;
        phase = fullOrganize ? "Organize" : "Sort tab";
        detail = "Starting";
        Microbot.status = phase;

        if (mainScheduledFuture != null && !mainScheduledFuture.isDone()) {
            mainScheduledFuture.cancel(true);
        }
        mainScheduledFuture = scheduledExecutorService.schedule(() -> {
            try {
                OrganizeOptions options = OrganizeOptions.from(config);
                OrganizeOptions.use(options);
                debug.info("Start " + phase + " " + options);
                if (!prepareBank()) {
                    return;
                }
                debug.info("Bank ready realTabs=" + BankTabMover.realTabCount());
                if (fullOrganize) {
                    routeAll();
                    if (!isStopRequested()) {
                        sortAllTabs();
                    }
                } else {
                    sortOpenTab();
                }
                if (isStopRequested()) {
                    phase = "Stopped";
                    detail = "Cancelled";
                } else {
                    phase = "Done";
                    detail = failures == 0 ? "Bank organized" : "Finished with " + failures + " skipped moves";
                }
                Microbot.status = detail;
            } catch (Throwable ex) {
                if (!stopRequested) {
                    log.error("Bank sorter failed", ex);
                    phase = "Error";
                    detail = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                    Microbot.status = detail;
                    if (debug != null) {
                        debug.info("ERROR " + detail);
                    }
                }
            } finally {
                try {
                    if (debug != null && Rs2Bank.isOpen()) {
                        phase = "Dump";
                        detail = "Writing debug dump";
                        List<BankSortItem> finalItems = BankTabMover.snapshot();
                        debug.snapshot(finalItems, "final");
                        debug.writeRunDump(finalItems, plugin, config.captureScreenshots());
                    }
                } catch (Exception dumpEx) {
                    log.error("Debug dump failed", dumpEx);
                }
                busy = false;
            }
        }, 0, TimeUnit.SECONDS);
        return true;
    }

    private boolean prepareBank() {
        phase = "Setup";
        if (!Rs2Bank.isOpen()) {
            phase = "Error";
            detail = "Open your bank first";
            Microbot.status = detail;
            return false;
        }
        detail = "Enabling Insert mode";
        if (!BankTabMover.ensureInsertMode()) {
            phase = "Error";
            detail = "Could not enable Insert mode";
            Microbot.status = detail;
            return false;
        }
        detail = "Enabling placeholders";
        if (!BankTabMover.ensurePlaceholders()) {
            phase = "Error";
            detail = "Could not enable placeholders";
            Microbot.status = detail;
            return false;
        }
        return true;
    }

    private void routeAll() {
        phase = "Routing";
        final int MAX_ITEM_FAILURES = 3;
        Set<Integer> permSkipped = new HashSet<>();
        Map<Integer, Integer> failCount = new HashMap<>();

        while (!isStopRequested() && Rs2Bank.isOpen()) {
            List<BankSortItem> items = BankTabMover.snapshot();
            debug.snapshot(items, "route");

            if (config.createMissingTabs()) {
                ensureTabsExist(items);
                if (isStopRequested()) {
                    return;
                }
                items = BankTabMover.snapshot();
            }

            int realTabs = BankTabMover.realTabCount();
            List<BankSortItem> misplaced = items.stream()
                    .filter(item -> !permSkipped.contains(item.getId()))
                    .filter(item -> {
                        int cur = item.getCurrentTab();
                        int home = item.getHomeTab().index;
                        if (cur < 0 || cur == home) {
                            return false;
                        }
                        if (home > 0 && home > realTabs) {
                            return false;
                        }
                        return true;
                    })
                    .collect(Collectors.toList());

            total = Math.max(total, done + misplaced.size());

            if (misplaced.isEmpty()) {
                detail = permSkipped.isEmpty() ? "IDEMPOTENT: 0 misplaced" : "Routing finished (" + permSkipped.size() + " skipped)";
                debug.info(detail + " realTabs=" + realTabs);
                return;
            }

            BankSortItem next = misplaced.get(0);
            int dest = next.getHomeTab().index;
            debug.info("Misplaced " + misplaced.size() + " next=" + next.getOriginalName()
                    + " cur=" + next.getCurrentTab() + " home=" + dest + " cat=" + next.getCategory());

            detail = "Moving " + next.getBaseName() + " to " + next.getHomeTab().label
                    + " (" + (done + 1) + "/" + Math.max(total, 1) + ")";
            Microbot.status = detail;

            boolean moved = BankTabMover.dragToTab(next, dest);
            debug.drag(next, next.getCurrentTab(), dest, moved, next.getCategory());
            if (moved) {
                done++;
                failCount.remove(next.getId());
                sleep(moveDelay());
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
                sleep(400);
            }
        }
    }

    private void ensureTabsExist(List<BankSortItem> items) {
        int existing = BankTabMover.realTabCount();
        if (existing >= 8) {
            debug.info("Skip tab creation: already have " + existing + " real tabs");
            return;
        }
        int needed = Math.min(8,
                items.stream()
                        .filter(i -> i.getHomeTab().index > 0)
                        .mapToInt(i -> i.getHomeTab().index)
                        .max().orElse(0));

        while (BankTabMover.realTabCount() < needed && BankTabMover.realTabCount() < 8 && !isStopRequested()) {
            int nextTab = BankTabMover.realTabCount() + 1;
            BankSortItem seed = pickSeedForNewTab(items, nextTab);
            if (seed == null) {
                debug.info("No main-tab item available to seed tab " + nextTab);
                break;
            }
            detail = "Creating tab " + nextTab;
            Microbot.status = detail;
            debug.info("Creating tab " + nextTab + " with seed " + seed.getOriginalName());
            if (!BankTabMover.dragToNewTab(seed)) {
                failures++;
                debug.info("Could not create tab " + nextTab);
                break;
            }
            done++;
            sleep(moveDelay());
            items = BankTabMover.snapshot();
        }
    }

    private BankSortItem pickSeedForNewTab(List<BankSortItem> items, int newTabIndex) {
        List<BankSortItem> mainItems = items.stream()
                .filter(item -> item.getCurrentTab() == 0)
                .collect(Collectors.toList());

        return mainItems.stream()
                .filter(item -> item.getHomeTab().index == newTabIndex)
                .findFirst()
                .orElseGet(() ->
                        mainItems.stream()
                                .filter(item -> item.getHomeTab().index > BankTabMover.realTabCount())
                                .findFirst()
                                .orElseGet(() ->
                                        mainItems.isEmpty() ? null : mainItems.get(0)));
    }

    private void sortAllTabs() {
        phase = "Sorting";
        int maxTab = Math.min(8, Math.max(1, BankTabMover.realTabCount()));
        for (int tab = 1; tab <= maxTab; tab++) {
            if (isStopRequested()) {
                return;
            }
            if (BankTabMover.tabCount(tab) <= 0) {
                continue;
            }
            sortTab(tab);
        }
        if (config.sortMainTab() && !isStopRequested()) {
            sortTab(0);
        }
    }

    private void sortOpenTab() {
        phase = "Sorting";
        sortTab(Rs2Bank.getCurrentTab());
    }

    private void sortTab(int tabIndex) {
        if (!BankTabMover.openTab(tabIndex)) {
            failures++;
            return;
        }
        BankHomeTab home = BankHomeTab.fromIndex(tabIndex);
        detail = "Sorting " + home.label;
        Microbot.status = detail;
        List<BankSortItem> items = BankTabMover.snapshot().stream()
                .filter(item -> item.getCurrentTab() == tabIndex)
                .sorted(Comparator.comparingInt(BankSortItem::getOriginalIndex))
                .collect(Collectors.toList());
        if (items.size() < 2) {
            debug.info("Skip sort tab " + tabIndex + ": " + items.size() + " items");
            return;
        }
        List<Integer> slots = new ArrayList<>();
        for (BankSortItem item : items) {
            slots.add(item.getOriginalIndex());
        }
        List<BankSortItem> sorted = BankInTabSorter.sortItems(items);
        if (BankInTabSorter.alreadyInOrder(items, sorted)) {
            debug.info("Skip sort tab " + tabIndex + ": already in order");
            return;
        }
        debug.info("Sorting tab " + tabIndex + " " + home.label + " (" + items.size() + " items)");
        BankInTabSorter.rearrange(sorted, slots, this);
    }

    @Override
    public void shutdown() {
        stopRequested = true;
        busy = false;
        super.shutdown();
    }
}
