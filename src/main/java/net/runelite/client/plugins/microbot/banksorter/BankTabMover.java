package net.runelite.client.plugins.microbot.banksorter;

import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Point;
import net.runelite.api.Varbits;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.antidrag.AntiDragConfig;
import net.runelite.client.plugins.antidrag.AntiDragPlugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.input.InputLoop;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

final class BankTabMover {
    static final int PLACEHOLDER_ID = 6512;
    static final int BANK_FILLER_ID = 20594;
    static final int PLACEHOLDER_BUTTON = 786472;
    static final int PLACEHOLDER_SPRITE_ON = 179;
    private static final int BANK_GROUP_ID = 12;
    private static final int BANK_ITEM_CONTAINER_CHILD_ID = 12;
    private static final int BANK_INSERT_BUTTON_CHILD_ID = 17;
    static final int ITEM_WINDOW_INSET = 4;
    static final int BANK_TAB_CONTAINER_DYNAMIC_MAIN_INDEX = 10;
    private static final int NEW_TAB_ATTEMPTS = 3;
    private static final int NEW_TAB_CONFIRM_MS = 2000;
    /** Client default from Anti Drag when the plugin is not overriding. One client tick is 20ms. */
    private static final int CLIENT_DRAG_DELAY_TICKS = 5;
    private static final int[] TAB_COUNT_VARBITS = {
            Varbits.BANK_TAB_ONE_COUNT,
            Varbits.BANK_TAB_TWO_COUNT,
            Varbits.BANK_TAB_THREE_COUNT,
            Varbits.BANK_TAB_FOUR_COUNT,
            Varbits.BANK_TAB_FIVE_COUNT,
            Varbits.BANK_TAB_SIX_COUNT,
            Varbits.BANK_TAB_SEVEN_COUNT,
            Varbits.BANK_TAB_EIGHT_COUNT,
            Varbits.BANK_TAB_NINE_COUNT
    };

    private BankTabMover() {
    }

