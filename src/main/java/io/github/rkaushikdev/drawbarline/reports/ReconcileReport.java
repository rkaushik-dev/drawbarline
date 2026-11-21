package io.github.rkaushikdev.drawbarline.reports;

import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.interchange.Reconciliation;
import io.github.rkaushikdev.drawbarline.interchange.Timing;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Laying a reconciliation out for the road on the other side.
 *
 * <p>This one gets sent to somebody who is going to argue with it, so it is ordered the way an
 * argument goes. The records only they have come first, because those are the ones costing us
 * money. Then the ones only we have, because offering those up first is what makes the rest of the
 * letter credible. Then the timings, which are usually the whole story.
 *
 * <p>Every moment is printed in UTC, the one clock both roads' records agree on.
 */
public final class ReconcileReport {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DAY_AND_TIME =
            DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.ROOT).withZone(ZoneOffset.UTC);

    private ReconcileReport() {}

    /** The whole comparison, worst first. */
    public static List<String> reconciliationReport(Reconciliation found, ReportingMark road, String title) {
        List<String> lines = new ArrayList<>(Render.heading("interchange reconciliation " + road, title));
        lines.add("agreed: " + found.agreed().size());
        lines.add("");
        lines.addAll(section("on their record and not ours", found.onlyTheirs()));
        lines.addAll(section("on our record and not theirs", found.onlyOurs()));

        if (found.timings().isEmpty()) {
            lines.add("recorded at different times: none");
            return List.copyOf(lines);
        }
        Table table = new Table(List.of(
                new Column("car"),
                new Column("kind"),
                new Column("ours"),
                new Column("theirs"),
                new Column("minutes", Align.RIGHT)));
        List<Timing> worstFirst = new ArrayList<>(found.timings());
        worstFirst.sort(Comparator.comparingLong(Timing::minutesApart).reversed());
        for (Timing one : worstFirst) {
            table.add(
                    one.ours().car().toString(),
                    one.ours().kind().toString(),
                    DAY_AND_TIME.format(one.ours().when()),
                    DAY_AND_TIME.format(one.theirs().when()),
                    String.valueOf(one.minutesApart()));
        }
        lines.add("recorded at different times: " + found.timings().size());
        lines.addAll(table.lines());
        return List.copyOf(lines);
    }

    private static List<String> section(String title, List<Event> events) {
        if (events.isEmpty()) {
            return List.of(title + ": none", "");
        }
        Table table = new Table(List.of(new Column("car"), new Column("kind"), new Column("at"), new Column("when")));
        for (Event event : events) {
            table.add(event.car().toString(), event.kind().toString(), event.station(), WHEN.format(event.when()));
        }
        List<String> lines = new ArrayList<>();
        lines.add(title + ": " + events.size());
        lines.addAll(table.lines());
        lines.add("");
        return lines;
    }
}
