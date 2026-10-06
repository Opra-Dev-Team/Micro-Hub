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

    public enum Speed {
        FAST("Fast"),
        CAREFUL("Careful");

        private final String label;

        Speed(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum RawFishHome {
        GATHERING("Gathering"),
        WITH_FOOD("With food");

        private final String label;

        RawFishHome(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final OrganizeOptions DEFAULTS = new OrganizeOptions(
            Layout.IRON, ClueHome.MAIN, JewelleryHome.SUPPLIES, Drinks.WITH_FOOD,
            Speed.FAST, true, RawFishHome.GATHERING);

    private static volatile OrganizeOptions current = DEFAULTS;

    private final Layout layout;
    private final ClueHome clueHome;
    private final JewelleryHome jewelleryHome;
    private final Drinks drinks;
    private final Speed speed;
    private final boolean fileLeftovers;
    private final RawFishHome rawFishHome;

    OrganizeOptions(Layout layout, ClueHome clueHome, JewelleryHome jewelleryHome, Drinks drinks,
                    Speed speed, boolean fileLeftovers, RawFishHome rawFishHome) {
        this.layout = layout;
        this.clueHome = clueHome;
        this.jewelleryHome = jewelleryHome;
        this.drinks = drinks;
        this.speed = speed;
        this.fileLeftovers = fileLeftovers;
        this.rawFishHome = rawFishHome;
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
                config.drinks(),
                config.speed(),
                config.fileLeftovers(),
                config.rawFish());
    }

    OrganizeOptions withLayout(Layout layout) {
        return copy(layout, clueHome, jewelleryHome, drinks, speed, fileLeftovers, rawFishHome);
    }

    OrganizeOptions withClues(ClueHome clueHome) {
        return copy(layout, clueHome, jewelleryHome, drinks, speed, fileLeftovers, rawFishHome);
    }

    OrganizeOptions withJewellery(JewelleryHome jewelleryHome) {
        return copy(layout, clueHome, jewelleryHome, drinks, speed, fileLeftovers, rawFishHome);
    }

    OrganizeOptions withDrinks(Drinks drinks) {
        return copy(layout, clueHome, jewelleryHome, drinks, speed, fileLeftovers, rawFishHome);
    }

    OrganizeOptions withSpeed(Speed speed) {
        return copy(layout, clueHome, jewelleryHome, drinks, speed, fileLeftovers, rawFishHome);
    }

    OrganizeOptions withLeftovers(boolean fileLeftovers) {
        return copy(layout, clueHome, jewelleryHome, drinks, speed, fileLeftovers, rawFishHome);
    }

    OrganizeOptions withRawFish(RawFishHome rawFishHome) {
        return copy(layout, clueHome, jewelleryHome, drinks, speed, fileLeftovers, rawFishHome);
    }

    private OrganizeOptions copy(Layout layout, ClueHome clueHome, JewelleryHome jewelleryHome, Drinks drinks,
                                 Speed speed, boolean fileLeftovers, RawFishHome rawFishHome) {
        return new OrganizeOptions(layout, clueHome, jewelleryHome, drinks, speed, fileLeftovers, rawFishHome);
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

    boolean fast() {
        return speed == Speed.FAST;
    }

    boolean fileLeftovers() {
        return fileLeftovers;
    }

    boolean rawFishWithFood() {
        return rawFishHome == RawFishHome.WITH_FOOD;
    }

    @Override
    public String toString() {
        return "layout=" + layout
                + " clues=" + clueHome
                + " jewellery=" + jewelleryHome
                + " drinks=" + drinks
                + " speed=" + speed
                + " leftovers=" + fileLeftovers
                + " rawFish=" + rawFishHome;
    }
}
