package io.github.rkaushikdev.drawbarline.distribution;

import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.mileage.Mileage;
import java.util.Objects;

/** One empty against one order: the car, where it stands, and where it has to go. */
public record Assignment(int order, CarMark car, String fromStation, String toStation) {

    public Assignment {
        Objects.requireNonNull(car, "car");
        Objects.requireNonNull(fromStation, "fromStation");
        Objects.requireNonNull(toStation, "toStation");
    }

    /** How far the empty has to run, in tenths of a mile along the track. */
    public int tenths() {
        return Mileage.tenthsBetween(fromStation, toStation);
    }

    /** {@code CN 400100 PWR to ESC for order 1}. */
    @Override
    public String toString() {
        return car + " " + fromStation + " to " + toStation + " for order " + order;
    }
}
