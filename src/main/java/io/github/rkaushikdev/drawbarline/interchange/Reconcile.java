package io.github.rkaushikdev.drawbarline.interchange;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.events.Event;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Comparing our interchange record against the other road's.
 *
 * <p>Both sides of a junction record the same handoffs and both sides bill off their own record,
 * so the month a connection disputes is the month the two records differ. Finding the difference by
 * eye across two hundred records is a morning's work.
 *
 * <p>Matching is on the car, the kind and the station, and never on the time, because the time is
 * the thing being compared. Two records for the same car and kind at the same place on the same day
 * are the same handoff however far apart the clocks are.
 */
public final class Reconcile {

    /** How far apart two clocks may be and still agree. */
    public static final Duration TOLERANCE = Duration.ofMinutes(30);

    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final Comparator<Event> IN_ORDER = Comparator.comparing(Event::order);

    private Reconcile() {}

    /** What makes two records the same handoff, which is everything but the clock. */
    public static HandoffKey keyOf(Event event) {
        return new HandoffKey(event.car(), event.kind().name(), event.station(), DAY.format(event.when()));
    }

    /** Set two records of the same junction and month against each other, at the usual tolerance. */
    public static Reconciliation compare(List<Event> ours, List<Event> theirs) {
        return compare(ours, theirs, TOLERANCE);
    }

    /**
     * Set two records of the same junction and month against each other.
     *
     * <p>Their records are taken in event order, each paired with the first unmatched record of ours
     * for the same handoff. Whatever of ours is left over comes back in event order.
     */
    public static Reconciliation compare(List<Event> ours, List<Event> theirs, Duration tolerance) {
        if (tolerance.isNegative()) {
            throw new ReconcileError(
                    "a tolerance does not run backwards", Details.of("given", Durations.text(tolerance)));
        }

        Map<HandoffKey, List<Event>> mine = new LinkedHashMap<>();
        for (Event event : ours) {
            mine.computeIfAbsent(keyOf(event), key -> new ArrayList<>()).add(event);
        }

        Reconciliation found = new Reconciliation();
        for (Event event : theirs.stream().sorted(IN_ORDER).toList()) {
            List<Event> waiting = mine.computeIfAbsent(keyOf(event), key -> new ArrayList<>());
            if (waiting.isEmpty()) {
                found.onlyTheirs(event);
                continue;
            }
            Event match = waiting.remove(0);
            if (Duration.between(match.when(), event.when()).abs().compareTo(tolerance) > 0) {
                found.outOfTime(new Timing(match, event));
            } else {
                found.agree(match);
            }
        }

        List<Event> left = new ArrayList<>();
        for (List<Event> events : mine.values()) {
            left.addAll(events);
        }
        left.sort(IN_ORDER);
        found.onlyOurs(left);
        return found;
    }
}
