package net.runelite.client.plugins.microbot.banksorter;

public enum BankHomeTab {
    MAIN(0, "Main"),
    SUPPLIES(1, "Supplies"),
    COMBAT(2, "Combat"),
    GATHERING(3, "Gathering Materials"),
    PRODUCTION(4, "Production Tools"),
    RUNES(5, "Runes / Magic"),
    FARMING(6, "Farming / Herblore"),
    CLUES(7, "Clues / Uniques"),
    JUNK(8, "Misc / Junk");

    public final int index;
    public final String label;

    BankHomeTab(int index, String label) {
        this.index = index;
        this.label = label;
    }

    public static BankHomeTab fromIndex(int index) {
        for (BankHomeTab tab : values()) {
            if (tab.index == index) {
                return tab;
            }
        }
        return JUNK;
    }
}
