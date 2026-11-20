package io.github.rkaushikdev.drawbarline.events;

import io.github.rkaushikdev.drawbarline.clock.Interval;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import java.util.Objects;
import java.util.Optional;

/**
 * A span on our rail, from the receipt at a junction to the delivery back, which is what car hire
 * is computed over.
 *
 * @param receivedFrom the road that handed us the car
 * @param deliveredTo the road we handed it to, absent while it is still here
 */
public record OnLine(
        CarMark car,
        Interval interval,
        Event openedBy,
        Optional<Event> closedBy,
        Optional<ReportingMark> receivedFrom,
        Optional<ReportingMark> deliveredTo)
        implements Span {

    public OnLine {
        Objects.requireNonNull(car, "car");
        Objects.requireNonNull(interval, "interval");
        Objects.requireNonNull(openedBy, "openedBy");
        Objects.requireNonNull(closedBy, "closedBy");
        Objects.requireNonNull(receivedFrom, "receivedFrom");
        Objects.requireNonNull(deliveredTo, "deliveredTo");
    }

    @Override
    public String toString() {
        return Span.describe(this);
    }
}
