package net.runelite.client.plugins.microbot.banksorter;

/**
 * Layout and placement choices from the config panel.
 * Defaults match the iron 8-tab bank, except clue items are kept together
 * and the teleport list includes the jewellery that used to fall through to combat.
 */
public final class OrganizeOptions {
    public enum Layout {
        IRON("Iron 8-tab"),
        TIGHT("Tight iron");

        private final String label;

        Layout(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum ClueHome {
        MAIN("Main tab"),
        CLUES("Clues tab");

        private final String label;

        ClueHome(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum JewelleryHome {
        SUPPLIES("Supplies"),
        COMBAT("Combat");

        private final String label;

        JewelleryHome(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Drinks {
        WITH_FOOD("With food"),
        OWN_BLOCK("Own block");

        private final String label;

        Drinks(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final OrganizeOptions DEFAULTS = new OrganizeOptions(
            Layout.IRON, ClueHome.MAIN, JewelleryHome.SUPPLIES, Drinks.WITH_FOOD);

    private static volatile OrganizeOptions current = DEFAULTS;

    private final Layout layout;
    private final ClueHome clueHome;
    private final JewelleryHome jewelleryHome;
    private final Drinks drinks;

    OrganizeOptions(Layout layout, ClueHome clueHome, JewelleryHome jewelleryHome, Drinks drinks) {
        this.layout = layout;
        this.clueHome = clueHome;
        this.jewelleryHome = jewelleryHome;
        this.drinks = drinks;
    }

    static OrganizeOptions defaults() {
        return DEFAULTS;
    }

    static OrganizeOptions current() {
        return current;
    }

    static void use(OrganizeOptions options) {
        current = options == null ? DEFAULTS : options;
    }

    static void reset() {
        current = DEFAULTS;
    }

    static OrganizeOptions from(BankSorterConfig config) {
        return new OrganizeOptions(
                config.layout(),
                config.clueHome(),
                config.teleportJewellery(),
                config.drinks());
    }

    OrganizeOptions withLayout(Layout layout) {
        return new OrganizeOptions(layout, clueHome, jewelleryHome, drinks);
    }

    OrganizeOptions withClues(ClueHome clueHome) {
        return new OrganizeOptions(layout, clueHome, jewelleryHome, drinks);
    }

    OrganizeOptions withJewellery(JewelleryHome jewelleryHome) {
        return new OrganizeOptions(layout, clueHome, jewelleryHome, drinks);
    }

    OrganizeOptions withDrinks(Drinks drinks) {
        return new OrganizeOptions(layout, clueHome, jewelleryHome, drinks);
    }

    boolean tight() {
        return layout == Layout.TIGHT;
    }

    boolean cluesOnMain() {
        return clueHome == ClueHome.MAIN;
    }

    boolean jewelleryOnSupplies() {
        return jewelleryHome == JewelleryHome.SUPPLIES;
    }

    boolean drinksWithFood() {
        return drinks == Drinks.WITH_FOOD;
    }

    @Override
    public String toString() {
        return "layout=" + layout
                + " clues=" + clueHome
                + " jewellery=" + jewelleryHome
                + " drinks=" + drinks;
    }
}
