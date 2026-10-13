package net.runelite.client.plugins.microbot.sweetcornchef;

public enum SeedHandling {
    DROP("Drop seeds"),
    KEEP("Keep seeds");

    private final String label;

    SeedHandling(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
