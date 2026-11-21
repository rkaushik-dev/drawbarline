package io.github.rkaushikdev.drawbarline.reports;

import io.github.rkaushikdev.drawbarline.clock.Interval;
import io.github.rkaushikdev.drawbarline.events.CarStream;
import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.events.EventKind;
import io.github.rkaushikdev.drawbarline.events.EventLog;
import io.github.rkaushikdev.drawbarline.events.OnLine;
import io.github.rkaushikdev.drawbarline.events.OnTrack;
import io.github.rkaushikdev.drawbarline.events.Spans;
import io.github.rkaushikdev.drawbarline.fleet.Car;
import io.github.rkaushikdev.drawbarline.fleet.FleetRegister;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What the fleet did, and how old it is getting.
 *
 * <p>Turn time is the number the operating department is judged on. It is the hours between taking
 * a car and handing it back, and the part of that the car spent standing on somebody's private
 * track is the part they can do something about. So the report keeps the two apart rather than
 * printing one figure nobody can act on.
 *
 * <p>The age profile is the other half of the same worry. Cars leave interchange service fifty
 * years after they were built and there is no appeal, so the office wants to know every year how
 * many go out in the next five. A fleet that is fine this year and has thirty cars expiring in
 * three is a fleet with a capital problem nobody has noticed.
 */
public final class FleetReport {

    /** The width of an age band, in years. */
    public static final int DECADE = 10;

    /** How far ahead the retirement list looks, in years. */
    public static final int LOOKAHEAD_YEARS = 5;

    private FleetReport() {}

    /** Work one car's month out of its stream. */
    public static Cycle cycleOf(EventLog log, CarMark car, Interval window) {
        CarStream stream = log.stream(car);
        List<Interval> onLine = new ArrayList<>();
        for (OnLine one : Spans.onLineSpans(stream, window.end())) {
            onLine.add(one.interval().clip(window));
        }
        List<Interval> onTrack = new ArrayList<>();
        for (OnTrack one : Spans.onTrackSpans(stream, window.end())) {
            onTrack.add(one.interval().clip(window));
        }
        long ran = 0;
        for (Event event : stream.during(window)) {
            if (event.kind() == EventKind.MOVED) {
                ran += event.tenths().orElse(0);
            }
        }
        return new Cycle(
                car,
                (int) onLine.stream().filter(one -> !one.isEmpty()).count(),
                onLine.stream().mapToLong(Interval::minutes).sum(),
                onTrack.stream().mapToLong(Interval::minutes).sum(),
                ran);
    }

    /** Every car's month, leaving out the cars with nothing on our rail inside the window. */
    public static List<Cycle> cycles(EventLog log, Interval window) {
        return log.cars().stream()
                .map(car -> cycleOf(log, car, window))
                .filter(one -> one.onLineMinutes() != 0 || one.visits() != 0)
                .toList();
    }

    /** One line a car, turn time against standing time. */
    public static List<String> utilisationReport(EventLog log, Interval window, String title) {
        Table table = new Table(List.of(
                new Column("car"),
                new Column("visits", Align.RIGHT),
                new Column("turn hrs", Align.RIGHT),
                new Column("standing", Align.RIGHT),
                new Column("miles", Align.RIGHT)));
        List<Cycle> found = cycles(log, window);
        for (Cycle one : found) {
            table.add(
                    one.car().toString(),
                    String.valueOf(one.visits()),
                    Render.hours(one.turnHundredths()),
                    Render.hours(one.standingHundredths()),
                    Render.miles(one.tenthsRun()));
        }
        table.total(
                found.size() + " cars",
                String.valueOf(found.stream().mapToInt(Cycle::visits).sum()),
                Render.hours(found.stream().mapToLong(Cycle::turnHundredths).sum()),
                Render.hours(found.stream().mapToLong(Cycle::standingHundredths).sum()),
                Render.miles(found.stream().mapToLong(Cycle::tenthsRun).sum()));
        List<String> out = new ArrayList<>(Render.heading("fleet utilisation", title));
        out.addAll(table.lines());
        return List.copyOf(out);
    }

    /** How many cars in service on a day sit in each decade of age, youngest decade first. */
    public static Map<String, Integer> ageProfile(FleetRegister register, LocalDate on) {
        Map<Integer, Integer> byDecade = new TreeMap<>();
        for (Car car : register) {
            if (!register.inServiceOn(car.mark(), on)) {
                continue;
            }
            byDecade.merge(Math.floorDiv(car.ageOn(on), DECADE) * DECADE, 1, Integer::sum);
        }
        Map<String, Integer> found = new LinkedHashMap<>();
        byDecade.forEach((decade, count) -> found.put(decade + " to " + (decade + DECADE - 1), count));
        return Collections.unmodifiableMap(found);
    }

    /** Cars in service whose fifty years run out within a number of years from a day, in mark order. */
    public static List<CarMark> expiringWithin(FleetRegister register, LocalDate on, int years) {
        LocalDate horizon = LocalDate.of(on.getYear() + years, on.getMonthValue(), Math.min(on.getDayOfMonth(), 28));
        List<CarMark> found = new ArrayList<>();
        for (Car car : register) {
            if (register.inServiceOn(car.mark(), on)
                    && !car.interchangeExpires().isAfter(horizon)) {
                found.add(car.mark());
            }
        }
        return List.copyOf(found);
    }

    /** The age profile with the retirement list under it. */
    public static List<String> ageReport(FleetRegister register, LocalDate on) {
        Table table = new Table(List.of(new Column("age"), new Column("cars", Align.RIGHT)));
        Map<String, Integer> profile = ageProfile(register, on);
        profile.forEach((label, count) -> table.add(label, String.valueOf(count)));
        table.total(
                "all cars",
                String.valueOf(
                        profile.values().stream().mapToInt(Integer::intValue).sum()));

        List<CarMark> leaving = expiringWithin(register, on, LOOKAHEAD_YEARS);
        List<String> out = new ArrayList<>(Render.heading("fleet age profile", on.toString()));
        out.addAll(table.lines());
        out.add("");
        out.add("out of interchange service within " + LOOKAHEAD_YEARS + " years: " + leaving.size());
        for (CarMark mark : leaving) {
            out.add("  " + mark);
        }
        return List.copyOf(out);
    }
}
