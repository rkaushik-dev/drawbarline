package io.github.rkaushikdev.drawbarline.interchange;

import io.github.rkaushikdev.drawbarline.events.Event;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * What our interchange record and the other road's agree on and what they do not.
 *
 * <p>A record only we have is usually a car we took and they have not billed us for yet. A record
 * only they have is a car they think we took, which is the expensive one. A record both have at
 * different times is nearly always a clerk reading a clock at the wrong end of a shift.
 */
public final class Reconciliation {

    private final List<Event> agreed = new ArrayList<>();
    private final List<Event> onlyOurs = new ArrayList<>();
    private final List<Event> onlyTheirs = new ArrayList<>();
    private final List<Timing> timings = new ArrayList<>();

    Reconciliation() {}

    /** Our side of every handoff the two records agree on. */
    public List<Event> agreed() {
        return Collections.unmodifiableList(agreed);
    }

    /** Handoffs only we recorded, in event order. */
    public List<Event> onlyOurs() {
        return Collections.unmodifiableList(onlyOurs);
    }

    /** Handoffs only they recorded, in event order. */
    public List<Event> onlyTheirs() {
        return Collections.unmodifiableList(onlyTheirs);
    }

    /** Handoffs both recorded, further apart than the tolerance. */
    public List<Timing> timings() {
        return Collections.unmodifiableList(timings);
    }

    void agree(Event ours) {
        agreed.add(ours);
    }

    void onlyTheirs(Event theirs) {
        onlyTheirs.add(theirs);
    }

    void onlyOurs(List<Event> ours) {
        onlyOurs.clear();
        onlyOurs.addAll(ours);
    }

    void outOfTime(Timing timing) {
        timings.add(timing);
    }

    /** Whether the two records agree on everything. */
    public boolean isClean() {
        return onlyOurs.isEmpty() && onlyTheirs.isEmpty() && timings.isEmpty();
    }

    /** How many differences there are, of all three kinds. */
    public int differences() {
        return onlyOurs.size() + onlyTheirs.size() + timings.size();
    }

    /** The handoff whose two clocks were furthest apart, the first of them if several tie. */
    public Optional<Timing> worstTiming() {
        return timings.stream().max(Comparator.comparing(Timing::apart));
    }

    /** {@code 1 records, both sides agree}, or the four counts. */
    @Override
    public String toString() {
        if (isClean()) {
            return agreed.size() + " records, both sides agree";
        }
        return agreed.size() + " agreed, " + onlyOurs.size() + " only ours, " + onlyTheirs.size() + " only theirs, "
                + timings.size() + " out of time";
    }
}
