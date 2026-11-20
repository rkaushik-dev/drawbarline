package io.github.rkaushikdev.drawbarline.clearance;

import io.github.rkaushikdev.drawbarline.measure.Length;

/**
 * The published outlines every railroad in interchange agrees to clear, smallest first. A car
 * inside plate C goes anywhere on this railroad without anybody thinking about it, which is the
 * entire point of having plates.
 */
public enum Plate {
    B("B", Length.of(15, 1), Length.of(10, 8)),
    C("C", Length.of(15, 6), Length.of(10, 8)),
    E("E", Length.of(15, 9), Length.of(10, 8)),
    F("F", Length.of(17, 0), Length.of(10, 8)),
    H("H", Length.of(20, 2), Length.of(10, 8));

    private final String value;
    private final Length height;
    private final Length width;

    Plate(String value, Length height, Length width) {
        this.value = value;
        this.height = height;
        this.width = width;
    }

    public String value() {
        return value;
    }

    public Length height() {
        return height;
    }

    public Length width() {
        return width;
    }

    /** Whether a load fits inside the outline. */
    public boolean holds(Dimensions load) {
        return load.height().compareTo(height) <= 0 && load.width().compareTo(width) <= 0;
    }
}
