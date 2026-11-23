package io.github.rkaushikdev.drawbarline.carhire;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.RateError;

/**
 * The ages of car a line of the schedule speaks for, both ends counted, in whole years since the car
 * was built.
 *
 * <p>A band of the published schedule and an owner's election are both filed against a range of
 * ages, and both used to check and compare their ranges in their own words. They are the same
 * range.
 *
 * @param from the youngest age covered
 * @param to the oldest age covered
 */
public record AgeRange(int from, int to) {

    public AgeRange {
        if (from < 0) {
            throw new RateError("an age band starts at zero or later", Details.of("from_age", from));
        }
        if (to < from) {
            throw new RateError("an age band ends after it starts", Details.of("from_age", from, "to_age", to));
        }
    }

    /** Whether a car of this age falls in the range. */
    public boolean covers(int age) {
        return from <= age && age <= to;
    }

    /** Whether the two ranges share an age. */
    public boolean overlaps(AgeRange other) {
        return from <= other.to && other.from <= to;
    }

    /** {@code 0-9}. */
    @Override
    public String toString() {
        return from + "-" + to;
    }
}
