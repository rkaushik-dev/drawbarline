package io.github.rkaushikdev.drawbarline.hazmat;

import io.github.rkaushikdev.drawbarline.Details;
import java.util.Objects;
import java.util.Optional;

/**
 * One position in a train, counted from one at the head end, whether the car there is placarded
 * and with what class, and whether it carries people.
 */
public record Standing(int position, boolean placarded, Optional<HazardClass> hazard, boolean occupied) {

    public Standing {
        Objects.requireNonNull(hazard, "hazard");
        if (position < 1) {
            throw new HazmatError("positions count from one", Details.of("position", position));
        }
        if (placarded && hazard.isEmpty()) {
            throw new HazmatError("a placarded car names its class", Details.of("position", position));
        }
        if (!placarded && hazard.isPresent()) {
            throw new HazmatError("an unplacarded car has no class", Details.of("position", position));
        }
    }

    /** An unoccupied car, placarded or not, with no class named. */
    public Standing(int position, boolean placarded) {
        this(position, placarded, Optional.empty(), false);
    }

    /** An unoccupied car with a class named. */
    public Standing(int position, boolean placarded, HazardClass hazard) {
        this(position, placarded, Optional.of(hazard), false);
    }
}
