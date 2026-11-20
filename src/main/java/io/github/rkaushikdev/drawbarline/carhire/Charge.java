package io.github.rkaushikdev.drawbarline.carhire;

import io.github.rkaushikdev.drawbarline.clock.Interval;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.util.Objects;

/**
 * One car, one span, one party owed: the hours and the miles kept apart, because a connection will
 * dispute one without disputing the other.
 */
public record Charge(CarMark car, ReportingMark owedTo, Interval window, Money hours, Money miles) {

    public Charge {
        Objects.requireNonNull(car, "car");
        Objects.requireNonNull(owedTo, "owedTo");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(hours, "hours");
        Objects.requireNonNull(miles, "miles");
    }

    /** The hours and the miles together. */
    public Money amount() {
        return hours.plus(miles);
    }

    /** Whether the charge comes to nothing at all. */
    public boolean isFree() {
        return amount().isZero();
    }

    /** {@code CN 400100 to CN $12.00}. */
    @Override
    public String toString() {
        return car + " to " + owedTo + " " + amount();
    }
}
