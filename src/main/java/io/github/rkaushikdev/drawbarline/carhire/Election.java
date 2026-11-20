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
        if (fromAge < 0) {
            throw new RateError("an age band starts at zero or later", Details.of("from_age", fromAge));
        }
        if (toAge < fromAge) {
            throw new RateError("an age band ends after it starts", Details.of("from_age", fromAge, "to_age", toAge));
        }
        if (hourlyTenths < 0) {
            throw new RateError("an elected rate is not negative", Details.of("elected", hourlyTenths));
        }
    }

    /** Whether a car of this age falls in the band, both ends counted. */
    public boolean coversAge(int age) {
        return fromAge <= age && age <= toAge;
    }

    /** {@code L351 0-9 at 390.0c from 2024-07-01}. */
    @Override
    public String toString() {
        double cents = hourlyTenths / (double) Rate.TENTHS_OF_CENT_PER_CENT;
        return typeCode + " " + fromAge + "-" + toAge + " at " + Numbers.fixed(cents, 1) + "c from " + effective;
    }
}
