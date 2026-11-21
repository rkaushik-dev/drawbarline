package io.github.rkaushikdev.drawbarline.reports;

import io.github.rkaushikdev.drawbarline.clock.Clock;
import io.github.rkaushikdev.drawbarline.clock.Interval;
import io.github.rkaushikdev.drawbarline.events.CarState;
import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.events.EventKind;
import io.github.rkaushikdev.drawbarline.events.EventLog;
import io.github.rkaushikdev.drawbarline.events.Positions;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What crossed the junctions, and what is still standing here.
 *
 * <p>The interchange balance is the first thing a connection disputes, because it is the only
 * figure both roads compute independently from the same events. If we say we took ninety cars from
 * CN in January and they say ninety two, the argument is about two sheets, and finding which two is
 * a great deal easier when both sides have the count by junction rather than by road.
 *
 * <p>Cars on hand is the other half. A car received and not delivered is standing somewhere on this
 * railroad costing money by the hour, and the report that says how many and how long is the one
 * the general manager asks for by name.
 */
public final class InterchangeReport {

    /** A moment as the report prints it, in UTC to the minute. */
    private static final DateTimeFormatter MINUTE =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT).withZone(ZoneOffset.UTC);

    private InterchangeReport() {}

    private record Key(String junction, ReportingMark road) {}

    /** Every junction and road pair that saw a car inside a window, by junction and then road. */
    public static List<Crossing> crossings(EventLog log, Interval window) {
        Map<Key, int[]> counts = new LinkedHashMap<>();
        for (Event event : log.during(window)) {
            if (!event.kind().isInterchange() || event.road().isEmpty()) {
                continue;
            }
            int[] seen =
                    counts.computeIfAbsent(new Key(event.station(), event.road().get()), key -> new int[2]);
            if (event.kind() == EventKind.RECEIVED) {
                seen[0]++;
            } else {
                seen[1]++;
            }
        }
        return counts.entrySet().stream()
                .sorted(Comparator.comparing(
                                (Map.Entry<Key, int[]> pair) -> pair.getKey().junction())
                        .thenComparing(pair -> pair.getKey().road().toString()))
                .map(pair -> new Crossing(
                        pair.getKey().junction(), pair.getKey().road(), pair.getValue()[0], pair.getValue()[1]))
                .toList();
    }

    /** One line a junction and road, with the net at the end. */
    public static List<String> balanceReport(EventLog log, Interval window, String title) {
        Table table = new Table(List.of(
                new Column("junction"),
                new Column("road"),
                new Column("received", Align.RIGHT),
                new Column("delivered", Align.RIGHT),
                new Column("net", Align.RIGHT)));
        List<Crossing> found = crossings(log, window);
        for (Crossing one : found) {
            table.add(
                    one.junction(),
                    one.road().toString(),
                    String.valueOf(one.received()),
                    String.valueOf(one.delivered()),
                    String.valueOf(one.net()));
        }
        table.total(
                found.size() + " junctions",
                "",
                String.valueOf(found.stream().mapToInt(Crossing::received).sum()),
                String.valueOf(found.stream().mapToInt(Crossing::delivered).sum()),
                String.valueOf(found.stream().mapToInt(Crossing::net).sum()));
        List<String> out = new ArrayList<>(Render.heading("interchange balance", title));
        out.addAll(table.lines());
        return List.copyOf(out);
    }

    /** Every car we have taken and not yet handed back at a moment, oldest first. */
    public static List<OnHand> onHand(EventLog log, Instant at) {
        Map<CarMark, CarState> where = Positions.positions(log, at);
        List<OnHand> found = new ArrayList<>();
        for (CarMark car : Positions.onLine(where)) {
            CarState state = where.get(car);
            if (state.receivedAt().isEmpty()) {
                continue;
            }
            Instant since = state.receivedAt().get();
            found.add(new OnHand(
                    car,
                    state.station().orElse(""),
                    state.industry(),
                    since,
                    Math.floorDiv(Duration.between(since, at).getSeconds(), Clock.SECONDS_PER_MINUTE),
                    state.badOrdered(),
                    state.held()));
        }
        found.sort(Comparator.comparing(OnHand::since)
                .thenComparing(one -> one.car().toString()));
        return List.copyOf(found);
    }

    /** One line a car, oldest first, with the reason it is stuck if there is one. */
    public static List<String> onHandReport(EventLog log, Instant at) {
        Table table = new Table(List.of(
                new Column("car"),
                new Column("where"),
                new Column("since"),
                new Column("hours", Align.RIGHT),
                new Column("note")));
        List<OnHand> found = onHand(log, at);
        for (OnHand one : found) {
            List<String> reasons = new ArrayList<>();
            if (one.badOrdered()) {
                reasons.add("bad order");
            }
            if (one.held()) {
                reasons.add("held");
            }
            table.add(
                    one.car().toString(),
                    one.industry().filter(track -> !track.isEmpty()).orElse(one.station()),
                    MINUTE.format(one.since()),
                    Render.hours(one.hundredthHours()),
                    String.join(" ", reasons));
        }
        table.total(
                found.size() + " cars",
                "",
                "",
                Render.hours(found.stream().mapToLong(OnHand::hundredthHours).sum()),
                "");
        List<String> out = new ArrayList<>(Render.heading("cars on hand", MINUTE.format(at) + "Z"));
        out.addAll(table.lines());
        return List.copyOf(out);
    }
}
