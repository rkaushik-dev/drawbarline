package io.github.rkaushikdev.drawbarline.clearance;

import io.github.rkaushikdev.drawbarline.measure.Length;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * How high and how wide a load stands at its extreme point, the height measured from the top of
 * the rail. A load outside every plate is a dimensional load: it has to be measured, and every
 * overhead and side clearance on the route checked against it before the car moves.
 *
 * <p>A shipper measures the load and the car measures the deck, and the clearance chart is in
 * height above the rail, so a load of machinery ten feet high on a flat car with a deck at four
 * feet two stands fourteen feet two at the overhead. {@link #onDeck} adds the two.
 */
public record Dimensions(Length height, Length width) {

    public Dimensions {
        Objects.requireNonNull(height, "height");
        Objects.requireNonNull(width, "width");
        if (height.inches() == 0 || width.inches() == 0) {
            throw new ClearanceError("a load has a height and a width");
        }
    }

    /** A load standing on a car deck, its height taken up to the top of the rail. */
    public static Dimensions onDeck(Length deckHeight, Length loadHeight, Length loadWidth) {
        if (loadHeight.inches() == 0) {
            throw new ClearanceError("a load on a deck stands some height above it");
        }
        return new Dimensions(deckHeight.plus(loadHeight), loadWidth);
    }

    /** Whether the load fits under a height and inside a width, both edges counted as fitting. */
    public boolean within(Length overhead, Length side) {
        return height.compareTo(overhead) <= 0 && width.compareTo(side) <= 0;
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
