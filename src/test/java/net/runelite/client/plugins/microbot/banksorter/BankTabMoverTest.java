package net.runelite.client.plugins.microbot.banksorter;

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankTabMoverTest {

    @Test
    void tabForSlotIgnoresPlaceholderHolesInFilteredOrdinal() {
        // Tab 1 varbit is 20 (10 real + 10 placeholders). Tab 2 is 10.
        int[] tabCounts = {20, 10, 0, 0, 0, 0, 0, 0, 0};

        assertEquals(1, BankTabMover.tabForSlot(0, tabCounts));
        assertEquals(1, BankTabMover.tabForSlot(19, tabCounts));
        // First real item of tab 2 lives at container slot 20.
        // Filtered ordinal 10 would have been wrongly assigned to tab 1.
        assertEquals(2, BankTabMover.tabForSlot(20, tabCounts));
        assertEquals(2, BankTabMover.tabForSlot(29, tabCounts));
        assertEquals(0, BankTabMover.tabForSlot(30, tabCounts));
    }

    @Test
    void placeholderSlotsAreKeptAndFillersAreDropped() {
        assertTrue(BankTabMover.includeInSnapshot(385, 0, "Shark"));
        assertTrue(BankTabMover.includeInSnapshot(385, 1, "Shark"));
        assertFalse(BankTabMover.includeInSnapshot(BankTabMover.BANK_FILLER_ID, 1, "Bank filler"));
        assertFalse(BankTabMover.includeInSnapshot(BankTabMover.PLACEHOLDER_ID, 0, "Placeholder"));
        assertFalse(BankTabMover.includeInSnapshot(-1, 0, null));
        assertFalse(BankTabMover.includeInSnapshot(385, 0, "  "));
    }

    @Test
    void itemAboveTheWindowIsNotDraggable() {
        Rectangle window = new Rectangle(100, 200, 400, 500);
        Rectangle above = new Rectangle(120, 140, 36, 32);

        assertFalse(BankTabMover.fullyInside(window, above));
    }

    @Test
    void negativeYIsNotDraggable() {
        Rectangle window = new Rectangle(100, 200, 400, 500);
        Rectangle aboveCanvas = new Rectangle(120, -20, 36, 32);

        assertFalse(BankTabMover.fullyInside(window, aboveCanvas));
    }

    @Test
    void itemFullyInsideTheWindowIsDraggable() {
        Rectangle window = new Rectangle(100, 200, 400, 500);
        Rectangle item = new Rectangle(140, 240, 36, 32);

        assertTrue(BankTabMover.fullyInside(window, item));
    }

    @Test
    void zeroTabsPutsNewTabOnChild11() {
        List<String[]> actions = tabBar(0);

        assertEquals(10, BankTabMover.dynamicChildWithAction(actions, "View all items"));
        assertEquals(11, BankTabMover.newTabDynamicIndex(actions, 0));
    }

    @Test
    void oneRealTabShiftsNewTabToChild12() {
        List<String[]> actions = tabBar(1);

        assertEquals(11, BankTabMover.dynamicChildWithAction(actions, "Collapse tab"));
        assertEquals(12, BankTabMover.newTabDynamicIndex(actions, 1));
    }

    @Test
    void missingNewTabActionFallsBackToSlotAfterLastRealTab() {
        List<String[]> actions = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            actions.add(null);
        }

        assertEquals(11, BankTabMover.newTabDynamicIndex(actions, 0));
        assertEquals(14, BankTabMover.newTabDynamicIndex(actions, 3));
        assertEquals(BankTabMover.BANK_TAB_CONTAINER_DYNAMIC_MAIN_INDEX + 1,
                BankTabMover.newTabDynamicIndex(null, 0));
    }

    @Test
    void scrollCentersSlotAndClampsToContent() {
        // Window 300 high, content 2000 high, items 32 high.
        assertEquals(1000 - 134, BankTabMover.scrollYToShow(1000, 32, 300, 2000));
        assertEquals(0, BankTabMover.scrollYToShow(20, 32, 300, 2000));
        assertEquals(1700, BankTabMover.scrollYToShow(1980, 32, 300, 2000));
        assertEquals(0, BankTabMover.scrollYToShow(100, 32, 300, 200));
    }

    @Test
    void bothSlotsFramedOnlyWhenTheyFit() {
        int y = BankTabMover.scrollYToShowBoth(500, 700, 32, 300, 2000);
        assertTrue(y <= 500 - BankTabMover.ITEM_WINDOW_INSET);
        assertTrue(y + 300 >= 732 + BankTabMover.ITEM_WINDOW_INSET);
        assertEquals(-1, BankTabMover.scrollYToShowBoth(100, 1200, 32, 300, 2000));
    }

    @Test
    void edgeScrollPutsSlotAgainstTheRequestedEdge() {
        assertEquals(1000 - 6, BankTabMover.scrollYToEdge(1000, 32, 300, 2000, true));
        assertEquals(1000 + 32 + 6 - 300, BankTabMover.scrollYToEdge(1000, 32, 300, 2000, false));
    }

    @Test
    void itemFlushWithTheWindowEdgesIsInside() {
        Rectangle window = new Rectangle(100, 200, 400, 500);
        assertTrue(BankTabMover.fullyInside(window, new Rectangle(151, 200, 36, 32)));
        assertTrue(BankTabMover.fullyInside(window, new Rectangle(151, 668, 36, 32)));
        assertFalse(BankTabMover.fullyInside(window, new Rectangle(151, 199, 36, 32)));
        assertFalse(BankTabMover.fullyInside(window, new Rectangle(151, 669, 36, 32)));
    }

    @Test
    void scrollLimitStopsAtTopAndBottom() {
        assertTrue(BankTabMover.atScrollLimit(0, 300, 2000, -1));
        assertFalse(BankTabMover.atScrollLimit(0, 300, 2000, 1));
        assertTrue(BankTabMover.atScrollLimit(1700, 300, 2000, 1));
        assertFalse(BankTabMover.atScrollLimit(1700, 300, 2000, -1));
        assertTrue(BankTabMover.atScrollLimit(0, 300, 200, 1));
        assertFalse(BankTabMover.atScrollLimit(500, 300, 2000, 0));
    }

    @Test
    void slotRectFollowsTheContainerScroll() {
        Rectangle top = BankTabMover.slotRect(100, 80, 0, 0, 48, 36, 36, 32);
        assertEquals(new Rectangle(148, 116, 36, 32), top);
        Rectangle scrolled = BankTabMover.slotRect(100, 80, 0, 500, 48, 536, 36, 32);
        assertEquals(new Rectangle(148, 116, 36, 32), scrolled);
        Rectangle above = BankTabMover.slotRect(100, 80, 0, 500, 48, 36, 36, 32);
        assertEquals(80 + 36 - 500, above.y);
    }

    @Test
    void scrollDirectionPointsTowardTheHiddenSlot() {
        Rectangle window = new Rectangle(100, 80, 400, 300);
        assertEquals(0, BankTabMover.scrollDirection(window, new Rectangle(150, 200, 36, 32)));
        assertEquals(-1, BankTabMover.scrollDirection(window, new Rectangle(150, 40, 36, 32)));
        assertEquals(1, BankTabMover.scrollDirection(window, new Rectangle(150, 370, 36, 32)));
        assertEquals(1, BankTabMover.scrollDirection(window, new Rectangle(150, 2000, 36, 32)));
    }

    /**
     * Live bank tab bar: ten slot backgrounds, then the main icon, then one icon per real tab,
     * then the plus button.
     */
    private static List<String[]> tabBar(int realTabs) {
        List<String[]> actions = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            actions.add(null);
        }
        actions.add(new String[]{"View all items", "Remove placeholders"});
        for (int i = 0; i < realTabs; i++) {
            actions.add(new String[]{"Collapse tab " + (i + 1)});
        }
        actions.add(new String[]{"New tab"});
        return actions;
    }
}
