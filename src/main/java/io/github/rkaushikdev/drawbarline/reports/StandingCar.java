package io.github.rkaushikdev.drawbarline.reports;

import io.github.rkaushikdev.drawbarline.carhire.Rate;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.util.Objects;

/**
 * One foreign car standing on our rail, and what it costs a day where it is.
 *
 * @param owedTo the road its car hire is owed to
 * @param station where it was last seen, empty when nobody has said
 * @param hourlyTenths the hourly car hire rate in force, in tenths of a cent
 */
public record StandingCar(CarMark car, ReportingMark owedTo, String station, long hourlyTenths) {

    /** Hours in a day of standing still. */
    public static final int HOURS_A_DAY = 24;

    public StandingCar {
        Objects.requireNonNull(car, "car");
        Objects.requireNonNull(owedTo, "owedTo");
        Objects.requireNonNull(station, "station");
    }

    /** A day's hire at the hourly rate, to the nearest cent with a tie going to the even one. */
    public Money aDay() {
        return new Money((long) Math.rint(hourlyTenths * HOURS_A_DAY / (double) Rate.TENTHS_OF_CENT_PER_CENT));
    }

    /** {@code CN 400100 at ESC $111.12 a day}. */
    @Override
    public String toString() {
        return car + " at " + station + " " + aDay() + " a day";
    }
}
