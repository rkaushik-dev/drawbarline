package io.github.rkaushikdev.drawbarline.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.github.rkaushikdev.drawbarline.clock.Clock;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Optional;

/**
 * Moments as a client writes them.
 *
 * <p>A client writes a moment as an ISO date and time, with an offset or without one, or as a day
 * on its own, which is midnight. Whether it carried an offset is kept, because an event's local
 * reading has to carry none and its moment has to carry one, and only the route knows which it is
 * reading.
 *
 * <p>Every report route wanted a moment or now. One place answers it, so a client passing a moment
 * with no offset and one passing the same moment with a {@code Z} on it are read the same way on
 * every route.
 */
public final class Moments {

    /** What a caller is told when a moment cannot be read. */
    static final String NOT_A_MOMENT = "Input should be a valid datetime";

    /** The kind of problem a moment that cannot be read is. */
    static final String UNREADABLE = "datetime_parsing";

    private static final int DAY_ONLY = "2025-01-14".length();

    private Moments() {}

    /**
     * A moment given by a client, in UTC, or now when none was given. A reading with no offset is
     * taken in this machine's own zone, as a reading on its clock.
     */
    public static Instant orNow(Optional<? extends Temporal> at) {
        if (at.isEmpty()) {
            return Clock.now();
        }
        return switch (at.get()) {
            case LocalDateTime local -> local.atZone(ZoneId.systemDefault()).toInstant();
            case Temporal aware -> Clock.requireAware(aware, "at");
        };
    }

    /**
     * Text read as a moment: an {@link OffsetDateTime} when it carries an offset, a {@link
     * LocalDateTime} when it does not, or nothing when it is not a moment at all. The date and the
     * time may be parted by a space as well as by a {@code T}.
     */
    static Optional<Temporal> read(String text) {
        String iso = text.length() > DAY_ONLY && text.charAt(DAY_ONLY) == ' '
                ? text.substring(0, DAY_ONLY) + "T" + text.substring(DAY_ONLY + 1)
                : text;
        try {
            if (iso.length() == DAY_ONLY) {
                return Optional.of(LocalDate.parse(iso).atStartOfDay());
            }
            TemporalAccessor found =
                    DateTimeFormatter.ISO_DATE_TIME.parseBest(iso, OffsetDateTime::from, LocalDateTime::from);
            return Optional.of(found instanceof OffsetDateTime offset ? offset : (LocalDateTime) found);
        } catch (DateTimeParseException unreadable) {
            return Optional.empty();
        }
    }

    /** A query parameter read as a moment, refused as a malformed request when it is not one. */
    static Optional<Temporal> query(String name, Optional<String> text) {
        if (text.isEmpty()) {
            return Optional.empty();
        }
        Optional<Temporal> found = read(text.get());
        if (found.isEmpty()) {
            throw new RequestRejected(new Problem(UNREADABLE, List.of("query", name), NOT_A_MOMENT));
        }
        return found;
    }

    /** A moment in a request body, as {@link #read} reads it. */
    static final class Reading extends StdDeserializer<Temporal> {

        private static final long serialVersionUID = 1L;

        Reading() {
            super(Temporal.class);
        }

        @Override
        public Temporal deserialize(JsonParser in, DeserializationContext context) throws IOException {
            if (in.currentToken() != JsonToken.VALUE_STRING) {
                throw new Refused(in, "datetime_type", NOT_A_MOMENT);
            }
            return read(in.getText()).orElseThrow(() -> new Refused(in, UNREADABLE, NOT_A_MOMENT));
        }
    }
}
