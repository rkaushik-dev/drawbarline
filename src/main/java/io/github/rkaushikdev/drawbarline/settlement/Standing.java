package io.github.rkaushikdev.drawbarline.settlement;

/** Where a dispute stands: open until it is settled or the road withdraws it. */
public enum Standing {
    OPEN("open"),
    SETTLED("settled"),
    WITHDRAWN("withdrawn");

    private final String value;

    Standing(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
