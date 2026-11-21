package io.github.rkaushikdev.drawbarline.switching;

import io.github.rkaushikdev.drawbarline.marks.CarMark;
import java.util.Objects;

/** A car the yard has for a customer, and the track it is to be spotted on. */
public record Placement(CarMark car, String industry) {

    public Placement {
        Objects.requireNonNull(car, "car");
        Objects.requireNonNull(industry, "industry");
    }
}
