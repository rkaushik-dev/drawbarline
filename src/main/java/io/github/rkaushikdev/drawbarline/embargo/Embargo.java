package io.github.rkaushikdev.drawbarline.embargo;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.clock.Term;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.mileage.Mileage;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One restriction, from a date to a date: how a railroad says stop.
 *
 * <p>An embargo names what it stops: a station, a commodity family, a connection, or some
 * combination, and a movement is caught only when every stated field matches. An embargo that
 * restricts nothing is refused, because it is the most common thing filed by accident and it would
 * stop the whole railroad. A permit is the way around one, and only where the embargo allows them.
 */
public record Embargo(
        int number,
        LocalDate effective,
        String reason,
        Optional<String> station,
        Optional<String> commodityFamily,
        Optional<ReportingMark> road,
        Optional<LocalDate> expires,
        boolean permitsAllowed) {

    public Embargo {
        Objects.requireNonNull(effective, "effective");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(commodityFamily, "commodityFamily");
        Objects.requireNonNull(road, "road");
        Objects.requireNonNull(expires, "expires");
        if (number < 1) {
            throw new EmbargoError("an embargo is numbered from one", Details.of("number", number));
        }
        if (Text.isBlank(reason)) {
            throw new EmbargoError("an embargo carries a reason", Details.of("number", number));
        }
        station.ifPresent(Mileage::milepost);
        if (commodityFamily.isPresent() && !Text.isDigits(commodityFamily.get())) {
            throw new EmbargoError(
                    "a commodity family is digits", Details.of("number", number, "family", commodityFamily.get()));
        }
        if (station.isEmpty() && commodityFamily.isEmpty() && road.isEmpty()) {
            throw new EmbargoError(
                    "an embargo that restricts nothing stops the whole railroad", Details.of("number", number));
        }
        Term.checked(
                effective,
                expires,
                () -> new EmbargoError(
                        "an embargo cannot expire before it takes effect", Details.of("number", number)));
    }

    /** Start filing an embargo; at least one of station, commodity family and road has to follow. */
    public static Builder builder(int number, LocalDate effective, String reason) {
        return new Builder(number, effective, reason);
    }

    /** The same embargo with a last day, which is what lifting an open one does. */
    public Embargo endingOn(LocalDate on) {
        return new Embargo(number, effective, reason, station, commodityFamily, road, Optional.of(on), permitsAllowed);
    }

    /** The days the embargo stops anything. */
    public Term term() {
        return new Term(effective, expires);
    }

    public boolean inForceOn(LocalDate day) {
        return term().covers(day);
    }

    /** Whether a movement runs into this embargo. Every stated field has to match. */
    public boolean catches(Movement movement) {
        if (station.isPresent() && !station.equals(movement.station())) {
            return false;
        }
        if (commodityFamily.isPresent()
                && movement.commodity()
                        .map(commodity -> !commodity.code().within(commodityFamily.get()))
                        .orElse(true)) {
            return false;
        }
        return road.isEmpty() || road.equals(movement.road());
    }

    /** {@code embargo 101 at HRM on 01 with CN}, naming only what it restricts. */
    @Override
    public String toString() {
        List<String> parts = new ArrayList<>();
        parts.add("embargo " + number);
        station.ifPresent(one -> parts.add("at " + one));
        commodityFamily.ifPresent(one -> parts.add("on " + one));
        road.ifPresent(one -> parts.add("with " + one));
        return String.join(" ", parts);
    }

    /** Fills in the optional parts of an embargo before it is checked. */
    public static final class Builder {

        private final int number;
        private final LocalDate effective;
        private final String reason;
        private Optional<String> station = Optional.empty();
        private Optional<String> commodityFamily = Optional.empty();
        private Optional<ReportingMark> road = Optional.empty();
        private Optional<LocalDate> expires = Optional.empty();
        private boolean permitsAllowed;

        private Builder(int number, LocalDate effective, String reason) {
            this.number = number;
            this.effective = effective;
            this.reason = reason;
        }

        public Builder station(String station) {
            this.station = Optional.of(station);
            return this;
        }

        public Builder commodityFamily(String family) {
            this.commodityFamily = Optional.of(family);
            return this;
        }

        public Builder road(ReportingMark road) {
            this.road = Optional.of(road);
            return this;
        }

        public Builder expires(LocalDate expires) {
            this.expires = Optional.of(expires);
            return this;
        }

        public Builder permitsAllowed(boolean allowed) {
            this.permitsAllowed = allowed;
            return this;
        }

        public Embargo build() {
            return new Embargo(number, effective, reason, station, commodityFamily, road, expires, permitsAllowed);
        }
    }
}
