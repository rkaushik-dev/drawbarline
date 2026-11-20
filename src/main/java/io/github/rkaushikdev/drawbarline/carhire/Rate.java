package io.github.rkaushikdev.drawbarline.carhire;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.RateError;
import io.github.rkaushikdev.drawbarline.util.Numbers;

/**
 * One line of the car hire schedule.
 *
 * <p>A rate has two halves. The hourly half is what the car earns for every hour it sits on a
 * foreign road, quoted in tenths of a cent an hour, and the mileage half is what it earns for every
 * mile it runs, quoted in mills a mile. Money at these resolutions does not fit in cents, so both
 * halves stay integers in their own unit and only become money once, on a whole span.
 *
 * @param hourlyTenths tenths of a cent an hour
 * @param mileageMills mills a mile
 */
public record Rate(long hourlyTenths, long mileageMills) {

    public static final int TENTHS_OF_CENT_PER_CENT = 10;
    public static final int MILLS_PER_CENT = 10;

    public Rate {
        if (hourlyTenths < 0 || mileageMills < 0) {
            throw new RateError("a rate is not negative", Details.of("hourly", hourlyTenths, "mileage", mileageMills));
        }
    }

    /** The hourly half in cents, for a report. */
    public double hourlyCents() {
        return hourlyTenths / (double) TENTHS_OF_CENT_PER_CENT;
    }

    /** The mileage half in cents, for a report. */
    public double mileageCents() {
        return mileageMills / (double) MILLS_PER_CENT;
    }

    /** {@code 463.0c an hour and 121.0c a mile}. */
    @Override
    public String toString() {
        return Numbers.fixed(hourlyCents(), 1) + "c an hour and " + Numbers.fixed(mileageCents(), 1) + "c a mile";
    }
}
