package io.github.rkaushikdev.drawbarline.lading;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.util.Objects;

/**
 * A commodity and how much of it is in the car.
 *
 * <p>An empty car has no lading at all rather than a lading of nothing, because the two are treated
 * differently everywhere: an empty moves under a different rate, a different routing rule and a
 * different demurrage clock.
 */
public record Lading(Commodity commodity, Weight weight) {

    public Lading {
        Objects.requireNonNull(commodity, "commodity");
        Objects.requireNonNull(weight, "weight");
        if (weight.pounds() == 0) {
            throw new LadingError(
                    "a loaded car weighs something, an empty one has no lading at all",
                    Details.of("commodity", commodity.code().toString()));
        }
    }

    public boolean hazardous() {
        return commodity.hazardous();
    }

    /** {@code wheat 198,000 lb}. */
    @Override
    public String toString() {
        return commodity.name() + " " + weight;
    }
}
