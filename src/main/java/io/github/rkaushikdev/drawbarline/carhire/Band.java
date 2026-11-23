package io.github.rkaushikdev.drawbarline.carhire;

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
        new AgeRange(fromAge, toAge);
    }

    /** The ages the band speaks for. */
    public AgeRange ages() {
        return new AgeRange(fromAge, toAge);
    }

    /** Whether a car of this age falls in the band. */
    public boolean coversAge(int age) {
        return ages().covers(age);
    }
}
