package io.github.rkaushikdev.drawbarline.embargo;

import io.github.rkaushikdev.drawbarline.lading.Commodity;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import java.util.Objects;
import java.util.Optional;

/**
 * A movement as an embargo sees it: where it goes, what it carries, and which connection it is
 * with. Anything left unsaid is unknown, and an embargo that restricts on it does not catch the
 * movement.
 */
public record Movement(Optional<String> station, Optional<Commodity> commodity, Optional<ReportingMark> road) {

    public Movement {
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(commodity, "commodity");
        Objects.requireNonNull(road, "road");
    }

    /** A movement nothing is known about yet. */
    public static Movement anywhere() {
        return new Movement(Optional.empty(), Optional.empty(), Optional.empty());
    }

    public Movement atStation(String station) {
        return new Movement(Optional.of(station), commodity, road);
    }

    public Movement carrying(Commodity commodity) {
        return new Movement(station, Optional.of(commodity), road);
    }

    public Movement byRoad(ReportingMark road) {
        return new Movement(station, commodity, Optional.of(road));
    }
}
