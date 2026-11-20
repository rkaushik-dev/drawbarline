package io.github.rkaushikdev.drawbarline.clearance;

import io.github.rkaushikdev.drawbarline.measure.Length;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * How high and how wide a load stands at its extreme point. A load outside every plate is a
 * dimensional load: it has to be measured, and every overhead and side clearance on the route
 * checked against it before the car moves.
 */
public record Dimensions(Length height, Length width) {

    public Dimensions {
        Objects.requireNonNull(height, "height");
        Objects.requireNonNull(width, "width");
        if (height.inches() == 0 || width.inches() == 0) {
            throw new ClearanceError("a load has a height and a width");
        }
    }

    /** The smallest published outline this fits inside, if any does. */
    public Optional<Plate> smallestPlate() {
        return Arrays.stream(Plate.values()).filter(plate -> plate.holds(this)).findFirst();
    }

    /** Whether it is outside every plate and has to be measured over the route. */
    public boolean dimensional() {
        return smallestPlate().isEmpty();
    }

    /** {@code 15'4" high 10'6" wide}. */
    @Override
    public String toString() {
        return height + " high " + width + " wide";
    }
}
