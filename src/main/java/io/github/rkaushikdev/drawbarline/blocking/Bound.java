package io.github.rkaushikdev.drawbarline.blocking;

import io.github.rkaushikdev.drawbarline.marks.CarMark;
import java.util.Objects;

/** A car and the destination it is bound for, which is what decides its block. */
public record Bound(CarMark car, String destination) {

    public Bound {
        Objects.requireNonNull(car, "car");
        Objects.requireNonNull(destination, "destination");
    }
}
