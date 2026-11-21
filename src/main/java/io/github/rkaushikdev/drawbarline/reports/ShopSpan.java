package io.github.rkaushikdev.drawbarline.reports;

import io.github.rkaushikdev.drawbarline.clock.Interval;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.util.Numbers;
import java.util.Objects;

/**
 * One car set out bad order, and possibly back in service.
 *
 * @param interval from the bad order to the repair, or to the cut off while it is still down
 * @param station where the car was set out
 * @param stillDown whether no repair has been recorded yet
 */
public record ShopSpan(CarMark car, Interval interval, String station, boolean stillDown) {

    /** Minutes in a day, which is the unit the shop is judged in. */
    public static final int MINUTES_PER_DAY = 24 * 60;

    public ShopSpan {
        Objects.requireNonNull(car, "car");
        Objects.requireNonNull(interval, "interval");
        Objects.requireNonNull(station, "station");
    }

    /** Whole minutes down. */
    public long minutes() {
        return interval.minutes();
    }

    /** Days down, with the part day as a fraction. */
    public double days() {
        return minutes() / (double) MINUTES_PER_DAY;
    }

    /** {@code CN 400100 4.0 days back}, or {@code still down}. */
    @Override
    public String toString() {
        String tail = stillDown ? "still down" : "back";
        return car + " " + Numbers.fixed(days(), 1) + " days " + tail;
    }
}
