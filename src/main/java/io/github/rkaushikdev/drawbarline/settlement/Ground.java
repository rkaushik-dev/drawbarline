package io.github.rkaushikdev.drawbarline.settlement;

/** What a road says is wrong with a statement it is disputing. */
public enum Ground {
    CAR_NOT_OURS("the car was never on their rail"),
    HOURS("the hours do not agree with their record"),
    RATE("the rate applied is not the one in effect"),
    MILEAGE("the miles do not agree"),
    REPAIR("the repair card is under exception"),
    DUPLICATE("billed twice"),
    OPENING_BALANCE("the opening balance does not agree");

    private final String text;

    Ground(String text) {
        this.text = text;
    }

    /** The ground as the dispute states it. */
    public String text() {
        return text;
    }
}
