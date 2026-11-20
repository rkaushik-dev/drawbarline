package io.github.rkaushikdev.drawbarline.events;

import io.github.rkaushikdev.drawbarline.clock.Interval;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import java.util.Optional;

/**
 * A stretch of time somebody is accountable for: from the event that opened it to the one that
 * closed it, or to a cut off while it is still open.
 *
 * <p>Two readings of it are billed: {@link OnLine}, a car on our rail, which car hire is computed
 * over, and {@link OnTrack}, a car on a customer track, which demurrage is counted over. Cutting
 * either down to a window with {@link #clipped} gives a plain {@link Stretch} that still remembers
 * what opened it.
 */
public sealed interface Span permits Stretch, OnLine, OnTrack {

    CarMark car();

    Interval interval();

    Event openedBy();

    /** The event that closed the span, absent while it is still open. */
    Optional<Event> closedBy();

    /** Whether nothing has closed the span yet. */
    default boolean isOpen() {
        return closedBy().isEmpty();
    }

    /** The span's length in whole minutes. */
    default long minutes() {
        return interval().minutes();
    }

    /** The part inside a window, still remembering what opened it. */
    default Span clipped(Interval window) {
        return new Stretch(car(), interval().clip(window), openedBy(), closedBy());
    }

    /** {@code DBL 104207 2025-01-14 14:00Z to 2025-02-01 00:00Z open}. */
    static String describe(Span span) {
        return span.car() + " " + span.interval() + " " + (span.isOpen() ? "open" : "closed");
    }
}
