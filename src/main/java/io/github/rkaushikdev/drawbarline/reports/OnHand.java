package io.github.rkaushikdev.drawbarline.reports;

import io.github.rkaushikdev.drawbarline.clock.Clock;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A car standing on our rail, and how long it has been.
 *
 * @param station where it was last seen, empty when nobody has said
 * @param industry the track it is placed on, absent when it is not placed
 * @param since when we last took it in interchange
 * @param minutes whole minutes from then to the moment asked about
 */
public record OnHand(
        CarMark car,
        String station,
        Optional<String> industry,
        Instant since,
        long minutes,
        boolean badOrdered,
        boolean held) {

    public OnHand {
        Objects.requireNonNull(car, "car");
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(industry, "industry");
        Objects.requireNonNull(since, "since");
    }

    /** The time on hand in hundredths of an hour, the nearest hundredth with a tie going to the even one. */
    public long hundredthHours() {
        return (long) Math.rint(minutes * 100 / (double) Clock.MINUTES_PER_HOUR);
    }
}
