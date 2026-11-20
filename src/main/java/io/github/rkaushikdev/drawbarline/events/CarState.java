package io.github.rkaushikdev.drawbarline.events;

import io.github.rkaushikdev.drawbarline.marks.CarMark;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Where a car is and what it is doing, as of the last event applied.
 *
 * <p>Nothing stores this. It is replayed out of the car's stream every time it is asked for (see
 * {@link Replay}), so a correction filed for last Tuesday changes today's answer without anybody
 * going back to update a row.
 *
 * @param car the car
 * @param asOf the moment of the last event applied, absent when none has been
 * @param onLine whether the car is on our rail
 * @param station where the car was last seen
 * @param industry the track the car is placed on, absent when it is not placed
 * @param waybill the waybill it came to us on
 * @param badOrdered whether it is set out for repair
 * @param held whether it is held under an embargo
 * @param receivedAt when we last took it in interchange
 * @param placedAt when it was placed on the track it is on
 * @param releasedAt when the shipper released it, while it waits to be pulled
 * @param tenthsRun how far it has moved on our rail, in tenths of a mile
 * @param applied how many events went into this state
 */
public record CarState(
        CarMark car,
        Optional<Instant> asOf,
        boolean onLine,
        Optional<String> station,
        Optional<String> industry,
        Optional<Integer> waybill,
        boolean badOrdered,
        boolean held,
        Optional<Instant> receivedAt,
        Optional<Instant> placedAt,
        Optional<Instant> releasedAt,
        int tenthsRun,
        int applied) {

    public CarState {
        Objects.requireNonNull(car, "car");
        Objects.requireNonNull(asOf, "asOf");
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(industry, "industry");
        Objects.requireNonNull(waybill, "waybill");
        Objects.requireNonNull(receivedAt, "receivedAt");
        Objects.requireNonNull(placedAt, "placedAt");
        Objects.requireNonNull(releasedAt, "releasedAt");
    }

    /** A car nothing has happened to: off line, nowhere, with nothing applied. */
    public CarState(CarMark car) {
        this(
                car,
                Optional.empty(),
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                false,
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0,
                0);
    }

    /** Whether the car stands on an industry track. */
    public boolean placed() {
        return industry.isPresent();
    }

    /** Released by the shipper and still sitting on their track. */
    public boolean awaitingPull() {
        return placed() && releasedAt.isPresent();
    }

    /** Whether the car can be moved: on our rail, not bad ordered and not held. */
    public boolean movable() {
        return onLine && !badOrdered && !held;
    }

    /** {@code DBL 104207 at ESCPAP1}, or {@code DBL 104207 off line}. */
    @Override
    public String toString() {
        if (!onLine) {
            return car + " off line";
        }
        return car + " at " + industry.or(() -> station).orElse("somewhere");
    }

    /** A copy to change, for the replay that moves a state on by one event. */
    Change change() {
        return new Change(this);
    }

    /** The fields of a state being moved on by one event. */
    static final class Change {

        private final CarMark car;
        private Instant asOf;
        private boolean onLine;
        private String station;
        private String industry;
        private Integer waybill;
        private boolean badOrdered;
        private boolean held;
        private Instant receivedAt;
        private Instant placedAt;
        private Instant releasedAt;
        private int tenthsRun;
        private int applied;

        private Change(CarState from) {
            car = from.car;
            asOf = from.asOf.orElse(null);
            onLine = from.onLine;
            station = from.station.orElse(null);
            industry = from.industry.orElse(null);
            waybill = from.waybill.orElse(null);
            badOrdered = from.badOrdered;
            held = from.held;
            receivedAt = from.receivedAt.orElse(null);
            placedAt = from.placedAt.orElse(null);
            releasedAt = from.releasedAt.orElse(null);
            tenthsRun = from.tenthsRun;
            applied = from.applied;
        }

        /** The event that moves the state on: its moment is the new as-of, and it counts. */
        Change by(Event event) {
            asOf = event.when();
            applied += 1;
            return this;
        }

        Change onLine(boolean onLine) {
            this.onLine = onLine;
            return this;
        }

        Change station(String station) {
            this.station = station;
            return this;
        }

        Change industry(String industry) {
            this.industry = industry;
            return this;
        }

        Change waybill(Integer waybill) {
            this.waybill = waybill;
            return this;
        }

        Change badOrdered(boolean badOrdered) {
            this.badOrdered = badOrdered;
            return this;
        }

        Change held(boolean held) {
            this.held = held;
            return this;
        }

        Change receivedAt(Instant receivedAt) {
            this.receivedAt = receivedAt;
            return this;
        }

        Change placedAt(Instant placedAt) {
            this.placedAt = placedAt;
            return this;
        }

        Change releasedAt(Instant releasedAt) {
            this.releasedAt = releasedAt;
            return this;
        }

        Change ran(int tenths) {
            this.tenthsRun += tenths;
            return this;
        }

        CarState done() {
            return new CarState(
                    car,
                    Optional.ofNullable(asOf),
                    onLine,
                    Optional.ofNullable(station),
                    Optional.ofNullable(industry),
                    Optional.ofNullable(waybill),
                    badOrdered,
                    held,
                    Optional.ofNullable(receivedAt),
                    Optional.ofNullable(placedAt),
                    Optional.ofNullable(releasedAt),
                    tenthsRun,
                    applied);
        }
    }
}
