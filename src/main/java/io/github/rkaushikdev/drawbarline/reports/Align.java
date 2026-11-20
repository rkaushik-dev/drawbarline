package io.github.rkaushikdev.drawbarline.reports;

/** Which side of its column a value sits on: text left, numbers and money right. */
public enum Align {
    LEFT("left"),
    RIGHT("right");

    private final String value;

    Align(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
