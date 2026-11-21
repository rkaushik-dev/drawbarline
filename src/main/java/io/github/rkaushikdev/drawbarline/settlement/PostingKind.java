package io.github.rkaushikdev.drawbarline.settlement;

/** What a line on a road's account is for. */
public enum PostingKind {
    CAR_HIRE("car hire"),
    MILEAGE("mileage"),
    DEMURRAGE("demurrage"),
    REPAIRS("repairs"),
    RECLAIM("reclaim"),
    ADJUSTMENT("adjustment"),
    PAYMENT("payment");

    private final String text;

    PostingKind(String text) {
        this.text = text;
    }

    /** The kind as a statement line names it. */
    public String text() {
        return text;
    }
}