    static boolean isInsertMode() {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getVarbitValue(Varbits.BANK_REARRANGE_MODE)).orElse(-1) == 1;
    }

    static boolean ensureInsertMode() {
        if (isInsertMode()) {
            return true;
        }
        Rectangle bounds = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget button = Microbot.getClient().getWidget(BANK_GROUP_ID, BANK_INSERT_BUTTON_CHILD_ID);
            return button == null ? null : new Rectangle(button.getBounds());
        }).orElse(null);
        if (!inCanvas(bounds)) {
            return false;
        }
        Microbot.getMouse().click(bounds);
        return Global.sleepUntil(BankTabMover::isInsertMode, 2500);
    }

    static boolean placeholdersEnabled() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget button = Microbot.getClient().getWidget(PLACEHOLDER_BUTTON);
            return button != null && button.getSpriteId() == PLACEHOLDER_SPRITE_ON;
        }).orElse(false);
    }

    static boolean ensurePlaceholders() {
        if (placeholdersEnabled()) {
            return true;
        }
        Rectangle bounds = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget button = Microbot.getClient().getWidget(PLACEHOLDER_BUTTON);
            return button == null ? null : new Rectangle(button.getBounds());
        }).orElse(null);
        if (!inCanvas(bounds)) {
            return false;
        }
        Microbot.getMouse().click(bounds);
        return Global.sleepUntil(BankTabMover::placeholdersEnabled, 2500);
    }

    static int tabCount(int tabIndex) {
        if (tabIndex < 1 || tabIndex > TAB_COUNT_VARBITS.length) {
            return 0;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getVarbitValue(TAB_COUNT_VARBITS[tabIndex - 1])).orElse(0);
    }

    static int realTabCount() {
        int widgetCount = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            List<Widget> tabs = Rs2Bank.getTabs();
            int count = 0;
            for (int i = 1; i <= 9; i++) {
                int dynamicIndex = BANK_TAB_CONTAINER_DYNAMIC_MAIN_INDEX + i;
                if (dynamicIndex < tabs.size() && isRealTab(tabs.get(dynamicIndex))) {
                    count = i;
                }
            }
            return count;
        }).orElse(0);
        if (widgetCount > 0) {
            return widgetCount;
        }

        // Fallback for clients that do not expose widget actions.
        int[] counts = readTabCounts();
        int count = 0;
        for (int i = 1; i <= 9; i++) {
            if (counts[i - 1] > 0) {
                count = i;
            }
        }
        return count;
    }

    private static boolean isRealTab(Widget widget) {
        if (widget == null || widget.isHidden()) {
            return false;
        }
        String[] actions = widget.getActions();
        if (actions == null) {
            return false;
        }
        for (String action : actions) {
            if (action != null && action.toLowerCase().contains("collapse tab")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rs2Bank.openTab indexes the tab bar without a bounds check and throws once the bank
     * closes, so the tab bar is checked first and any client error counts as "not opened".
     */
    static boolean openTab(int tabIndex) {
        if (!Rs2Bank.isOpen() || tabIndex < 0 || tabIndex > 9) {
            return false;
        }
        if (Rs2Bank.getCurrentTab() == tabIndex) {
            return true;
        }
        boolean opened = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            try {
                List<Widget> tabs = Rs2Bank.getTabs();
                if (tabs == null || tabs.size() <= BANK_TAB_CONTAINER_DYNAMIC_MAIN_INDEX + tabIndex) {
                    return false;
                }
                return tabIndex == 0 ? Rs2Bank.openMainTab() : Rs2Bank.openTab(tabIndex);
            } catch (RuntimeException ex) {
                return false;
            }
        }).orElse(false);
        if (!opened) {
            return false;
        }
        return Global.sleepUntil(() -> !Rs2Bank.isOpen() || Rs2Bank.getCurrentTab() == tabIndex, 2500)
                && Rs2Bank.isOpen();
    }

    /**
     * Quantity 0 is a placeholder of a real item and must be sorted.
     * Bank filler and the generic placeholder sprite are not items.
     */
    static boolean includeInSnapshot(int id, int quantity, String name) {
        if (quantity < 0 || id <= 0 || id == BANK_FILLER_ID || id == PLACEHOLDER_ID) {
            return false;
        }
        return name != null && !name.trim().isEmpty() && !"null".equalsIgnoreCase(name);
    }

    private static final Map<String, BankSortItem> CLASSIFIED = new HashMap<>();
    private static OrganizeOptions classifiedWith;

    /** Classification depends only on the item and the layout options, so it is cached per run. */
    static synchronized List<BankSortItem> snapshot() {
        List<BankSlot> slots = readSlots();
        int[] tabCounts = readTabCounts();
        if (classifiedWith != OrganizeOptions.current()) {
            CLASSIFIED.clear();
            classifiedWith = OrganizeOptions.current();
        }
        List<BankSortItem> items = new ArrayList<>();
        for (BankSlot slot : slots) {
            String name = slot.quantity == 0 ? slot.name + " (placeholder)" : slot.name;
            int tab = tabForSlot(slot.slot, tabCounts);
            BankSortItem classified = CLASSIFIED.computeIfAbsent(slot.id + "|" + name, key -> new BankSortItem(
                    slot.id, name, slot.slot, slot.quantity, tab, slot.classificationId, slot.classificationName));
            items.add(classified.at(slot.slot, slot.quantity, tab));
        }
        return items;
    }

    /**
     * Tab membership from the item container slot, matching OSRS tab varbits.
     * Varbit counts include placeholders, and so does {@link #readSlots()}.
     * Never use a filtered-list ordinal here or items shift tabs on re-run.
     */
    static int tabForSlot(int slot) {
        return tabForSlot(slot, readTabCounts());
    }

    static int tabForSlot(int slot, int[] tabCounts) {
        int total = 0;
        for (int i = 0; i < tabCounts.length; i++) {
            total += tabCounts[i];
            if (slot < total) {
                return i + 1;
            }
        }
        return 0;
    }

    static boolean dragToTab(BankSortItem item, int destTab) {
        return dragToTab(item, destTab, false);
    }

    /**
     * @param careful wait the full confirm window even in Fast mode, for retries of an item
     *                that already failed once
     */
    static boolean dragToTab(BankSortItem item, int destTab, boolean careful) {
        SourceRef sourceRef = locateSource(item);
        if (sourceRef == null) {
            Microbot.log("Could not locate visible source widget for " + item.getOriginalName());
            return false;
        }
        if (sourceRef.tab == destTab) {
            return true;
        }
        if (!revealSlot(sourceRef.slot, item.getId())) {
            Microbot.log("[Opra] Route " + item.getOriginalName() + ": source not in window slot=" + sourceRef.slot
                    + " rect=" + slotRect(sourceRef.slot, item.getId()) + " window=" + itemWindow());
            return false;
        }
        Rectangle source = slotRect(sourceRef.slot, item.getId());
        Rectangle target = destTabBounds(destTab);
        if (!fullyInside(itemWindow(), source) || !inCanvas(target)) {
            Microbot.log("Exact source or destination bounds unavailable for " + item.getOriginalName()
                    + " source=" + source + " target=" + target);
            return false;
        }
        int beforeDest = destTab == 0 ? mainApproxCount() : tabCount(destTab);
        int beforeSource = sourceRef.tab > 0 ? tabCount(sourceRef.tab) : 0;
        int qty = quantityFor(item.getId());
        if (humanInputActive()) {
            Microbot.log("[Opra] Route " + item.getOriginalName() + " skipped a drag: your mouse or keys are in use");
            return false;
        }
        dragBankItem(sourceRef.slot, source, target);
        int src = sourceRef.tab;
        String[] failed = {"none"};
        boolean ok = waitUntil(() -> {
            if (quantityFor(item.getId()) != qty) {
                failed[0] = "quantity " + qty + " -> " + quantityFor(item.getId());
                return false;
            }
            if (destTab > 0 && tabCount(destTab) <= beforeDest) {
                failed[0] = "dest tab " + destTab + " count stayed " + tabCount(destTab);
                return false;
            }
            if (src > 0 && destTab != src && tabCount(src) >= beforeSource) {
                failed[0] = "source tab " + src + " count stayed " + tabCount(src);
                return false;
            }
            int nowTab = tabForItem(item.getId());
            if (nowTab != destTab) {
                failed[0] = "item in tab " + nowTab;
                return false;
            }
            return true;
        }, 5000, careful);
        if (!ok) {
            BankSlot now = findById(item.getId());
            Microbot.log("[Opra] Route " + item.getOriginalName() + " id=" + item.getId() + " to tab " + destTab
                    + " not verified: " + failed[0]
                    + " | now slot=" + (now == null ? -1 : now.slot)
                    + " tab=" + (now == null ? -1 : tabForSlot(now.slot))
                    + " qty=" + (now == null ? 0 : now.quantity)
                    + " source=" + source + " target=" + target
                    + (careful ? " (careful wait)" : ""));
        }
        return ok;
    }

    static boolean dragToNewTab(BankSortItem item) {
        for (int attempt = 1; attempt <= NEW_TAB_ATTEMPTS; attempt++) {
            int beforeReal = realTabCount();
            if (beforeReal >= 9) {
                return false;
            }
            int newTabIndex = beforeReal + 1;
            int beforeCount = tabCount(newTabIndex);
            if (beforeCount > 0 && tabForItem(item.getId()) == newTabIndex) {
                return true;
            }
            SourceRef sourceRef = locateSource(item);
            if (sourceRef == null) {
                Microbot.log("Could not locate visible source widget for " + item.getOriginalName()
                        + " (attempt " + attempt + ")");
                continue;
            }
            int beforeSource = sourceRef.tab > 0 ? tabCount(sourceRef.tab) : 0;
            int quantity = quantityFor(item.getId());
            if (!revealSlot(sourceRef.slot, item.getId())) {
                Microbot.log("Source not in bank window for " + item.getOriginalName()
                        + " slot=" + sourceRef.slot + " rect=" + slotRect(sourceRef.slot, item.getId())
                        + " window=" + itemWindow() + " (attempt " + attempt + ")");
                continue;
            }
            Rectangle source = slotRect(sourceRef.slot, item.getId());
            NewTabTarget target = findNewTabTarget(beforeReal);
            if (!fullyInside(itemWindow(), source) || target == null || !inCanvas(target.bounds)) {
                Microbot.log("New-tab target or source bounds unavailable for " + item.getOriginalName()
                        + " at widget index " + (target == null ? -1 : target.index)
                        + " source=" + source
                        + " plus=" + (target == null ? null : target.bounds)
                        + " (attempt " + attempt + ")");
                continue;
            }
            dragBankItem(sourceRef.slot, source, target.bounds);
            boolean verified = waitUntil(() ->
                    tabCount(newTabIndex) > beforeCount
                            && (sourceRef.tab <= 0 || tabCount(sourceRef.tab) < beforeSource)
                            && quantityFor(item.getId()) == quantity
                            && tabForItem(item.getId()) == newTabIndex,
                    NEW_TAB_CONFIRM_MS,
                    true);
            if (verified) {
                return true;
            }
            Microbot.log("New-tab drag was not verified for " + item.getOriginalName()
                    + " into tab " + newTabIndex
                    + " source=" + source
                    + " plus=" + target.bounds
                    + " (attempt " + attempt + ")");
        }
        return false;
    }

    /**
     * Clears the client drag delay for this gesture so the item is picked up even when
     * Anti Drag, or the built-in 5-tick delay, is longer than the mouse hold.
     * The delay is restored after the drag returns.
     */
    static void dragBankItem(int sourceSlot, Rectangle source, Rectangle target) {
        DragDelay saved = clearDragDelay(sourceSlot);
        try {
            Microbot.getMouse().drag(centerPoint(source), centerPoint(target));
        } finally {
            restoreDragDelay(sourceSlot, saved);
        }
    }

    /**
     * The item icon must sit inside the bank item window. A rectangle that only ends
     * inside the game canvas can still be the tab bar or a slot scrolled above the view.
     * The top row sits flush with the window top at scroll 0, so touching an edge counts.
     */
    static boolean fullyInside(Rectangle container, Rectangle item) {
        if (container == null || item == null || item.width <= 0 || item.height <= 0
                || container.width <= 0 || container.height <= 0) {
            return false;
        }
        return item.x >= container.x
                && item.y >= container.y
                && item.x + item.width <= container.x + container.width
                && item.y + item.height <= container.y + container.height;
    }

    /** True when the bank cannot scroll any further in {@code dir} (-1 up, 1 down). */
    static boolean atScrollLimit(int scrollY, int viewH, int scrollH, int dir) {
        if (dir < 0) {
            return scrollY <= 0;
        }
        if (dir > 0) {
            return scrollY >= maxScrollY(viewH, scrollH);
        }
        return false;
    }

    /**
     * Child index of the plus button. Prefers the widget whose action is "New tab".
     * Falls back to the slot after the last real tab when that action is missing.
     */
    static int newTabDynamicIndex(List<String[]> actions, int realTabCount) {
        int found = dynamicChildWithAction(actions, "new tab");
        if (found >= 0) {
            return found;
        }
        return BANK_TAB_CONTAINER_DYNAMIC_MAIN_INDEX + realTabCount + 1;
    }

    static int dynamicChildWithAction(List<String[]> actions, String action) {
        if (actions == null || action == null) {
            return -1;
        }
        String needle = action.toLowerCase();
        for (int i = 0; i < actions.size(); i++) {
            if (containsAction(actions.get(i), needle)) {
                return i;
            }
        }
        return -1;
    }

    static Rectangle destTabBounds(int destTab) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget tab = Rs2Bank.getTabWidget(destTab);
            return tab == null ? null : new Rectangle(tab.getBounds());
        }).orElse(null);
    }

    /** {@code expectedItemId} -1 accepts any item. */
    private static Rectangle itemBounds(int slot, int expectedItemId, boolean requireVisible) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget item = Rs2Bank.getItemWidget(slot);
            if (item == null || (expectedItemId >= 0 && item.getItemId() != expectedItemId)
                    || (requireVisible && item.isHidden())) {
                return null;
            }
            Rectangle bounds = item.getBounds();
            return bounds == null ? null : new Rectangle(bounds);
        }).orElse(null);
    }

    /**
     * Fast mode polls every 50ms and gives up after 800ms. Careful mode keeps the longer confirm window.
     */
    static boolean waitUntil(BooleanSupplier condition, int carefulTimeoutMs) {
        return waitUntil(condition, carefulTimeoutMs, false);
    }

    /**
     * @param ignoreFast tab creation must wait out the varbit even in Fast mode
     */
    private static boolean waitUntil(BooleanSupplier condition, int timeoutMs, boolean ignoreFast) {
        boolean fast = !ignoreFast && OrganizeOptions.current().fast();
        int timeout = fast ? 800 : timeoutMs;
        int poll = fast ? 50 : 100;
        long deadline = System.currentTimeMillis() + timeout;
        while (true) {
            if (condition.getAsBoolean()) {
                return true;
            }
            if (System.currentTimeMillis() >= deadline) {
                return false;
            }
            Global.sleep(poll);
        }
    }

    private static int lastLoggedDeadTime = Integer.MIN_VALUE;

    private static DragDelay clearDragDelay(int sourceSlot) {
        int inventoryDelay = clientInventoryDragDelay();
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget item = Rs2Bank.getItemWidget(sourceSlot);
            int deadTime = item == null ? 0 : item.getDragDeadTime();
            if (deadTime != lastLoggedDeadTime) {
                Microbot.log("Cleared bank drag delay deadTime=" + deadTime + " inventoryDelay=" + inventoryDelay);
                lastLoggedDeadTime = deadTime;
            }
            if (item != null) {
                item.setDragDeadTime(0);
            }
            Microbot.getClient().setInventoryDragDelay(0);
            return new DragDelay(deadTime, inventoryDelay, item != null);
        }).orElse(null);
    }

    /**
     * This client exposes {@code setInventoryDragDelay} only. The live value is the Anti Drag
     * setting while that plugin is overriding, and 5 client ticks otherwise.
     */
    private static int clientInventoryDragDelay() {
        try {
            if (Microbot.getConfigManager() == null || !Microbot.isPluginEnabled(AntiDragPlugin.class)) {
                return CLIENT_DRAG_DELAY_TICKS;
            }
            AntiDragConfig antiDrag = Microbot.getConfigManager().getConfig(AntiDragConfig.class);
            if (antiDrag == null || antiDrag.onShiftOnly()) {
                return CLIENT_DRAG_DELAY_TICKS;
            }
            return Math.max(0, antiDrag.dragDelay());
        } catch (Exception ex) {
            return CLIENT_DRAG_DELAY_TICKS;
        }
    }

    private static void restoreDragDelay(int sourceSlot, DragDelay saved) {
        if (saved == null) {
            return;
        }
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (saved.hadWidget) {
                Widget item = Rs2Bank.getItemWidget(sourceSlot);
                if (item != null) {
                    item.setDragDeadTime(saved.deadTime);
                }
            }
            Microbot.getClient().setInventoryDragDelay(saved.inventoryDelay);
            return true;
        });
    }

    private static NewTabTarget findNewTabTarget(int realTabCount) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            List<Widget> tabs = Rs2Bank.getTabs();
            if (tabs == null || tabs.isEmpty()) {
                return null;
            }
            List<String[]> actions = new ArrayList<>(tabs.size());
            for (Widget tab : tabs) {
                actions.add(tab == null ? null : tab.getActions());
            }
            int index = newTabDynamicIndex(actions, realTabCount);
            if (index < 0 || index >= tabs.size() || tabs.get(index) == null || tabs.get(index).isHidden()) {
                return null;
            }
            Rectangle bounds = tabs.get(index).getBounds();
            if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
                return null;
            }
            return new NewTabTarget(index, new Rectangle(bounds));
        }).orElse(null);
    }

    private static Point centerPoint(Rectangle bounds) {
        int x = bounds.x + Math.max(0, bounds.width - 1) / 2;
        int y = bounds.y + Math.max(0, bounds.height - 1) / 2;
        return new Point(x, y);
    }

    private static Rectangle itemWindow() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget container = Microbot.getClient().getWidget(BANK_GROUP_ID, BANK_ITEM_CONTAINER_CHILD_ID);
            if (container == null || container.isHidden()) {
                return null;
            }
            Rectangle bounds = container.getBounds();
            if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
                return null;
            }
            return new Rectangle(bounds);
        }).orElse(null);
    }

    private static boolean containsAction(String[] actions, String needleLower) {
        if (actions == null) {
            return false;
        }
        for (String action : actions) {
            if (action != null && action.toLowerCase().contains(needleLower)) {
                return true;
            }
        }
        return false;
    }

    static boolean revealSlot(int slot, int itemId) {
        if (itemInWindow(slot, itemId)) {
            return true;
        }
        if (!scrollToShow(slot, -1)) {
            return false;
        }
        return waitForInWindow(slot, itemId, REVEAL_WAIT_MS);
    }

    /** {@code itemId} -1 accepts any item. */
    private static boolean itemInWindow(int slot, int itemId) {
        return fullyInside(itemWindow(), slotRect(slot, itemId));
    }

    /**
     * Where the slot is on the canvas right now, from the container's location and scroll.
     * {@code Widget.getBounds()} of a child lags a frame behind {@code setScrollY}.
     * {@code itemId} -1 accepts any item.
     */
    static Rectangle slotRect(int slot, int itemId) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget container = Microbot.getClient().getWidget(BANK_ITEM_CONTAINER_COMPONENT);
            Widget item = Rs2Bank.getItemWidget(slot);
            if (container == null || container.isHidden() || item == null || item.isHidden()
                    || (itemId >= 0 && item.getItemId() != itemId)) {
                return null;
            }
            Point origin = container.getCanvasLocation();
            if (origin == null) {
                return null;
            }
            return slotRect(origin.getX(), origin.getY(), container.getScrollX(), container.getScrollY(),
                    item.getRelativeX(), item.getRelativeY(), item.getWidth(), item.getHeight());
        }).orElse(null);
    }

    static Rectangle slotRect(int originX, int originY, int scrollX, int scrollY,
                              int relX, int relY, int width, int height) {
        return new Rectangle(originX + relX - scrollX, originY + relY - scrollY, width, height);
    }

    /** True once the game has laid the slot out where {@link #slotRect} says, so clicks hit it. */
    private static boolean layoutMatches(int slot) {
        Rectangle computed = slotRect(slot, -1);
        Rectangle drawn = itemBounds(slot, -1, true);
        return computed != null && drawn != null && computed.x == drawn.x && computed.y == drawn.y;
    }

    private static void awaitLayout(int slot) {
        long deadline = System.currentTimeMillis() + LAYOUT_WAIT_MS;
        while (!layoutMatches(slot) && System.currentTimeMillis() < deadline) {
            Global.sleep(20);
        }
    }

    private static boolean waitForInWindow(int slot, int itemId, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            if (itemInWindow(slot, itemId)) {
                return true;
            }
            if (System.currentTimeMillis() >= deadline) {
                return false;
            }
            Global.sleep(20);
        }
    }

    // ---- In-tab moves ------------------------------------------------------------------

    static final String METHOD_DIRECT = "direct";
    static final String METHOD_SCROLL = "scroll-drag";
    static final String METHOD_RELAY = "relay";
    static final String REASON_HUMAN = "human-input";
    static final String REASON_BANK_CLOSED = "bank-closed";
    static final String REASON_SOURCE_MOVED = "source-moved";
    static final String REASON_TARGET_NOT_SHOWN = "target-not-shown";
    private static final int BANK_SCROLL_VARC = 51;
    private static final int UPDATE_SCROLLBAR_SCRIPT = 72;
    private static final int BANK_SCROLLBAR_COMPONENT = (BANK_GROUP_ID << 16) | 13;
    private static final int BANK_ITEM_CONTAINER_COMPONENT = (BANK_GROUP_ID << 16) | BANK_ITEM_CONTAINER_CHILD_ID;
    private static final int REVEAL_WAIT_MS = 600;
    private static final int LAYOUT_WAIT_MS = 300;
    private static final int SYNC_WAIT_MS = 1500;
    private static final int MAX_RELAY_HOPS = 40;
    private static final int MOUSE_BUTTON_LEFT = 1;
    /** The wheel counts as not working when the scroll has not changed for this long. */
    private static final int WHEEL_STALL_MS = 350;
    private static final int WHEEL_TIMEOUT_MS = 8000;
    private static final int EDGE_STALL_MS = 1500;
    private static final int EDGE_TIMEOUT_MS = 12000;
    /** How far inside the item window edge the held item hovers so the game scrolls the bank. */
    private static final int EDGE_HOVER_PX = 6;

    /** How one in-tab move went: the method that worked, or why it did not. */
    static final class MoveOutcome {
        final String method;
        final String reason;

        private MoveOutcome(String method, String reason) {
            this.method = method;
            this.reason = reason;
        }

        static MoveOutcome ok(String method) {
            return new MoveOutcome(method, null);
        }

        static MoveOutcome fail(String reason) {
            return new MoveOutcome(null, reason);
        }

        boolean ok() {
            return method != null;
        }

        /** The player's own mouse or keys stopped the gesture, so the move was never really tried. */
        boolean humanInput() {
            return REASON_HUMAN.equals(reason);
        }

        @Override
        public String toString() {
            return ok() ? method : reason;
        }
    }

    static boolean humanInputActive() {
        return InputArbiter.isHuman();
    }

    /**
     * Moves one item inside the open tab with insert mode on, so it lands at {@code toSlot}.
     * Tries a plain drag when both slots fit in the window, then a drag that scrolls the bank
     * the way a player does while the item is held, then a chain of short in-window hops.
     */
    static MoveOutcome moveWithinTab(int fromSlot, int itemId, int toSlot) {
        if (fromSlot == toSlot) {
            return MoveOutcome.ok(METHOD_DIRECT);
        }
        if (!Rs2Bank.isOpen()) {
            return MoveOutcome.fail(REASON_BANK_CLOSED);
        }
        if (itemIdAtSlot(fromSlot) != itemId) {
            return MoveOutcome.fail(REASON_SOURCE_MOVED);
        }
        List<String> tried = new ArrayList<>();
        if (scrollToShow(fromSlot, toSlot) && itemInWindow(fromSlot, itemId) && itemInWindow(toSlot, -1)) {
            MoveOutcome direct = directDrag(fromSlot, itemId, toSlot, METHOD_DIRECT);
            if (direct.ok() || direct.humanInput()) {
                return direct;
            }
            tried.add(METHOD_DIRECT + ":" + direct.reason);
        }
        if (!Rs2Bank.isOpen()) {
            return MoveOutcome.fail(REASON_BANK_CLOSED);
        }
        if (itemIdAtSlot(fromSlot) == itemId) {
            MoveOutcome scroll = scrollDrag(fromSlot, itemId, toSlot);
            if (scroll.ok() || scroll.humanInput()) {
                return scroll;
            }
            tried.add(METHOD_SCROLL + ":" + scroll.reason);
        }
        if (!Rs2Bank.isOpen()) {
            return MoveOutcome.fail(REASON_BANK_CLOSED);
        }
        if (itemIdAtSlot(fromSlot) != itemId) {
            tried.add(REASON_SOURCE_MOVED);
            return MoveOutcome.fail(String.join(" ", tried));
        }
        MoveOutcome relay = relayDrag(fromSlot, itemId, toSlot);
        if (relay.ok() || relay.humanInput()) {
            return relay;
        }
        tried.add(METHOD_RELAY + ":" + relay.reason);
        return MoveOutcome.fail(String.join(" ", tried));
    }

    static int itemIdAtSlot(int slot) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget item = Rs2Bank.getItemWidget(slot);
            return item == null || item.isHidden() ? -1 : item.getItemId();
        }).orElse(-1);
    }

    static int containerIdAt(int slot) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ItemContainer container = Microbot.getClient().getItemContainer(InventoryID.BANK);
            if (container == null) {
                return -1;
            }
            Item item = container.getItem(slot);
            return item == null ? -1 : item.getId();
        }).orElse(-1);
    }

    /**
     * The drop shows after the server tick, so Fast mode's short wait would call good drags failed.
     * The widget redraws before the item container updates, and planning reads the container,
     * so both must show the item.
     */
    private static boolean landed(int itemId, int toSlot) {
        return waitUntil(() -> itemIdAtSlot(toSlot) == itemId && containerIdAt(toSlot) == itemId, 2500, true);
    }

    /** Waits until the widget and the item container agree on what is at {@code slot}. */
    static boolean awaitSync(int slot) {
        return waitUntil(() -> itemIdAtSlot(slot) == containerIdAt(slot), SYNC_WAIT_MS, true);
    }

    private static MoveOutcome landedOutcome(int itemId, int fromSlot, int toSlot, String method) {
        if (landed(itemId, toSlot)) {
            return MoveOutcome.ok(method);
        }
        if (humanInputActive()) {
            return MoveOutcome.fail(REASON_HUMAN);
        }
        BankSlot now = findById(itemId);
        if (now == null) {
            return MoveOutcome.fail("item-missing");
        }
        return MoveOutcome.fail(now.slot == fromSlot ? "did-not-move" : "landed-at-" + now.slot);
    }

    private static MoveOutcome directDrag(int fromSlot, int itemId, int toSlot, String method) {
        Rectangle window = itemWindow();
        Rectangle source = slotRect(fromSlot, itemId);
        Rectangle target = slotRect(toSlot, -1);
        if (!fullyInside(window, source)) {
            return MoveOutcome.fail("source-not-shown");
        }
        if (!fullyInside(window, target)) {
            return MoveOutcome.fail(REASON_TARGET_NOT_SHOWN);
        }
        if (humanInputActive()) {
            return MoveOutcome.fail(REASON_HUMAN);
        }
        dragBankItem(fromSlot, source, target);
        return landedOutcome(itemId, fromSlot, toSlot, method);
    }

    /**
     * Presses on the source, then scrolls the bank under the held item with the mouse wheel,
     * or by hovering at the window edge when the wheel does nothing, until the target slot is in
     * the window, then releases on it. When the target never shows, the item is released back
     * on its own slot so nothing is dropped on a random slot.
     */
    private static MoveOutcome scrollDrag(int fromSlot, int itemId, int toSlot) {
        if (!revealSlot(fromSlot, itemId)) {
            return MoveOutcome.fail("source-not-shown");
        }
        Rectangle window = itemWindow();
        Rectangle source = slotRect(fromSlot, itemId);
        if (!fullyInside(window, source)) {
            return MoveOutcome.fail("source-not-shown");
        }
        Point start = centerPoint(source);
        Point parking = new Point(window.x + window.width / 2, window.y + window.height / 2);
        Point[] cursor = {start};
        boolean[] onTarget = {false};
        String[] how = {REASON_TARGET_NOT_SHOWN};
        DragDelay saved = clearDragDelay(fromSlot);
        InputLoop.Result result;
        try {
            result = InputLoop.run(emit -> {
                emit.move(start.getX(), start.getY());
                Global.sleep(Rs2Random.betweenInclusive(40, 70));
                emit.press(start.getX(), start.getY(), MOUSE_BUTTON_LEFT);
                Global.sleep(Rs2Random.betweenInclusive(60, 90));
                Point nudge = new Point(start.getX() + 8, start.getY() + 6);
                glide(emit, start, nudge, 3);
                Global.sleep(Rs2Random.betweenInclusive(40, 60));
                glide(emit, nudge, parking, 6);
                cursor[0] = parking;

                if (wheelUntilShown(emit, cursor, window, toSlot)) {
                    how[0] = "wheel";
                    onTarget[0] = true;
                } else if (!bankAtLimit(directionToward(window, toSlot))
                        && edgeHoverUntilShown(emit, cursor, window, toSlot)) {
                    how[0] = "edge";
                    onTarget[0] = true;
                } else if (bankAtLimit(directionToward(window, toSlot))) {
                    how[0] = "target-unreachable";
                }
                if (onTarget[0]) {
                    onTarget[0] = settleOn(emit, cursor, window, toSlot);
                    if (!onTarget[0]) {
                        how[0] = "target-moved";
                    }
                }
                if (!onTarget[0]) {
                    scrollToShow(fromSlot, -1);
                    Rectangle back = slotRect(fromSlot, itemId);
                    Point end = back == null ? start : centerPoint(back);
                    glide(emit, cursor[0], end, 6);
                    cursor[0] = end;
                }
                Global.sleep(Rs2Random.betweenInclusive(70, 100));
                emit.release(cursor[0].getX(), cursor[0].getY(), MOUSE_BUTTON_LEFT);
            });
        } finally {
            restoreDragDelay(fromSlot, saved);
        }
        if (result != InputLoop.Result.COMPLETED) {
            return MoveOutcome.fail(humanInputActive() ? REASON_HUMAN : "gesture-aborted");
        }
        if (!onTarget[0]) {
            return MoveOutcome.fail(how[0]);
        }
        MoveOutcome outcome = landedOutcome(itemId, fromSlot, toSlot, METHOD_SCROLL + "/" + how[0]);
        return outcome.ok() ? outcome : MoveOutcome.fail(how[0] + "-" + outcome.reason);
    }

    /** -1 scroll up, 1 scroll down, 0 when the slot is fully in the window. */
    private static int directionToward(Rectangle window, int slot) {
        Rectangle rect = slotRect(slot, -1);
        if (rect == null || window == null) {
            return 0;
        }
        return scrollDirection(window, rect);
    }

    static int scrollDirection(Rectangle window, Rectangle item) {
        if (fullyInside(window, item)) {
            return 0;
        }
        return item.y < window.y + window.height / 2 ? -1 : 1;
    }

    private static boolean wheelUntilShown(InputLoop.Emit emit, Point[] cursor, Rectangle window, int slot) {
        long deadline = System.currentTimeMillis() + WHEEL_TIMEOUT_MS;
        int lastScroll = bankScrollY();
        long lastChange = System.currentTimeMillis();
        while (System.currentTimeMillis() < deadline) {
            int dir = directionToward(window, slot);
            if (dir == 0) {
                return slotRect(slot, -1) != null;
            }
            if (bankAtLimit(dir)) {
                return false;
            }
            emit.wheel(cursor[0].getX(), cursor[0].getY(), dir, 1);
            Global.sleep(Rs2Random.betweenInclusive(35, 55));
            int scroll = bankScrollY();
            if (scroll != lastScroll) {
                lastScroll = scroll;
                lastChange = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - lastChange > WHEEL_STALL_MS) {
                return false;
            }
        }
        return false;
    }

    private static boolean edgeHoverUntilShown(InputLoop.Emit emit, Point[] cursor, Rectangle window, int slot) {
        long deadline = System.currentTimeMillis() + EDGE_TIMEOUT_MS;
        int lastScroll = bankScrollY();
        long lastChange = System.currentTimeMillis();
        int lastDir = 0;
        while (System.currentTimeMillis() < deadline) {
            int dir = directionToward(window, slot);
            if (dir == 0) {
                return slotRect(slot, -1) != null;
            }
            if (bankAtLimit(dir)) {
                return false;
            }
            if (dir != lastDir) {
                Point edge = new Point(window.x + window.width / 2,
                        dir > 0 ? window.y + window.height - EDGE_HOVER_PX : window.y + EDGE_HOVER_PX);
                glide(emit, cursor[0], edge, 5);
                cursor[0] = edge;
                lastDir = dir;
                lastChange = System.currentTimeMillis();
            }
            Global.sleep(30);
            int scroll = bankScrollY();
            if (scroll != lastScroll) {
                lastScroll = scroll;
                lastChange = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - lastChange > EDGE_STALL_MS) {
                return false;
            }
        }
        return false;
    }

    /**
     * Glides onto the target and checks the cursor is still inside it, since the bank can keep
     * scrolling for a moment after the item leaves the window edge.
     */
    private static boolean settleOn(InputLoop.Emit emit, Point[] cursor, Rectangle window, int slot) {
        for (int attempt = 0; attempt < 4; attempt++) {
            awaitLayout(slot);
            Rectangle target = slotRect(slot, -1);
            if (!fullyInside(window, target)) {
                int dir = directionToward(window, slot);
                if (dir == 0) {
                    return false;
                }
                emit.wheel(cursor[0].getX(), cursor[0].getY(), dir, 1);
                Global.sleep(60);
                continue;
            }
            Point end = centerPoint(target);
            glide(emit, cursor[0], end, 6);
            cursor[0] = end;
            Global.sleep(40);
            Rectangle again = slotRect(slot, -1);
            if (again != null && again.contains(end.getX(), end.getY())) {
                return true;
            }
        }
        return false;
    }

    /** The bank is already scrolled as far as it goes in {@code dir}, so holding longer cannot help. */
    private static boolean bankAtLimit(int dir) {
        if (dir == 0) {
            return false;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget container = Microbot.getClient().getWidget(BANK_ITEM_CONTAINER_COMPONENT);
            return container != null && atScrollLimit(container.getScrollY(), container.getHeight(),
                    container.getScrollHeight(), dir);
        }).orElse(false);
    }

    private static int bankScrollY() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget container = Microbot.getClient().getWidget(BANK_ITEM_CONTAINER_COMPONENT);
            return container == null ? -1 : container.getScrollY();
        }).orElse(-1);
    }

    /**
     * Fallback: a chain of in-window drags. Each hop puts the item at the furthest slot toward
     * the target that is visible with it. A chain of inserts of one item ends in the same order
     * as a single insert, so the plan stays valid.
     */
    private static MoveOutcome relayDrag(int fromSlot, int itemId, int toSlot) {
        int current = fromSlot;
        for (int hop = 0; hop < MAX_RELAY_HOPS && current != toSlot; hop++) {
            if (!Rs2Bank.isOpen()) {
                return MoveOutcome.fail(REASON_BANK_CLOSED);
            }
            if (itemIdAtSlot(current) != itemId) {
                return MoveOutcome.fail(REASON_SOURCE_MOVED + "-hop" + hop);
            }
            if (scrollToShow(current, toSlot) && itemInWindow(current, itemId) && itemInWindow(toSlot, -1)) {
                MoveOutcome last = directDrag(current, itemId, toSlot, METHOD_RELAY);
                return last.ok() || last.humanInput() ? last : MoveOutcome.fail("hop" + hop + "-" + last.reason);
            }
            boolean down = toSlot > current;
            if (!scrollToEdge(current, down) || !itemInWindow(current, itemId)) {
                return MoveOutcome.fail("edge-scroll-hop" + hop);
            }
            int hopTarget = furthestVisibleToward(current, toSlot);
            if (hopTarget == current) {
                return MoveOutcome.fail("no-hop-target-hop" + hop);
            }
            MoveOutcome step = directDrag(current, itemId, hopTarget, METHOD_RELAY);
            if (!step.ok()) {
                return step.humanInput() ? step : MoveOutcome.fail("hop" + hop + "-" + step.reason);
            }
            current = hopTarget;
        }
        return current == toSlot ? MoveOutcome.ok(METHOD_RELAY) : MoveOutcome.fail("hops-exhausted");
    }

    private static int furthestVisibleToward(int from, int to) {
        Rectangle window = itemWindow();
        int step = to > from ? 1 : -1;
        int best = from;
        for (int slot = from + step; slot != to + step; slot += step) {
            if (!fullyInside(window, slotRect(slot, -1))) {
                break;
            }
            best = slot;
        }
        return best;
    }

    private static void glide(InputLoop.Emit emit, Point from, Point to, int steps) {
        for (int i = 1; i <= steps; i++) {
            int x = from.getX() + (to.getX() - from.getX()) * i / steps;
            int y = from.getY() + (to.getY() - from.getY()) * i / steps;
            emit.move(x, y);
            Global.sleep(Rs2Random.betweenInclusive(8, 14));
        }
    }

    /**
     * Scroll so {@code slot} is in the item window. With {@code otherSlot >= 0}, frame both slots
     * when they fit together; returns false when they do not. Returns once the game has drawn
     * the new scroll, so positions and clicks match.
     */
    private static boolean scrollToShow(int slot, int otherSlot) {
        boolean ok = applyScroll(slot, otherSlot, (rel, other, itemH, viewH, scrollH) -> other == null
                ? scrollYToShow(rel, itemH, viewH, scrollH)
                : scrollYToShowBoth(rel, other, itemH, viewH, scrollH));
        if (ok) {
            awaitLayout(slot);
        }
        return ok;
    }

    /** Puts the slot at the top edge when moving down, or the bottom edge when moving up. */
    private static boolean scrollToEdge(int slot, boolean slotAtTop) {
        boolean ok = applyScroll(slot, -1, (rel, other, itemH, viewH, scrollH) ->
                scrollYToEdge(rel, itemH, viewH, scrollH, slotAtTop));
        if (ok) {
            awaitLayout(slot);
        }
        return ok;
    }

    private interface ScrollRule {
        int scrollY(int relY, Integer otherRelY, int itemH, int viewH, int scrollH);
    }

    private static boolean applyScroll(int slot, int otherSlot, ScrollRule rule) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget container = Microbot.getClient().getWidget(BANK_ITEM_CONTAINER_COMPONENT);
            Widget item = Rs2Bank.getItemWidget(slot);
            if (container == null || container.isHidden() || item == null || item.isHidden()) {
                return false;
            }
            Integer otherRel = null;
            if (otherSlot >= 0) {
                Widget other = Rs2Bank.getItemWidget(otherSlot);
                if (other == null || other.isHidden()) {
                    return false;
                }
                otherRel = other.getRelativeY();
            }
            int y = rule.scrollY(item.getRelativeY(), otherRel, item.getHeight(),
                    container.getHeight(), container.getScrollHeight());
            if (y < 0) {
                return false;
            }
            if (container.getScrollY() != y) {
                container.setScrollY(y);
                Microbot.getClient().setVarcIntValue(BANK_SCROLL_VARC, y);
                Microbot.getClient().runScript(UPDATE_SCROLLBAR_SCRIPT, BANK_SCROLLBAR_COMPONENT,
                        BANK_ITEM_CONTAINER_COMPONENT, y);
            }
            return true;
        }).orElse(false);
    }

    static int maxScrollY(int viewH, int scrollH) {
        return Math.max(0, scrollH - viewH);
    }

    static int scrollYToShow(int relY, int itemH, int viewH, int scrollH) {
        return clamp(relY - (viewH - itemH) / 2, 0, maxScrollY(viewH, scrollH));
    }

    /** -1 when both items cannot be fully inside the window at once. */
    static int scrollYToShowBoth(int relA, int relB, int itemH, int viewH, int scrollH) {
        int top = Math.min(relA, relB);
        int bottom = Math.max(relA, relB) + itemH;
        if (bottom - top > viewH - ITEM_WINDOW_INSET * 2) {
            return -1;
        }
        return clamp((top + bottom) / 2 - viewH / 2, 0, maxScrollY(viewH, scrollH));
    }

    static int scrollYToEdge(int relY, int itemH, int viewH, int scrollH, boolean slotAtTop) {
        int margin = ITEM_WINDOW_INSET + 2;
        int y = slotAtTop ? relY - margin : relY + itemH + margin - viewH;
        return clamp(y, 0, maxScrollY(viewH, scrollH));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Container slot range of a tab: tabs 1-9 from the varbits, main tab after them. */
    static int[] tabRange(int tab) {
        int[] counts = readTabCounts();
        int before = 0;
        for (int i = 0; i < counts.length; i++) {
            if (i + 1 == tab) {
                return new int[]{before, before + counts[i]};
            }
            before += counts[i];
        }
        int size = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ItemContainer container = Microbot.getClient().getItemContainer(InventoryID.BANK);
            if (container == null) {
                return 0;
            }
            Item[] items = container.getItems();
            int end = items.length;
            while (end > 0 && (items[end - 1] == null || items[end - 1].getId() <= 0)) {
                end--;
            }
            return end;
        }).orElse(0);
        return new int[]{before, Math.max(before, size)};
    }

    /** Raw item ids in container order for {@code [start, end)}, -1 for empty slots. */
    static int[] containerIds(int start, int end) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ItemContainer container = Microbot.getClient().getItemContainer(InventoryID.BANK);
            int[] ids = new int[Math.max(0, end - start)];
            Item[] items = container == null ? new Item[0] : container.getItems();
            for (int i = 0; i < ids.length; i++) {
                int slot = start + i;
                ids[i] = slot < items.length && items[slot] != null ? items[slot].getId() : -1;
            }
            return ids;
        }).orElse(new int[0]);
    }

    private static int mainApproxCount() {
        return Math.max(0, Rs2Bank.bankItems().size() - sumTabCounts());
    }

    private static int sumTabCounts() {
        int sum = 0;
        for (int i = 1; i <= 9; i++) {
            sum += tabCount(i);
        }
        return sum;
    }

    private static BankSlot findById(int itemId) {
        return readSlots().stream()
                .filter(slot -> slot.id == itemId)
                .min(Comparator.comparingInt(slot -> slot.slot))
                .orElse(null);
    }

    private static int tabForItem(int itemId) {
        BankSlot item = findById(itemId);
        if (item == null) {
            return -1;
        }
        return tabForSlot(item.slot, readTabCounts());
    }

    private static int quantityFor(int itemId) {
        BankSlot item = findById(itemId);
        return item == null ? 0 : item.quantity;
    }

    private static String nameFor(int id) {
        if (id <= 0) {
            return null;
        }
        ItemComposition composition = Microbot.getClient().getItemDefinition(id);
        return composition == null ? null : composition.getName();
    }

    private static CanonicalItem canonicalItem(int id, String name) {
        ItemComposition composition = Microbot.getClient().getItemDefinition(id);
        int canonicalId = id;
        String canonicalName = name;
        if (composition != null && composition.getNote() != -1 && composition.getLinkedNoteId() > 0) {
            canonicalId = composition.getLinkedNoteId();
            ItemComposition unnoted = Microbot.getClient().getItemDefinition(canonicalId);
            if (unnoted != null && unnoted.getName() != null) {
                canonicalName = unnoted.getName();
            }
        }
        return new CanonicalItem(canonicalId, canonicalName);
    }

    private static int[] readTabCounts() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            int[] counts = new int[TAB_COUNT_VARBITS.length];
            for (int i = 0; i < TAB_COUNT_VARBITS.length; i++) {
                counts[i] = Microbot.getClient().getVarbitValue(TAB_COUNT_VARBITS[i]);
            }
            return counts;
        }).orElse(new int[TAB_COUNT_VARBITS.length]);
    }

    private static List<BankSlot> readSlots() {
        List<BankSlot> fromContainer = Microbot.getClientThread().runOnClientThreadOptional(
                BankTabMover::readContainerSlots).orElse(null);
        if (fromContainer != null) {
            return fromContainer;
        }
        return readFromBankItems();
    }

    private static List<BankSlot> readContainerSlots() {
        ItemContainer container = Microbot.getClient().getItemContainer(InventoryID.BANK);
        if (container == null) {
            return null;
        }
        Item[] items = container.getItems();
        List<BankSlot> slots = new ArrayList<>();
        for (int slot = 0; slot < items.length; slot++) {
            Item item = items[slot];
            if (item == null) {
                continue;
            }
            BankSlot built = buildSlot(item.getId(), slot, item.getQuantity());
            if (built != null) {
                slots.add(built);
            }
        }
        return slots;
    }

    private static List<BankSlot> readFromBankItems() {
        List<Rs2ItemModel> bankItems = new ArrayList<>(Rs2Bank.bankItems());
        bankItems.sort(Comparator.comparingInt(Rs2ItemModel::getSlot));
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            List<BankSlot> slots = new ArrayList<>();
            for (Rs2ItemModel item : bankItems) {
                BankSlot built = buildSlot(item.getId(), item.getSlot(), item.getQuantity());
                if (built != null) {
                    slots.add(built);
                }
            }
            return slots;
        }).orElse(List.of());
    }

    private static BankSlot buildSlot(int id, int slot, int quantity) {
        String name = nameFor(id);
        if (!includeInSnapshot(id, quantity, name)) {
            return null;
        }
        CanonicalItem canonical = canonicalItem(id, name);
        return new BankSlot(id, slot, quantity, name, canonical.id, canonical.name);
    }

    private static boolean inCanvas(Rectangle rectangle) {
        return rectangle != null && Rs2UiHelper.isRectangleWithinCanvas(rectangle);
    }

    /**
     * Locates the source slot for an item using ONLY its snapshot-recorded tab.
     * We deliberately do NOT search other tabs: every failed tab search opens that
     * tab visually (causing the cycling the user sees). If the snapshot is stale,
     * the caller must re-snapshot and retry.
     */
    private static SourceRef locateSource(BankSortItem item) {
        int tab = item.getCurrentTab();
        if (tab < 0) {
            tab = 0;
        }
        return locateInTab(item.getId(), tab);
    }

    private static SourceRef locateInTab(int itemId, int tab) {
        if (tab < 0 || !openTab(tab)) {
            return null;
        }
        int slot = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            for (Widget widget : Rs2Bank.getItems()) {
                if (widget.getItemId() == itemId && !widget.isHidden()) {
                    return widget.getIndex();
                }
            }
            return -1;
        }).orElse(-1);
        return slot < 0 ? null : new SourceRef(tab, slot);
    }

    private static final class BankSlot {
        private final int id;
        private final int slot;
        private final int quantity;
        private final String name;
        private final int classificationId;
        private final String classificationName;

        private BankSlot(int id, int slot, int quantity, String name, int classificationId, String classificationName) {
            this.id = id;
            this.slot = slot;
            this.quantity = quantity;
            this.name = name;
            this.classificationId = classificationId;
            this.classificationName = classificationName;
        }
    }

    private static final class DragDelay {
        private final int deadTime;
        private final int inventoryDelay;
        private final boolean hadWidget;

        private DragDelay(int deadTime, int inventoryDelay, boolean hadWidget) {
            this.deadTime = deadTime;
            this.inventoryDelay = inventoryDelay;
            this.hadWidget = hadWidget;
        }
    }

    private static final class NewTabTarget {
        private final int index;
        private final Rectangle bounds;

        private NewTabTarget(int index, Rectangle bounds) {
            this.index = index;
            this.bounds = bounds;
        }
    }

    private static final class SourceRef {
        private final int tab;
        private final int slot;

        private SourceRef(int tab, int slot) {
            this.tab = tab;
            this.slot = slot;
        }
    }

    private static final class CanonicalItem {
        private final int id;
        private final String name;

        private CanonicalItem(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
