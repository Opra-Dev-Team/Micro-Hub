package net.runelite.client.plugins.microbot.banksorter;

import org.junit.jupiter.api.Test;

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
}
