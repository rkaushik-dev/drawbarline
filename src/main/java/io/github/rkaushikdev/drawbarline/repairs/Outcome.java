package io.github.rkaushikdev.drawbarline.repairs;

/** Where an exception against a repair card ended up. */
public enum Outcome {
    OPEN("open"),
    ALLOWED("allowed in full"),
    PART_ALLOWED("allowed in part"),
    DECLINED("declined"),
    ARBITRATION("gone to arbitration");

    private final String value;

    Outcome(String value) {
        this.value = value;
    }

    /** The outcome as a resolution prints it. */
    public String value() {
        return value;
    }
}
