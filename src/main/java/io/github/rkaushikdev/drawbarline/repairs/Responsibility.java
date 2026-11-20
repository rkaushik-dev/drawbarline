package io.github.rkaushikdev.drawbarline.repairs;

/**
 * Who a repair bill goes to.
 *
 * <p>Wear is the owner's. Damage is whoever was handling the car. Everything else is argument
 * about which of the two a particular crack is.
 */
public enum Responsibility {
    OWNER("owner"),
    HANDLING_LINE("handling line"),
    THIRD_PARTY("third party"),
    JOINT("joint");

    private final String text;

    Responsibility(String text) {
        this.text = text;
    }

    /** The party as a card prints it. */
    public String text() {
        return text;
    }

    /** Whether work answered for by this party goes on the owner's bill: wear and joint findings. */
    public boolean billableToOwner() {
        return this == OWNER || this == JOINT;
    }
}
