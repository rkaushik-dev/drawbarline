package io.github.rkaushikdev.drawbarline.reports;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.RateError;
import io.github.rkaushikdev.drawbarline.carhire.Accrual;
import io.github.rkaushikdev.drawbarline.carhire.Rate;
import io.github.rkaushikdev.drawbarline.carhire.RateTable;
import io.github.rkaushikdev.drawbarline.events.CarState;
import io.github.rkaushikdev.drawbarline.events.EventLog;
import io.github.rkaushikdev.drawbarline.events.Positions;
import io.github.rkaushikdev.drawbarline.fleet.Car;
import io.github.rkaushikdev.drawbarline.fleet.FleetRegister;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the cars standing here cost us a day.
 *
 * <p>The car hire report says what last month came to. This says what today is costing, which is
 * the number the superintendent can still do something about. It is the hourly rate of every
 * foreign car on hand, added up, times twenty four, and it assumes nothing moves, which is exactly
 * the assumption worth pricing.
 *
 * <p>It is a forecast and it is honest about being one. Nothing here is posted, nothing is billed
 * off it, and the mileage half is left out entirely because a car that does not move runs no
 * miles. A forecast that quietly included mileage would be a forecast that could not be checked
 * against tomorrow's accrual.
 */
public final class ForecastReport {

    /** How many days a forecast runs out when nobody says. */
    public static final int DAYS = 7;

    private static final DateTimeFormatter MINUTE =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT).withZone(ZoneOffset.UTC);

    private ForecastReport() {}

    /**
     * Every foreign car on hand at a moment, priced at the hourly rate in force. Our own cars,
     * private cars, cars the register has no record of and cars with no rate on file are left out.
     */
    public static List<StandingCar> standingCars(
            FleetRegister register, EventLog log, RateTable rates, Instant at, ReportingMark home) {
        Map<CarMark, CarState> where = Positions.positions(log, at);
        LocalDate day = Accrual.settlementDate(at);
        List<StandingCar> found = new ArrayList<>();
        for (CarMark car : Positions.onLine(where)) {
            if (!register.has(car)) {
                continue;
            }
            ReportingMark owedTo = register.ownership(car).holderOn(day);
            if (owedTo.equals(home)) {
                continue;
            }
            Car record = register.get(car);
            if (record.isPrivate()) {
                continue;
            }
            Rate rate;
            try {
                rate = rates.lookup(record.typeCode(), record.ageOn(day), day);
            } catch (RateError noRate) {
                continue;
            }
            found.add(new StandingCar(car, owedTo, where.get(car).station().orElse(""), rate.hourlyTenths()));
        }
        return List.copyOf(found);
    }

    /** What the whole railroad is costing a day as it stands. */
    public static Money aDay(List<StandingCar> cars) {
        return Money.total(cars.stream().map(StandingCar::aDay).toList());
    }

    /** What each road's cars cost a day, in mark order. */
    public static Map<ReportingMark, Money> byRoad(List<StandingCar> cars) {
        Map<ReportingMark, Money> running = new HashMap<>();
        for (StandingCar one : cars) {
            running.merge(one.owedTo(), one.aDay(), Money::plus);
        }
        Map<ReportingMark, Money> found = new LinkedHashMap<>();
        running.entrySet().stream()
                .sorted(Comparator.comparing(pair -> pair.getKey().toString()))
                .forEach(pair -> found.put(pair.getKey(), pair.getValue()));
        return Collections.unmodifiableMap(found);
    }

    /** What it comes to over a run of days if nothing moves, refusing a run backwards. */
    public static Money over(List<StandingCar> cars, int days) {
        if (days < 0) {
            throw new RateError("a forecast does not run backwards", Details.of("days", days));
        }
        return aDay(cars).times(days);
    }

    /** One line a road with a week run out, and the whole railroad on the bottom. */
    public static List<String> forecastReport(List<StandingCar> cars, Instant at) {
        return forecastReport(cars, at, DAYS);
    }

    /** One line a road with the run of days out, and the whole railroad on the bottom. */
    public static List<String> forecastReport(List<StandingCar> cars, Instant at, int days) {
        Table table = new Table(List.of(
                new Column("road"),
                new Column("cars", Align.RIGHT),
                new Column("a day", Align.RIGHT),
                new Column(days + " days", Align.RIGHT)));
        Map<ReportingMark, Integer> counts = new HashMap<>();
        for (StandingCar one : cars) {
            counts.merge(one.owedTo(), 1, Integer::sum);
        }
        byRoad(cars)
                .forEach((road, amount) -> table.add(
                        road.toString(),
                        String.valueOf(counts.get(road)),
                        Render.money(amount),
                        Render.money(amount.times(days))));
        table.total("all roads", String.valueOf(cars.size()), Render.money(aDay(cars)), Render.money(over(cars, days)));
        String subtitle = MINUTE.format(at) + "Z, nothing moves";
        List<String> out = new ArrayList<>(Render.heading("car hire forecast", subtitle));
        out.addAll(table.lines());
        return List.copyOf(out);
    }

    /** How many days of standing still it takes to reach an amount, refusing when nothing accrues. */
    public static int daysUntil(List<StandingCar> cars, Money amount) {
        Money burn = aDay(cars);
        if (burn.isZero()) {
            throw new RateError("nothing is accruing, so it never gets there");
        }
        Money running = Money.ZERO;
        int days = 0;
        while (running.isLessThan(amount)) {
            running = running.plus(burn);
            days++;
        }
        return days;
    }

    /** The same moment a day later. */
    public static Instant tomorrow(Instant at) {
        return at.plus(Duration.ofDays(1));
    }
}
