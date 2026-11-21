package io.github.rkaushikdev.drawbarline.reports;

import io.github.rkaushikdev.drawbarline.carhire.Charge;
import io.github.rkaushikdev.drawbarline.carhire.Liability;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.settlement.Period;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The car hire reports.
 *
 * <p>Two of them, and the difference matters. The liability report is one line a road and it is
 * what the general manager reads. The detail is one line a car and it is what gets sent to a
 * connection that says the number is wrong.
 *
 * <p>Neither computes anything. Both take a finished liability and lay it out, so the number on the
 * summary and the number on the detail cannot disagree.
 */
public final class CarHireReport {

    /** A window end as the detail prints it, month, day and UTC time: {@code 01-06 12:00}. */
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.ROOT).withZone(ZoneOffset.UTC);

    private CarHireReport() {}

    /** One line a road in mark order, with the hours and the miles kept apart. */
    public static List<String> liabilityReport(Liability found, Period period) {
        Table table = new Table(List.of(
                new Column("road"),
                new Column("cars", Align.RIGHT),
                new Column("hours", Align.RIGHT),
                new Column("mileage", Align.RIGHT),
                new Column("total", Align.RIGHT)));
        for (ReportingMark road : roadsOf(found)) {
            List<Charge> charges = found.forRoad(road);
            table.add(
                    road.toString(),
                    String.valueOf(charges.stream().map(Charge::car).distinct().count()),
                    Render.money(Money.total(charges.stream().map(Charge::hours).toList())),
                    Render.money(Money.total(charges.stream().map(Charge::miles).toList())),
                    Render.money(
                            Money.total(charges.stream().map(Charge::amount).toList())));
        }
        table.total(
                "all roads",
                String.valueOf(found.cars().size()),
                Render.money(found.hoursTotal()),
                Render.money(found.milesTotal()),
                Render.money(found.total()));
        List<String> out = new ArrayList<>(Render.heading("car hire liability", period.label()));
        out.addAll(table.lines());
        return List.copyOf(out);
    }

    /** One line a car, for the road that is arguing about it. */
    public static List<String> detailReport(Liability found, ReportingMark road, Period period) {
        Table table = new Table(List.of(
                new Column("car"),
                new Column("from"),
                new Column("to"),
                new Column("hours", Align.RIGHT),
                new Column("hire", Align.RIGHT),
                new Column("mileage", Align.RIGHT)));
        List<Charge> charges = found.forRoad(road);
        for (Charge charge : charges) {
            table.add(
                    charge.car().toString(),
                    WHEN.format(charge.window().start()),
                    WHEN.format(charge.window().end()),
                    Render.hours(charge.window().hundredthHours()),
                    Render.money(charge.hours()),
                    Render.money(charge.miles()));
        }
        table.total(
                charges.size() + " charges",
                "",
                "",
                Render.hours(charges.stream()
                        .mapToLong(one -> one.window().hundredthHours())
                        .sum()),
                Render.money(Money.total(charges.stream().map(Charge::hours).toList())),
                Render.money(Money.total(charges.stream().map(Charge::miles).toList())));
        List<String> out = new ArrayList<>(Render.heading("car hire detail " + road, period.label()));
        out.addAll(table.lines());
        return List.copyOf(out);
    }

    /** The summary as data, road mark to what it is owed, for anything that is not a printer. */
    public static Map<String, Money> owedTo(Liability found) {
        Map<String, Money> owed = new LinkedHashMap<>();
        Map<ReportingMark, Money> byRoad = found.byRoad();
        for (ReportingMark road : roadsOf(found)) {
            owed.put(road.toString(), byRoad.get(road));
        }
        return Collections.unmodifiableMap(owed);
    }

    private static List<ReportingMark> roadsOf(Liability found) {
        return found.byRoad().keySet().stream()
                .sorted(Comparator.comparing(ReportingMark::toString))
                .toList();
    }
}
