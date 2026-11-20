package io.github.rkaushikdev.drawbarline.events;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.EventError;
import io.github.rkaushikdev.drawbarline.limits.Limits;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.mileage.Mileage;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Something that happened to one car at one place at one moment.
 *
 * <p>The record is deliberately narrow. It says what happened, to which car, where and when, and it
 * carries the extra fields the particular kind needs. It carries no status, balance or anything
 * else derived, because the moment a record carries a derived field somebody updates the record
 * instead of adding an event and the history stops being a history.
 *
 * <p>Events recorded at the same minute have to sort the same way every time or two runs of the
 * same month disagree, so the order inside a minute is fixed by kind (see {@link #WITHIN_A_MINUTE})
 * rather than left to whatever order the rows came back in.
 *
 * @param kind what happened
 * @param car the car it happened to
 * @param when the moment it happened
 * @param station the station it happened at, which has to be on our line
 * @param road the road on the other side of an interchange, and only of an interchange
 * @param industry the track of a placement, release or pull, and only of those
 * @param waybill the waybill the car travels on, numbered from one
 * @param tenths the distance a movement covered, in tenths of a mile, and only a movement's
 * @param note what somebody wrote to explain a correction
 */
public record Event(
        EventKind kind,
        CarMark car,
        Instant when,
        String station,
        Optional<ReportingMark> road,
        Optional<String> industry,
        Optional<Integer> waybill,
        Optional<Integer> tenths,
        String note) {

    /**
     * The rank of each kind inside one minute; lower sorts earlier. A car is received before it
     * moves, moved before it is placed, and released before it is pulled.
     */
    public static final Map<EventKind, Integer> WITHIN_A_MINUTE = ranks();

    private static final DateTimeFormatter SHOWN =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT).withZone(ZoneOffset.UTC);

    public Event {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(car, "car");
        Objects.requireNonNull(when, "when");
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(road, "road");
        Objects.requireNonNull(industry, "industry");
        Objects.requireNonNull(waybill, "waybill");
        Objects.requireNonNull(tenths, "tenths");
        Objects.requireNonNull(note, "note");
        Mileage.milepost(station);

        if (kind.needsRoad() && road.isEmpty()) {
            throw new EventError("that event names the road on the other side", Details.of("kind", kind.toString()));
        }
        if (!kind.needsRoad() && road.isPresent()) {
            throw new EventError("that event has no road to name", Details.of("kind", kind.toString()));
        }

        if (kind.needsIndustry() && industry.isEmpty()) {
            throw new EventError("that event names the track", Details.of("kind", kind.toString()));
        }
        if (!kind.needsIndustry() && industry.isPresent()) {
            throw new EventError("that event has no track to name", Details.of("kind", kind.toString()));
        }

        if (kind.needsDistance()) {
            if (tenths.isEmpty()) {
                throw new EventError("a movement carries the distance", Details.of("kind", kind.toString()));
            }
            if (tenths.get() <= 0) {
                throw new EventError("a movement covers ground", Details.of("tenths", tenths.get()));
            }
        } else if (tenths.isPresent()) {
            throw new EventError("that event covers no distance", Details.of("kind", kind.toString()));
        }

        if (waybill.isPresent() && waybill.get() < 1) {
            throw new EventError("a waybill is numbered from one", Details.of("waybill", waybill.get()));
        }
        if (note.length() > Limits.NOTE) {
            throw new EventError("that note is too long to carry", Details.of("length", note.length()));
        }
    }

    /** An event that carries nothing beyond what, which car, where and when. */
    public static Event of(EventKind kind, CarMark car, Instant when, String station) {
        return builder(kind, car, when, station).build();
    }

    /** An event with the optional fields to fill in before it is built. */
    public static Builder builder(EventKind kind, CarMark car, Instant when, String station) {
        return new Builder(kind, car, when, station);
    }

    /** The sort key, which two runs of the same month have to agree on. */
    public Order order() {
        return new Order(when, WITHIN_A_MINUTE.get(kind), car.toString());
    }

    /** A copy carrying a note, which is how a correction is explained. */
    public Event withNote(String note) {
        return new Event(kind, car, when, station, road, industry, waybill, tenths, note);
    }

    /** {@code 2025-01-14 14:30Z DBL 104207 received at ESC CN}: the track stands in for the station. */
    @Override
    public String toString() {
        String where = industry.filter(track -> !track.isEmpty()).orElse(station);
        String who = road.map(mark -> " " + mark).orElse("");
        return SHOWN.format(when) + "Z " + car + " " + kind + " at " + where + who;
    }

    private static Map<EventKind, Integer> ranks() {
        Map<EventKind, Integer> found = new EnumMap<>(EventKind.class);
        found.put(EventKind.RECEIVED, 0);
        found.put(EventKind.MOVED, 1);
        found.put(EventKind.NOTIFIED, 2);
        found.put(EventKind.CONSTRUCTIVE, 3);
        found.put(EventKind.PLACED, 4);
        found.put(EventKind.WEIGHED, 5);
        found.put(EventKind.BAD_ORDERED, 6);
        found.put(EventKind.REPAIRED, 7);
        found.put(EventKind.HELD, 8);
        found.put(EventKind.FREED, 9);
        found.put(EventKind.RELEASED, 10);
        found.put(EventKind.PULLED, 11);
        found.put(EventKind.DELIVERED, 12);
        return Collections.unmodifiableMap(found);
    }

    /** Where an event sorts: by its moment, then its kind's rank in the minute, then the car. */
    public record Order(Instant when, int rank, String car) implements Comparable<Order> {

        private static final Comparator<Order> NATURAL =
                Comparator.comparing(Order::when).thenComparingInt(Order::rank).thenComparing(Order::car);

        @Override
        public int compareTo(Order other) {
            return NATURAL.compare(this, other);
        }
    }

    /**
     * Fills in the fields a kind needs. A field set to {@code null} is left absent, which is how a
     * row with an empty column reads back.
     */
    public static final class Builder {

        private final EventKind kind;
        private final CarMark car;
        private final Instant when;
        private final String station;
        private ReportingMark road;
        private String industry;
        private Integer waybill;
        private Integer tenths;
        private String note = "";

        private Builder(EventKind kind, CarMark car, Instant when, String station) {
            this.kind = kind;
            this.car = car;
            this.when = when;
            this.station = station;
        }

        public Builder road(ReportingMark road) {
            this.road = road;
            return this;
        }

        public Builder industry(String industry) {
            this.industry = industry;
            return this;
        }

        public Builder waybill(Integer waybill) {
            this.waybill = waybill;
            return this;
        }

        public Builder tenths(Integer tenths) {
            this.tenths = tenths;
            return this;
        }

        public Builder note(String note) {
            this.note = note == null ? "" : note;
            return this;
        }

        public Event build() {
            return new Event(
                    kind,
                    car,
                    when,
                    station,
                    Optional.ofNullable(road),
                    Optional.ofNullable(industry),
                    Optional.ofNullable(waybill),
                    Optional.ofNullable(tenths),
                    note);
        }
    }
}
