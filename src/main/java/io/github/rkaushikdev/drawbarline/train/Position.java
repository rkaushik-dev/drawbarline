package io.github.rkaushikdev.drawbarline.train;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.fleet.Car;
import io.github.rkaushikdev.drawbarline.hazmat.Placard;
import io.github.rkaushikdev.drawbarline.hazmat.Standing;
import io.github.rkaushikdev.drawbarline.lading.Lading;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.util.Objects;
import java.util.Optional;

/**
 * One car in a train, in the place it stands, loaded or empty, and the placard on its sides.
 *
 * <p>A hazardous load always travels under a placard. An empty may carry one too: a tank that last
 * held a hazardous commodity keeps its placards until it is cleaned and is handled as if it were
 * still loaded. What a placard may not do is ride on a load that is not hazardous, because then the
 * placard is telling the crew something false about the car.
 *
 * @param placard what is on the car's sides, absent when it is not placarded
 */
public record Position(Car car, Optional<Lading> lading, Optional<Placard> placard) {

    public Position {
        Objects.requireNonNull(car, "car");
        Objects.requireNonNull(lading, "lading");
        Objects.requireNonNull(placard, "placard");
        if (lading.filter(Lading::hazardous).isPresent() && placard.isEmpty()) {
            throw new ConsistError(
                    "a hazardous load travels under a placard",
                    Details.of("car", car.mark().toString()));
        }
        if (lading.filter(one -> !one.hazardous()).isPresent() && placard.isPresent()) {
            throw new ConsistError(
                    "a placard goes on a hazardous load or its residue",
                    Details.of("car", car.mark().toString()));
        }
    }

    /** A car with no placard on it, loaded or empty. */
    public Position(Car car, Optional<Lading> lading) {
        this(car, lading, Optional.empty());
    }

    /** An empty car. */
    public Position(Car car) {
        this(car, Optional.empty());
    }

    /** A loaded car. */
    public Position(Car car, Lading lading) {
        this(car, Optional.of(lading));
    }

    /** A hazardous load under its placard. */
    public Position(Car car, Lading lading, Placard placard) {
        this(car, Optional.of(lading), Optional.of(placard));
    }

    /** An empty still placarded for the residue of what it last carried. */
    public static Position residue(Car car, Placard placard) {
        return new Position(car, Optional.empty(), Optional.of(placard));
    }

    public boolean loaded() {
        return lading.isPresent();
    }

    /** What the car weighs on the rail, which is its own weight plus the load. */
    public Weight weight() {
        return lading.map(one -> car.lightWeight().plus(one.weight())).orElse(car.lightWeight());
    }

    /** Tons on the rail, rounded down the way a tonnage rating is read. */
    public long tons() {
        return Math.floorDiv(weight().pounds(), Weight.POUNDS_PER_TON);
    }

    /** Where the car stands as the placard rules read it, at a position counted from one. */
    public Standing standing(int at) {
        return new Standing(at, placard.isPresent(), placard.map(Placard::hazard), false);
    }

    /** {@code CN 400100 wheat}, or {@code empty}. */
    @Override
    public String toString() {
        return car.mark() + " " + lading.map(one -> one.commodity().name()).orElse("empty");
    }
}
