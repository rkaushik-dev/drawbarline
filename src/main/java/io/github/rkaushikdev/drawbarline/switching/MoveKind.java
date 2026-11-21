package io.github.rkaushikdev.drawbarline.switching;

/** What the crew does with one car on a switch list. */
public enum MoveKind {
    PULL("pull"),
    PLACE("place"),
    CONSTRUCTIVE("constructive"),
    NOT_SWITCHED("not switched today"),
    STOPPED("stopped");

    private final String text;

    MoveKind(String text) {
        this.text = text;
    }

    /** How the move reads on the list. */
    public String text() {
        return text;
    }

    /** Whether the crew actually touches the car. */
    public boolean worked() {
        return this == PULL || this == PLACE;
    }
}
