package io.github.rkaushikdev.drawbarline.train;

import io.github.rkaushikdev.drawbarline.fleet.Car;
import io.github.rkaushikdev.drawbarline.lading.Lading;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.util.Objects;
import java.util.Optional;

/** One car in a train, in the place it stands, loaded or empty. */
public record Position(Car car, Optional<Lading> lading) {

    public Position {
        Objects.requireNonNull(car, "car");
        Objects.requireNonNull(lading, "lading");
    }

    /** An empty car. */
    public Position(Car car) {
        this(car, Optional.empty());
    }

    /** A loaded car. */
    public Position(Car car, Lading lading) {
        this(car, Optional.of(lading));
    }

    public boolean loaded() {
        return lading.isPresent();
    }

    /** What the car weighs on the rail, which is its own weight plus the load. */
    public Weight weight() {
        return lading.map(one -> car.lightWeight().plus(one.weight())).orElse(car.lightWeight());
    }

    /** {@code CN 400100 wheat}, or {@code empty}. */
    @Override
    public String toString() {
        return car.mark() + " " + lading.map(one -> one.commodity().name()).orElse("empty");
    }
}
