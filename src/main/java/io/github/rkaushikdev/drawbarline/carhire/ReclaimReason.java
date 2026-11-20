package io.github.rkaushikdev.drawbarline.carhire;

/**
 * Why hours come back off a charge, by the rule they come back under.
 *
 * <p>The rule numbers travel on the statement, because a connection disputing a reclaim disputes
 * it by rule, so the reason is an enumeration carrying its number rather than a note somebody typed.
 */
public enum ReclaimReason {
    REPAIRS(5, "held for repairs the owner answers for"),
    SHIPPER_DELAY(15, "held under load past the reclaim threshold"),
    EMBARGO(24, "held under embargo");

    private final int number;
    private final String text;

    ReclaimReason(int number, String text) {
        this.number = number;
        this.text = text;
    }

    /** The rule number. */
    public int value() {
        return number;
    }

    /** {@code rule 5}. */
    public String rule() {
        return "rule " + number;
    }

    public String text() {
        return text;
    }
}
