package io.github.rkaushikdev.drawbarline.switching;

import io.github.rkaushikdev.drawbarline.marks.CarMark;
import java.util.Objects;

/** One line of a switch list: a car, what is done with it, the track, and why when it is not done. */
public record Move(CarMark car, MoveKind kind, String industry, String note) {

    public Move {
        Objects.requireNonNull(car, "car");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(industry, "industry");
        Objects.requireNonNull(note, "note");
    }

    /** A move that needs no note. */
    public Move(CarMark car, MoveKind kind, String industry) {
        this(car, kind, industry, "");
    }

    /** {@code place CN 400100 ESCPAP1}, with the note in brackets when there is one. */
    @Override
    public String toString() {
        String tail = note.isEmpty() ? "" : " (" + note + ")";
        return kind.text() + " " + car + " " + industry + tail;
    }
}
