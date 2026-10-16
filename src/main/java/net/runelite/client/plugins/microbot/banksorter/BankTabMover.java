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
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.function.BooleanSupplier;
import java.util.Comparator;
import java.util.List;

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

    static boolean openTab(int tabIndex) {
        if (Rs2Bank.getCurrentTab() == tabIndex) {
            return true;
        }
        boolean opened = Microbot.getClientThread().runOnClientThreadOptional(() ->
                tabIndex == 0 ? Rs2Bank.openMainTab() : Rs2Bank.openTab(tabIndex)).orElse(false);
        if (!opened) {
            return false;
        }
        return Global.sleepUntil(() -> Rs2Bank.getCurrentTab() == tabIndex, 2500);
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

    static List<BankSortItem> snapshot() {
        List<BankSlot> slots = readSlots();
        int[] tabCounts = readTabCounts();
        List<BankSortItem> items = new ArrayList<>();
        for (BankSlot slot : slots) {
            items.add(new BankSortItem(
                    slot.id,
                    slot.quantity == 0 ? slot.name + " (placeholder)" : slot.name,
                    slot.slot,
                    slot.quantity,
                    tabForSlot(slot.slot, tabCounts),
                    slot.classificationId,
                    slot.classificationName));
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
        SourceRef sourceRef = locateSource(item);
        if (sourceRef == null) {
            Microbot.log("Could not locate visible source widget for " + item.getOriginalName());
            return false;
        }
        if (sourceRef.tab == destTab) {
            return true;
        }
        if (!revealSlot(sourceRef.slot, item.getId())) {
            return false;
        }
        Rectangle source = itemBounds(sourceRef.slot, item.getId(), true);
        Rectangle target = destTabBounds(destTab);
        if (!fullyInside(itemWindow(), source) || !inCanvas(target)) {
            Microbot.log("Exact source or destination bounds unavailable for " + item.getOriginalName()
                    + " source=" + source + " target=" + target);
            return false;
        }
        int beforeDest = destTab == 0 ? mainApproxCount() : tabCount(destTab);
        int beforeSource = sourceRef.tab > 0 ? tabCount(sourceRef.tab) : 0;
        int qty = quantityFor(item.getId());
        dragBankItem(sourceRef.slot, source, target);
        int src = sourceRef.tab;
        return waitUntil(() -> {
            if (quantityFor(item.getId()) != qty) {
                return false;
            }
            if (destTab > 0 && tabCount(destTab) <= beforeDest) {
                return false;
            }
            if (src > 0 && destTab != src && tabCount(src) >= beforeSource) {
                return false;
            }
            return tabForItem(item.getId()) == destTab;
        }, 5000);
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
                        + " slot=" + sourceRef.slot + " (attempt " + attempt + ")");
                continue;
            }
            Rectangle source = itemBounds(sourceRef.slot, item.getId(), true);
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
     */
    static boolean fullyInside(Rectangle container, Rectangle item) {
        if (container == null || item == null || item.width <= 0 || item.height <= 0
                || container.width <= ITEM_WINDOW_INSET * 2 || container.height <= ITEM_WINDOW_INSET * 2) {
            return false;
        }
        int left = container.x + ITEM_WINDOW_INSET;
        int top = container.y + ITEM_WINDOW_INSET;
        int right = container.x + container.width - ITEM_WINDOW_INSET;
        int bottom = container.y + container.height - ITEM_WINDOW_INSET;
        return item.x >= left
                && item.y >= top
                && item.x + item.width <= right
                && item.y + item.height <= bottom;
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

    private static Rectangle itemBounds(int slot, int expectedItemId, boolean requireVisible) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget item = Rs2Bank.getItemWidget(slot);
            if (item == null || item.getItemId() != expectedItemId
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
        if (!scrollToSlot(slot)) {
            return false;
        }
        Global.sleep(OrganizeOptions.current().fast() ? 100 : 200);
        return itemInWindow(slot, itemId);
    }

    private static boolean itemInWindow(int slot, int itemId) {
        return fullyInside(itemWindow(), itemBounds(slot, itemId, true));
    }

    private static boolean scrollToSlot(int slot) {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Rs2Bank.scrollBankToSlot(slot)).orElse(false);
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
