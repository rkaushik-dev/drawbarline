package io.github.rkaushikdev.drawbarline.repairs;

/** What an owner says is wrong with a line of a repair card. */
public enum Ground {
    PRICE("priced against the wrong master line"),
    RESPONSIBILITY("billed to the wrong party"),
    DEPRECIATION("depreciation not allowed for"),
    DUPLICATE("already billed on another card"),
    NOT_OUR_CAR("the car is not ours to answer for"),
    NO_AUTHORITY("the work was never authorised");

    private final String text;

    Ground(String text) {
        this.text = text;
    }

    /** The ground as an exception states it. */
    public String text() {
        return text;
    }
}
