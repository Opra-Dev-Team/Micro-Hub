package net.runelite.client.plugins.microbot.flaxpicker;

public enum PickSpeed {
    SPAM("Spam pick"),
    FAST("Fast click"),
    NORMAL("Normal click");

    private final String label;

    PickSpeed(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
