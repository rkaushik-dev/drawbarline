package io.github.rkaushikdev.drawbarline.reports;

import io.github.rkaushikdev.drawbarline.clock.Interval;
import io.github.rkaushikdev.drawbarline.distribution.Distribution;
import io.github.rkaushikdev.drawbarline.embargo.Embargo;
import io.github.rkaushikdev.drawbarline.embargo.EmbargoRegister;
import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.events.EventKind;
import io.github.rkaushikdev.drawbarline.events.EventLog;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The morning sheet.
 *
 * <p>One page, read standing up, before anybody has sat down. It answers the four questions the
 * superintendent asks every day in the same order: how many cars are we holding, what moved
 * yesterday, what is stopped, and what did we fail to do for a customer.
 *
 * <p>Nothing on it is new. Every figure is one another report already produces, and that is the
 * point: the morning sheet and the monthly report have to agree, and the way to guarantee that is
 * for one to be built out of the same functions as the other.
 */
public final class DailyReport {

    /** The length of the day the sheet looks back over. */
    public static final int HOURS_A_DAY = 24;

    private DailyReport() {}

    /** The twenty four hours ending on a day at the time of day, in UTC, of a moment. */
    public static Interval windowOf(LocalDate day, Instant at) {
        Instant end = day.atTime(LocalTime.ofInstant(at, ZoneOffset.UTC)).toInstant(ZoneOffset.UTC);
        return new Interval(end.minus(Duration.ofHours(HOURS_A_DAY)), end);
    }

    /** How many of each kind happened in a window. */
    public static DayCounts countsIn(EventLog log, Interval window) {
        Map<EventKind, Integer> seen = new EnumMap<>(EventKind.class);
        for (EventKind kind : EventKind.values()) {
            seen.put(kind, 0);
        }
        for (Event event : log.during(window)) {
            seen.merge(event.kind(), 1, Integer::sum);
        }
        return new DayCounts(
                seen.get(EventKind.RECEIVED),
                seen.get(EventKind.DELIVERED),
                seen.get(EventKind.PLACED) + seen.get(EventKind.CONSTRUCTIVE),
                seen.get(EventKind.PULLED),
                seen.get(EventKind.BAD_ORDERED));
    }

    /** The morning sheet for one day, taken at a moment. */
    public static List<String> dailySummary(
            EventLog log, EmbargoRegister embargoes, Distribution distribution, LocalDate day, Instant at) {
        DayCounts counts = countsIn(log, windowOf(day, at));
        List<OnHand> standing = InterchangeReport.onHand(log, at);
        List<ShopSpan> down = ShopReport.downNow(ShopReport.shopSpans(log, at));
        List<Embargo> live = embargoes.inForceOn(day);

        Table movement = new Table(List.of(new Column("what"), new Column("cars", Align.RIGHT)));
        movement.add("received in interchange", String.valueOf(counts.received()));
        movement.add("delivered in interchange", String.valueOf(counts.delivered()));
        movement.add("placed at industries", String.valueOf(counts.placed()));
        movement.add("pulled off industries", String.valueOf(counts.pulled()));
        movement.add("set out bad order", String.valueOf(counts.badOrdered()));
        movement.total("net change on line", String.valueOf(counts.netCars()));

        List<String> lines = new ArrayList<>(Render.heading("morning sheet", day.toString()));
        lines.addAll(movement.lines());
        lines.add("");
        lines.add("cars on hand          " + standing.size());
        lines.add("cars bad ordered      " + down.size());
        lines.add("embargoes in force    " + live.size());
        lines.add("car orders filled     " + distribution.filled());
        lines.add("car orders short      " + distribution.unfilled());

        if (!down.isEmpty()) {
            lines.add("");
            lines.add("bad order");
            for (ShopSpan span : down) {
                lines.add("  " + span);
            }
        }
        if (!live.isEmpty()) {
            lines.add("");
            lines.add("embargoes");
            for (Embargo embargo : live) {
                lines.add("  " + embargo);
            }
        }
        return List.copyOf(lines);
    }
}
