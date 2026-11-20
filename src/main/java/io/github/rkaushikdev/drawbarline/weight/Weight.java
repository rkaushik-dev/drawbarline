package io.github.rkaushikdev.drawbarline.weight;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.util.Numbers;

/**
 * A weight in whole pounds.
 *
 * <p>Everything a railroad weighs is stencilled in pounds. Light weight, load limit and gross rail
 * load all appear on the car, and the difference between two of them decides whether a load is
 * legal. Tons only exist where somebody says a car is a hundred ton car, and that names a capacity
 * class rather than a weight: see {@link NominalCapacity}.
 */
public record Weight(long pounds) implements Comparable<Weight> {

    public static final long POUNDS_PER_TON = 2000;

    /** No weight at all. */
    public static final Weight NOTHING = new Weight(0);

    public Weight {
        if (pounds < 0) {
            throw new WeightError("a weight is not negative", Details.of("pounds", pounds));
        }
    }

    /** Short tons, which is the only ton a North American railroad means. */
    public static Weight tons(long tons) {
        return new Weight(tons * POUNDS_PER_TON);
    }

    /** Decimal tons, for a report. Never for a comparison. */
    public double inTons() {
        return pounds / (double) POUNDS_PER_TON;
    }

    public Weight plus(Weight other) {
        return new Weight(pounds + other.pounds);
    }

    public Weight minus(Weight other) {
        if (other.pounds > pounds) {
            throw new WeightError(
                    "that subtraction runs past zero", Details.of("left", toString(), "right", other.toString()));
        }
        return new Weight(pounds - other.pounds);
    }

    public Weight times(long count) {
        if (count < 0) {
            throw new WeightError("a weight does not scale by a negative", Details.of("count", count));
        }
        return new Weight(pounds * count);
    }

    public boolean isLessThan(Weight other) {
        return pounds < other.pounds;
    }

    public boolean isGreaterThan(Weight other) {
        return pounds > other.pounds;
    }

    @Override
    public int compareTo(Weight other) {
        return Long.compare(pounds, other.pounds);
    }

    /** Pounds grouped by commas: {@code 263,000 lb}. */
    @Override
    public String toString() {
        return Numbers.grouped(pounds) + " lb";
    }

    /** Read the class back off a gross rail load, refusing an odd number. */
    public static NominalCapacity capacityFor(Weight grossRailLoad) {
        for (NominalCapacity capacity : NominalCapacity.values()) {
            if (capacity.grossRailLoad().equals(grossRailLoad)) {
                return capacity;
            }
        }
        throw new WeightError(
                "no capacity class has that gross rail load", Details.of("pounds", grossRailLoad.pounds()));
    }

    /** What a car may carry, which is the gross rail load less its own weight. */
    public static Weight loadLimit(Weight grossRailLoad, Weight lightWeight) {
        if (lightWeight.compareTo(grossRailLoad) >= 0) {
            throw new WeightError(
                    "a car cannot weigh its own gross rail load empty",
                    Details.of("light_weight", lightWeight.pounds(), "gross_rail_load", grossRailLoad.pounds()));
        }
        return grossRailLoad.minus(lightWeight);
    }

    /** How far past the load limit a lading sits. Nothing when it is inside. */
    public static Weight overload(Weight lading, Weight limit) {
        if (lading.compareTo(limit) <= 0) {
            return NOTHING;
        }
        return lading.minus(limit);
    }
}
