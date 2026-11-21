package io.github.rkaushikdev.drawbarline.reports;

import io.github.rkaushikdev.drawbarline.events.EventKind;
import io.github.rkaushikdev.drawbarline.events.EventLog;
import io.github.rkaushikdev.drawbarline.events.Pair;
import io.github.rkaushikdev.drawbarline.events.Spans;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.util.Numbers;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * What is in the shop, and how long it has been there.
 *
 * <p>A bad ordered car is a car earning nothing and costing car hire, so the two numbers the
 * mechanical department is asked for are how many are down and how long the ones that came back
 * took. Both come off the same pair of events and neither needs anything the log does not already
 * carry.
 *
 * <p>The report separates the cars still down from the ones that came back, because they are
 * different questions. Cars still down are a queue somebody has to work. Cars that came back are a
 * record somebody is judged on.
 */
public final class ShopReport {

    private static final DateTimeFormatter MINUTE =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT).withZone(ZoneOffset.UTC);

    private ShopReport() {}

    /** Every stretch a car has been bad ordered up to a cut off, oldest first. */
    public static List<ShopSpan> shopSpans(EventLog log, Instant until) {
        List<ShopSpan> found = new ArrayList<>();
        for (CarMark car : log.cars()) {
            List<Pair> pairs = Spans.scanPairs(
                    log.stream(car),
                    EnumSet.of(EventKind.BAD_ORDERED),
                    EnumSet.of(EventKind.REPAIRED),
                    until,
                    "the bad order");
            for (Pair pair : pairs) {
                found.add(new ShopSpan(car, pair.interval(), pair.openedBy().station(), pair.isOpen()));
            }
        }
        found.sort(Comparator.comparing((ShopSpan one) -> one.interval().start())
                .thenComparing(one -> one.car().toString()));
        return List.copyOf(found);
    }

    /** The cars still down. */
    public static List<ShopSpan> downNow(List<ShopSpan> spans) {
        return spans.stream().filter(ShopSpan::stillDown).toList();
    }

    /** The cars that came back. */
    public static List<ShopSpan> cameBack(List<ShopSpan> spans) {
        return spans.stream().filter(one -> !one.stillDown()).toList();
    }

    /** The mean days to repair over the cars that came back, nothing when none has. */
    public static double turnDays(List<ShopSpan> spans) {
        List<ShopSpan> finished = cameBack(spans);
        if (finished.isEmpty()) {
            return 0.0;
        }
        double days = 0;
        for (ShopSpan one : finished) {
            days += one.days();
        }
        return days / finished.size();
    }

    /** The cars still down, then the ones that came back with the mean turn on the total. */
    public static List<String> shopReport(EventLog log, Instant until) {
        List<ShopSpan> spans = shopSpans(log, until);
        List<String> lines = new ArrayList<>(Render.heading("bad order report", MINUTE.format(until) + "Z"));

        Table down = new Table(
                List.of(new Column("car"), new Column("at"), new Column("since"), new Column("days", Align.RIGHT)));
        for (ShopSpan one : downNow(spans)) {
            down.add(
                    one.car().toString(),
                    one.station(),
                    MINUTE.format(one.interval().start()),
                    Numbers.fixed(one.days(), 1));
        }
        down.total(downNow(spans).size() + " down", "", "", "");
        lines.add("cars down");
        lines.addAll(down.lines());
        lines.add("");

        Table back = new Table(
                List.of(new Column("car"), new Column("at"), new Column("out"), new Column("days", Align.RIGHT)));
        for (ShopSpan one : cameBack(spans)) {
            back.add(
                    one.car().toString(),
                    one.station(),
                    DAY.format(one.interval().start()),
                    Numbers.fixed(one.days(), 1));
        }
        back.total(cameBack(spans).size() + " back", "", "", Numbers.fixed(turnDays(spans), 1));
        lines.add("cars back in service");
        lines.addAll(back.lines());
        return List.copyOf(lines);
    }
}
