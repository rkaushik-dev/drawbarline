package io.github.rkaushikdev.drawbarline.carhire;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.RateError;
import io.github.rkaushikdev.drawbarline.fleet.CarTypeCode;
import io.github.rkaushikdev.drawbarline.util.Numbers;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One owner taking less than the schedule for one age band of one car type, from a date.
 *
 * @param hourlyTenths the elected hourly rate, in tenths of a cent an hour
 */
public record Election(CarTypeCode typeCode, int fromAge, int toAge, LocalDate effective, long hourlyTenths) {

    public Election {
        Objects.requireNonNull(typeCode, "typeCode");
        Objects.requireNonNull(effective, "effective");
        new AgeRange(fromAge, toAge);
        if (hourlyTenths < 0) {
            throw new RateError("an elected rate is not negative", Details.of("elected", hourlyTenths));
        }
    }

    /** The ages the election speaks for. */
    public AgeRange ages() {
        return new AgeRange(fromAge, toAge);
    }

    /** Whether a car of this age falls in the band, both ends counted. */
    public boolean coversAge(int age) {
        return ages().covers(age);
    }

    /** {@code L351 0-9 at 390.0c from 2024-07-01}. */
    @Override
    public String toString() {
        double cents = hourlyTenths / (double) Rate.TENTHS_OF_CENT_PER_CENT;
        return typeCode + " " + ages() + " at " + Numbers.fixed(cents, 1) + "c from " + effective;
    }
}
