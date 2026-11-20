package io.github.rkaushikdev.drawbarline.interchange;

import java.time.Duration;
import java.util.Locale;

/**
 * A span of time written the way an operator reads one: {@code 0:30:00}, {@code 2 days, 1:00:00},
 * with the microseconds only when there are some.
 *
 * <p>A negative span is written as whole days below zero and a positive remainder, so a minute
 * before nothing reads {@code -1 day, 23:59:00}.
 */
final class Durations {

    private static final long MICROS_PER_SECOND = 1_000_000L;
    private static final long SECONDS_PER_DAY = 86_400L;

    private Durations() {}

    static String text(Duration span) {
        long micros = Math.floorDiv(span.getNano(), 1000) + span.getSeconds() * MICROS_PER_SECOND;
        long days = Math.floorDiv(micros, SECONDS_PER_DAY * MICROS_PER_SECOND);
        long rest = Math.floorMod(micros, SECONDS_PER_DAY * MICROS_PER_SECOND);
        long seconds = rest / MICROS_PER_SECOND;
        long fraction = rest % MICROS_PER_SECOND;
        String clock = String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60);
        if (days != 0) {
            clock = String.format(Locale.ROOT, "%d day%s, ", days, Math.abs(days) != 1 ? "s" : "") + clock;
        }
        if (fraction != 0) {
            clock += String.format(Locale.ROOT, ".%06d", fraction);
        }
        return clock;
    }
}
