package io.github.rkaushikdev.drawbarline.carhire;

import io.github.rkaushikdev.drawbarline.fleet.Car;
import java.time.LocalDate;
import java.util.Optional;

/**
 * What a car earns an hour once its owner's elections and its age are taken into account.
 *
 * <p>The published schedule is what a car earns by default, and an owner may file to take less.
 * The harder rule is quieter: a car past its fifty years is not in interchange service, and a car
 * not in interchange service earns no car hire at all, whatever the schedule says. Roads have gone
 * on billing for condemned cars for months because nobody wired that rule into the rate lookup, so
 * it is wired in here rather than left to whoever reads a report.
 */
public final class Deprescription {

    private Deprescription() {}

    /** What the car earns on a day, or nothing at all when it is out of interchange service. */
    public static Optional<Rate> payable(Car car, RateTable schedule, Elections elections, LocalDate on) {
        if (!car.eligibleOn(on)) {
            return Optional.empty();
        }
        int age = car.ageOn(on);
        Rate published = schedule.lookup(car.typeCode(), age, on);
        return Optional.of(elections
                .elected(car.typeCode(), age, on)
                .map(chosen -> new Rate(chosen, published.mileageMills()))
                .orElse(published));
    }

    /** Whether the car is in interchange service at all on that day. */
    public static boolean earnsCarHire(Car car, LocalDate on) {
        return car.eligibleOn(on);
    }
}
