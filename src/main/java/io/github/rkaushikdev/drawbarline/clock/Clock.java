package io.github.rkaushikdev.drawbarline.clock;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.network.Station;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.List;
import java.util.Locale;

/**
 * The settlement clock.
 *
 * <p>Two rules run everything that follows. Inside the system a moment is an {@link Instant} and
 * nothing else, because two roads on either side of a time zone boundary have to agree on the
 * order of events. At the edges a moment is local time at a station, because that is what the
 * clerk wrote down and what the tariff means by a day.
 *
 * <p>Between the two sits the awkward hour twice a year. On the spring morning 02:30 local does
 * not exist, and on the autumn morning 01:30 happens twice. A reading in the gap is refused
 * outright, because the clerk mistyped, and a reading in the repeated hour is taken as the first
 * pass unless the caller says it was the second. Guessing silently is how an hour of car hire goes
 * missing every November.
 */
public final class Clock {

    public static final int MINUTES_PER_HOUR = 60;
    public static final int SECONDS_PER_MINUTE = 60;

    private static final DateTimeFormatter SECONDS = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss", Locale.ROOT);
    private static final DateTimeFormatter OFFSET = DateTimeFormatter.ofPattern("xxx", Locale.ROOT);

    private Clock() {}

    /** A moment stated directly in UTC, which is what tests and files carry. */
    public static Instant utc(int year, int month, int day) {
        return utc(year, month, day, 0, 0);
    }

    /** A moment stated directly in UTC, on the hour. */
    public static Instant utc(int year, int month, int day, int hour) {
        return utc(year, month, day, hour, 0);
    }

    /** A moment stated directly in UTC. */
    public static Instant utc(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).toInstant(ZoneOffset.UTC);
    }

    /** Now, to the microsecond, which is as fine as the books store a moment. */
    public static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** Today's date in UTC. */
    public static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    /** Midnight UTC at the start of a day. */
    public static Instant midnightUtc(LocalDate day) {
        return day.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    /**
     * A moment read from outside, refused unless it carries its zone so that a wall clock reading
     * never comes to mean local time by accident.
     */
    public static Instant requireAware(Temporal moment, String what) {
        return switch (moment) {
            case Instant instant -> instant;
            case OffsetDateTime offset -> offset.toInstant();
            case ZonedDateTime zoned -> zoned.toInstant();
            default -> throw new ClockError("a moment has to carry its zone", Details.of("what", what));
        };
    }

    /** {@link #requireAware(Temporal, String)} for a value with no better name than moment. */
    public static Instant requireAware(Temporal moment) {
        return requireAware(moment, "moment");
    }

    /** Read a local clock reading at a station as a moment, taking the first pass of a repeated hour. */
    public static Instant toUtc(Station station, LocalDateTime local) {
        return toUtc(station, local, false);
    }

    /**
     * Read a local clock reading at a station as a moment.
     *
     * <p>{@code secondPass} picks the later of the two readings on the morning the clocks go back.
     * It is meaningless on every other day and is refused there, so a caller cannot leave it set
     * and quietly shift an hour.
     */
    public static Instant toUtc(Station station, LocalDateTime local, boolean secondPass) {
        List<ZoneOffset> offsets = station.tz().getRules().getValidOffsets(local);
        if (offsets.isEmpty()) {
            throw new ClockError(
                    "that reading did not happen, the clocks went forward",
                    Details.of("station", station.code(), "local", iso(local)));
        }
        if (offsets.size() == 1) {
            if (secondPass) {
                throw new ClockError(
                        "that reading happened once, so there is no second pass",
                        Details.of("station", station.code(), "local", iso(local)));
            }
            return local.toInstant(offsets.get(0));
        }
        return local.toInstant(offsets.get(secondPass ? 1 : 0));
    }

    /** What the clock at a station read at that moment. */
    public static ZonedDateTime toLocal(Station station, Instant moment) {
        return moment.atZone(station.tz());
    }

    /** A moment written as a UTC date and time with its offset: {@code 2025-01-14T14:00:00+00:00}. */
    public static String iso(Instant moment) {
        return iso(moment.atOffset(ZoneOffset.UTC));
    }

    /** A zoned reading with its offset: {@code 2025-01-14T09:00:00-05:00}. */
    public static String iso(ZonedDateTime moment) {
        return iso(moment.toOffsetDateTime());
    }

    /** An offset reading with its offset: {@code 2025-01-14T09:00:00-05:00}. */
    public static String iso(OffsetDateTime moment) {
        return iso(moment.toLocalDateTime()) + moment.format(OFFSET);
    }

    /**
     * A wall clock reading to the second, with the microseconds only when there are some: {@code
     * 2025-03-09T02:30:00}.
     */
    public static String iso(LocalDateTime local) {
        String text = local.format(SECONDS);
        int micros = local.getNano() / 1000;
        return micros == 0 ? text : text + String.format(Locale.ROOT, ".%06d", micros);
    }
}
