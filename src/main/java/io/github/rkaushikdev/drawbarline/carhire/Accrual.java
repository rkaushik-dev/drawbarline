package io.github.rkaushikdev.drawbarline.carhire;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.RateError;
import io.github.rkaushikdev.drawbarline.clock.Clock;
import io.github.rkaushikdev.drawbarline.clock.Interval;
import io.github.rkaushikdev.drawbarline.fleet.Car;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.network.Station;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Working out what a span of time on our rail is worth.
 *
 * <p>A span is not billed at one rate. The schedule changes on filed dates and the car has a
 * birthday, and either of those inside a span steps the rate. So the span is cut at every boundary
 * that falls strictly inside it, each piece is priced at the rate in force for that piece, and the
 * pieces are added.
 *
 * <p>Where the cut falls is a decision, not a fact. Rates take effect at midnight, and midnight is
 * a different instant at Escanaba than at Marinette. The general office keeps central time and
 * files everything on it, so that is the clock the cuts are made on: {@link #SETTLEMENT_ZONE}.
 *
 * <p>A car past its fifty years earns nothing, and that is checked here rather than by whoever
 * reads the accrual. Rounding happens once, on the finished piece, never on each hour: a tenth of a
 * cent an hour rounded up every hour adds about two dollars a month to a single car and nobody
 * could reproduce the figure.
 */
public final class Accrual {

    /** The general office clock every accounting day and every rate step runs on. */
    public static final ZoneId SETTLEMENT_ZONE = ZoneId.of("America/Chicago");

    public static final int TENTHS_OF_CENT_PER_CENT = 10;
    public static final int HUNDREDTHS_PER_HOUR = 100;
    public static final int TENTHS_PER_MILE = 10;

    private static final DateTimeFormatter READING = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT);

    private Accrual() {}

    /** The accounting day a moment falls in, on the general office clock. */
    public static LocalDate settlementDate(Instant moment) {
        return moment.atZone(SETTLEMENT_ZONE).toLocalDate();
    }

    /** The instant an accounting day begins. */
    public static Instant midnightOn(LocalDate day) {
        return day.atStartOfDay(SETTLEMENT_ZONE).toInstant();
    }

    /** Every build date anniversary strictly after the first day and up to the last; a leap-day car keeps the 28th. */
    private static List<LocalDate> anniversaries(Car car, LocalDate first, LocalDate last) {
        List<LocalDate> found = new ArrayList<>();
        for (int year = first.getYear(); year <= last.getYear(); year++) {
            LocalDate birthday = car.built().withYear(year);
            if (birthday.isAfter(first) && !birthday.isAfter(last)) {
                found.add(birthday);
            }
        }
        return found;
    }

    /** The accounting days a span has to be split on, in order. */
    public static List<LocalDate> cutPoints(Car car, RateTable table, Interval window) {
        LocalDate first = settlementDate(window.start());
        LocalDate last = settlementDate(window.end());
        TreeSet<LocalDate> steps = new TreeSet<>(table.changesBetween(car.typeCode(), first, last));
        steps.addAll(anniversaries(car, first, last));
        return steps.stream().filter(day -> window.contains(midnightOn(day))).toList();
    }

    /** Cut a span at every rate step and price each piece at the published schedule. */
    public static List<Piece> pieces(Car car, RateTable table, Interval window) {
        return pieces(car, table, window, new Elections());
    }

    /**
     * Cut a span at every rate step and price each piece.
     *
     * <p>A piece the car was not in interchange service for is left out rather than priced at zero,
     * so a span wholly outside the car's life comes back empty and nothing on the statement
     * mentions it.
     */
    public static List<Piece> pieces(Car car, RateTable table, Interval window, Elections elections) {
        if (window.isEmpty()) {
            return List.of();
        }
        List<Instant> edges = new ArrayList<>();
        edges.add(window.start());
        for (LocalDate day : cutPoints(car, table, window)) {
            edges.add(midnightOn(day));
        }
        edges.add(window.end());

        List<Piece> found = new ArrayList<>();
        for (int at = 1; at < edges.size(); at++) {
            Instant start = edges.get(at - 1);
            Instant end = edges.get(at);
            if (start.equals(end)) {
                continue;
            }
            LocalDate day = settlementDate(start);
            Optional<Rate> rate = Deprescription.payable(car, table, elections, day);
            rate.ifPresent(one -> found.add(new Piece(new Interval(start, end), one, day)));
        }
        return List.copyOf(found);
    }

    /** What the hours in a span come to at the published schedule. */
    public static Money hourly(Car car, RateTable table, Interval window) {
        return hourly(car, table, window, new Elections());
    }

    /** What the hours in a span come to, each piece rounded once. */
    public static Money hourly(Car car, RateTable table, Interval window, Elections elections) {
        long total = 0;
        for (Piece piece : pieces(car, table, window, elections)) {
            total += piece.amount().cents();
        }
        return new Money(total);
    }

    /** What the miles come to at the published schedule in force on the day they ran. */
    public static Money mileage(Car car, RateTable table, long tenthsRun, LocalDate on) {
        return mileage(car, table, tenthsRun, on, new Elections());
    }

    /** What the miles come to, at the rate in force on the day they ran, rounded once. */
    public static Money mileage(Car car, RateTable table, long tenthsRun, LocalDate on, Elections elections) {
        if (tenthsRun < 0) {
            throw new RateError("a car does not run backwards", Details.of("tenths", tenthsRun));
        }
        Optional<Rate> rate = Deprescription.payable(car, table, elections, on);
        if (rate.isEmpty()) {
            return Money.ZERO;
        }
        long mills = rate.get().mileageMills() * tenthsRun;
        return new Money((long) Math.rint(mills / (double) (TENTHS_PER_MILE * TENTHS_OF_CENT_PER_CENT)));
    }

    /** The span as the clerk at a station would read it: {@code 2025-01-14 09:00 to 2025-01-14 13:00}. */
    public static String asReadAt(Interval window, Station station) {
        return READING.format(Clock.toLocal(station, window.start()))
                + " to "
                + READING.format(Clock.toLocal(station, window.end()));
    }
}
