package io.github.rkaushikdev.drawbarline.train;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.measure.Length;
import io.github.rkaushikdev.drawbarline.mileage.Mileage;
import io.github.rkaushikdev.drawbarline.weight.NominalCapacity;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.util.Objects;

/**
 * What a stretch of railroad will take: the tonnage rating, the longest train its sidings hold, and
 * the heaviest single car its bridges are rated for.
 *
 * @param maxCarWeight the most one car may weigh on the rail, its own weight and its load together
 */
public record District(String line, String name, int maxTons, Length maxLength, Weight maxCarWeight) {

    public District {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(maxLength, "maxLength");
        Objects.requireNonNull(maxCarWeight, "maxCarWeight");
        if (!Mileage.MAIN.equals(line) && !Mileage.BRANCH.equals(line)) {
            throw new ConsistError("this road has a main and a branch", Details.of("line", line));
        }
        if (maxTons < 1) {
            throw new ConsistError("a district takes at least a ton", Details.of("name", name));
        }
        if (maxCarWeight.pounds() < 1) {
            throw new ConsistError("a district takes a car that weighs something", Details.of("name", name));
        }
    }

    /** A district rated for the heaviest car in interchange, 286,000 pounds. */
    public District(String line, String name, int maxTons, Length maxLength) {
        this(line, name, maxTons, maxLength, NominalCapacity.ONE_TEN.grossRailLoad());
    }

    /** Whether one car is light enough for the district's bridges. */
    public boolean takesCar(Position car) {
        return !car.weight().isGreaterThan(maxCarWeight);
    }
}
