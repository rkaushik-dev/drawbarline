package io.github.rkaushikdev.drawbarline.health;

/** How much a finding matters. */
public enum Level {
    GOOD("ok"),
    WARN("warn"),
    BAD("bad");

    private final String value;

    Level(String value) {
        this.value = value;
    }

    /** The word a finding is printed with: {@code ok}, {@code warn} or {@code bad}. */
    public String value() {
        return value;
    }

    /** Whether a finding at this level means a close should not be run. */
    public boolean stopsAClose() {
        return this == BAD;
    }
}
