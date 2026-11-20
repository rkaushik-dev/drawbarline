package io.github.rkaushikdev.drawbarline.events;

import io.github.rkaushikdev.drawbarline.clock.Interval;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A span on a customer track, from the placement to the pull, which is what demurrage is counted
 * over.
 *
 * @param industry the track the car stood on
 * @param constructive whether the placement was constructive rather than actual
 * @param releasedAt when the shipper released the car, absent while they have not
 */
public record OnTrack(
        CarMark car,
        Interval interval,
        Event openedBy,
        Optional<Event> closedBy,
        String industry,
        boolean constructive,
        Optional<Instant> releasedAt)
        implements Span {

    public OnTrack {
        Objects.requireNonNull(car, "car");
        Objects.requireNonNull(interval, "interval");
        Objects.requireNonNull(openedBy, "openedBy");
        Objects.requireNonNull(closedBy, "closedBy");
        Objects.requireNonNull(industry, "industry");
        Objects.requireNonNull(releasedAt, "releasedAt");
    }

    @Override
    public String toString() {
        return Span.describe(this);
    }
}
