package io.github.rkaushikdev.drawbarline.network;

import io.github.rkaushikdev.drawbarline.ConfigurationError;
import io.github.rkaushikdev.drawbarline.Details;
import java.util.Objects;

/** A station where a foreign road hands cars over or takes them away. */
public record Junction(Station station, Road road) {

    public Junction {
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(road, "road");
        if (road.home()) {
            throw new ConfigurationError("a junction is with a foreign road", Details.of("station", station.code()));
        }
    }

    /** The station and the road: {@code ESC/CN}. */
    public String code() {
        return station.code() + "/" + road.mark();
    }

    /** {@code CN at Escanaba}. */
    @Override
    public String toString() {
        return road.mark() + " at " + station.name();
    }
}
