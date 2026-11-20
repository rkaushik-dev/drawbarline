package io.github.rkaushikdev.drawbarline.events;

import io.github.rkaushikdev.drawbarline.clock.Interval;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An opening event, whatever closed it, the time between, and the watched events that happened
 * inside.
 *
 * @param openedBy the event that opened the pair
 * @param closedBy the event that closed it, absent when it was still open at the cut off
 * @param interval from the opening to the closing, or to the cut off
 * @param inside the watched events between the two, in order
 */
public record Pair(Event openedBy, Optional<Event> closedBy, Interval interval, List<Event> inside) {

    public Pair {
        Objects.requireNonNull(openedBy, "openedBy");
        Objects.requireNonNull(closedBy, "closedBy");
        Objects.requireNonNull(interval, "interval");
        inside = List.copyOf(inside);
    }

    /** A pair with nothing watched inside it. */
    public Pair(Event openedBy, Optional<Event> closedBy, Interval interval) {
        this(openedBy, closedBy, interval, List.of());
    }

    /** Whether nothing closed the pair before the cut off. */
    public boolean isOpen() {
        return closedBy.isEmpty();
    }

    /** The first watched event of a kind inside the pair. */
    public Optional<Event> firstInside(EventKind kind) {
        return inside.stream().filter(event -> event.kind() == kind).findFirst();
    }
}
