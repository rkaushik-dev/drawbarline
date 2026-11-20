package io.github.rkaushikdev.drawbarline.events;

import io.github.rkaushikdev.drawbarline.clock.Interval;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import java.util.Objects;
import java.util.Optional;

/** A span with nothing more to say about itself, which is what clipping a span to a window leaves. */
public record Stretch(CarMark car, Interval interval, Event openedBy, Optional<Event> closedBy) implements Span {

    public Stretch {
        Objects.requireNonNull(car, "car");
        Objects.requireNonNull(interval, "interval");
        Objects.requireNonNull(openedBy, "openedBy");
        Objects.requireNonNull(closedBy, "closedBy");
    }

    @Override
    public String toString() {
        return Span.describe(this);
    }
}
