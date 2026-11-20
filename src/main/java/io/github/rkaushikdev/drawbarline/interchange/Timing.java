package io.github.rkaushikdev.drawbarline.interchange;

import io.github.rkaushikdev.drawbarline.events.Event;
import java.time.Duration;
import java.util.Objects;

/** One handoff both sides recorded, at different times. */
public record Timing(Event ours, Event theirs) {

    public Timing {
        Objects.requireNonNull(ours, "ours");
        Objects.requireNonNull(theirs, "theirs");
    }

    /** How far apart the two clocks were, whichever way round. */
    public Duration apart() {
        return Duration.between(ours.when(), theirs.when()).abs();
    }

    /** Whole minutes apart. */
    public long minutesApart() {
        return apart().getSeconds() / 60;
    }

    /** Whether our record has the handoff after theirs does. */
    public boolean weAreLater() {
        return ours.when().isAfter(theirs.when());
    }

    /** {@code CN 400100 received we are 120 minutes later}. */
    @Override
    public String toString() {
        String way = weAreLater() ? "later" : "earlier";
        return ours.car() + " " + ours.kind() + " we are " + minutesApart() + " minutes " + way;
    }
}
