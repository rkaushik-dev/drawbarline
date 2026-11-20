package io.github.rkaushikdev.drawbarline.carhire;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.RateError;
import io.github.rkaushikdev.drawbarline.fleet.CarTypeCode;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A rate for one type of car within an age range, in force from a date.
 *
 * <p>A new car earns more than a forty year old one and the steps are written into the schedule, so
 * a band covers the car type code and the ages from {@code fromAge} to {@code toAge}, both counted.
 */
public record Band(CarTypeCode typeCode, int fromAge, int toAge, LocalDate effective, Rate rate) {

    public Band {
        Objects.requireNonNull(typeCode, "typeCode");
        Objects.requireNonNull(effective, "effective");
        Objects.requireNonNull(rate, "rate");
        if (fromAge < 0) {
            throw new RateError("an age band starts at zero or later", Details.of("from_age", fromAge));
        }
        if (toAge < fromAge) {
            throw new RateError("an age band ends after it starts", Details.of("from_age", fromAge, "to_age", toAge));
        }
    }

    /** Whether a car of this age falls in the band. */
    public boolean coversAge(int age) {
        return fromAge <= age && age <= toAge;
    }

    boolean agesOverlap(Band other) {
        return fromAge <= other.toAge && other.fromAge <= toAge;
    }
}
