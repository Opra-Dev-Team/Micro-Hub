package net.runelite.client.plugins.microbot.flaxpicker;

public enum FlaxMode {
    PICK_ONLY("Pick flax only"),
    BOW_STRING("Bow strings"),
    LINEN_YARN("Linen yarn");

    private final String label;

    FlaxMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
