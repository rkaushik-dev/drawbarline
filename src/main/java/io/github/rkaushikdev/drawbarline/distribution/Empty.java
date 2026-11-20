package io.github.rkaushikdev.drawbarline.distribution;

import io.github.rkaushikdev.drawbarline.fleet.CarKind;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import java.util.Objects;

/** A car standing empty somewhere on the railroad, free to go against an order. */
public record Empty(CarMark car, CarKind kind, String station) {

    public Empty {
        Objects.requireNonNull(car, "car");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(station, "station");
    }

    /** {@code CN 400100 at ESC}. */
    @Override
    public String toString() {
        return car + " at " + station;
    }
}
