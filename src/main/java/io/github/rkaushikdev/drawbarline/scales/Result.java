package io.github.rkaushikdev.drawbarline.scales;

/** How a certification test on a track scale came out. */
public enum Result {
    PASSED("passed"),
    FAILED("failed"),
    ADJUSTED("adjusted and passed");

    private final String value;

    Result(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /** Whether the scale may be weighed on after this test. */
    public boolean usable() {
        return this != FAILED;
    }
}
