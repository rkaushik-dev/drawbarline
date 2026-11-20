package io.github.rkaushikdev.drawbarline.clock;

import io.github.rkaushikdev.drawbarline.Details;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/** A half open span of time, closed at the start and open at the end. */
public record Interval(Instant start, Instant end) {

    private static final DateTimeFormatter SHOWN =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT).withZone(ZoneOffset.UTC);

    public Interval {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (end.isBefore(start)) {
            throw new ClockError(
                    "an interval does not end before it starts",
                    Details.of("start", Clock.iso(start), "end", Clock.iso(end)));
        }
    }

    /** Whether no time passes between the two ends. */
    public boolean isEmpty() {
        return start.equals(end);
    }

    /** Whole minutes. Nothing on a railroad is billed finer than this. */
    public long minutes() {
        return Duration.between(start, end).getSeconds() / Clock.SECONDS_PER_MINUTE;
    }

    /** Hours to two places, the nearest hundredth with a tie going to the even one, which is the unit car hire is settled in. */
    public long hundredthHours() {
        return (long) Math.rint(minutes() * 100 / (double) Clock.MINUTES_PER_HOUR);
    }

    public boolean contains(Instant moment) {
        return !moment.isBefore(start) && moment.isBefore(end);
    }

    public boolean overlaps(Interval other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    /** The part of this interval inside a window, possibly empty. */
    public Interval clip(Interval window) {
        Instant from = start.isAfter(window.start) ? start : window.start;
        Instant to = end.isBefore(window.end) ? end : window.end;
        if (to.isBefore(from)) {
            return new Interval(window.start, window.start);
        }
        return new Interval(from, to);
    }

    public Interval shift(Duration by) {
        return new Interval(start.plus(by), end.plus(by));
    }

    /** {@code 2025-01-01 06:00Z to 2025-01-02 07:30Z}. */
    @Override
    public String toString() {
        return SHOWN.format(start) + "Z to " + SHOWN.format(end) + "Z";
    }
}
