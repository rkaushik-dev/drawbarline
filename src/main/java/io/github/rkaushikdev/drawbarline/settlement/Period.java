package io.github.rkaushikdev.drawbarline.settlement;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.SettlementError;
import io.github.rkaushikdev.drawbarline.carhire.Accrual;
import io.github.rkaushikdev.drawbarline.clock.Interval;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Locale;

/**
 * One calendar month of accounting.
 *
 * <p>Everything settles by calendar month on the general office clock. A period knows its own
 * boundaries as instants, not as dates, because a span that ends at midnight on the first has to
 * belong to exactly one of two months and the answer cannot depend on which zone the reader is in.
 */
public record Period(int year, int month) implements Comparable<Period> {

    public static final int MONTHS_IN_A_YEAR = 12;

    private static final Comparator<Period> ORDER =
            Comparator.comparingInt(Period::year).thenComparingInt(Period::month);

    public Period {
        if (month < 1 || month > MONTHS_IN_A_YEAR) {
            throw new SettlementError("that is not a month", Details.of("month", month));
        }
        if (year < 1900 || year > 2200) {
            throw new SettlementError("that is not a year we settle", Details.of("year", year));
        }
    }

    /** The month a day falls in. */
    public static Period of(LocalDate day) {
        return new Period(day.getYear(), day.getMonthValue());
    }

    public LocalDate firstDay() {
        return LocalDate.of(year, month, 1);
    }

    public LocalDate lastDay() {
        return following().firstDay().minusDays(1);
    }

    /** The month after. */
    public Period following() {
        if (month == MONTHS_IN_A_YEAR) {
            return new Period(year + 1, 1);
        }
        return new Period(year, month + 1);
    }

    /** The month before. */
    public Period preceding() {
        if (month == 1) {
            return new Period(year - 1, MONTHS_IN_A_YEAR);
        }
        return new Period(year, month - 1);
    }

    /** The month as instants, from general office midnight on the first to midnight on the next first. */
    public Interval window() {
        return new Interval(
                Accrual.midnightOn(firstDay()), Accrual.midnightOn(following().firstDay()));
    }

    /** Whether a day falls in the month. */
    public boolean covers(LocalDate day) {
        return !day.isBefore(firstDay()) && !day.isAfter(lastDay());
    }

    /** {@code 2025-01}. */
    public String label() {
        return String.format(Locale.ROOT, "%04d-%02d", year, month);
    }

    @Override
    public int compareTo(Period other) {
        return ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return label();
    }
}
