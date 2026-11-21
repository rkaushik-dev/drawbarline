package io.github.rkaushikdev.drawbarline.reports;

import io.github.rkaushikdev.drawbarline.clock.Clock;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import java.util.Objects;

/**
 * One car's month, split into the parts somebody answers for.
 *
 * @param visits how many separate stays on our rail touched the window
 * @param onLineMinutes the whole time on our rail inside the window, which is the turn time
 * @param onTrackMinutes the part of it standing on a customer track
 * @param tenthsRun how far it moved inside the window, in tenths of a mile
 */
public record Cycle(CarMark car, int visits, long onLineMinutes, long onTrackMinutes, long tenthsRun) {

    public Cycle {
        Objects.requireNonNull(car, "car");
    }

    /** Time on our rail that the car was not standing on a customer track. */
    public long movingMinutes() {
        return Math.max(onLineMinutes - onTrackMinutes, 0);
    }

    /** The turn time in hundredths of an hour, the nearest hundredth with a tie going to the even one. */
    public long turnHundredths() {
        return hundredths(onLineMinutes);
    }

    /** The standing time in hundredths of an hour, rounded the same way. */
    public long standingHundredths() {
        return hundredths(onTrackMinutes);
    }

    private static long hundredths(long minutes) {
        return (long) Math.rint(minutes * 100 / (double) Clock.MINUTES_PER_HOUR);
    }
}
