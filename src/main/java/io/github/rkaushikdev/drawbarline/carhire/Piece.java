package io.github.rkaushikdev.drawbarline.carhire;

import io.github.rkaushikdev.drawbarline.clock.Interval;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.util.Numbers;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One stretch of a span priced at one rate.
 *
 * @param on the accounting day the stretch starts on, which picks its rate
 */
public record Piece(Interval interval, Rate rate, LocalDate on) {

    public Piece {
        Objects.requireNonNull(interval, "interval");
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(on, "on");
    }

    /** Hours to two places, which is what car hire is settled in. */
    public long hundredthHours() {
        return interval.hundredthHours();
    }

    /**
     * Tenths of a cent an hour, over hundredths of an hour, rounded once to the nearest cent with a
     * tie going to the even cent.
     */
    public Money amount() {
        long tenths = rate.hourlyTenths() * hundredthHours();
        return new Money(
                (long) Math.rint(tenths / (double) (Accrual.HUNDREDTHS_PER_HOUR * Accrual.TENTHS_OF_CENT_PER_CENT)));
    }

    /** {@code 2025-01-14 1.00 hours $4.63}. */
    @Override
    public String toString() {
        return on + " " + Numbers.fixed(hundredthHours() / 100.0, 2) + " hours " + amount();
    }
}
