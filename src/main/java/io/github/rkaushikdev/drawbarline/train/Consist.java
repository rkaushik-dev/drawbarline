package io.github.rkaushikdev.drawbarline.train;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.measure.Length;
import io.github.rkaushikdev.drawbarline.util.Text;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A train, head end first, as its wheel report lists it.
 *
 * <p>Two numbers come out of it and both stop trains: the tonnage the engines have to drag up the
 * ruling grade, and the length, limited by the shortest siding the train has to fit into. The
 * length is not the sum of the car lengths: every coupling adds the slack between two drawbars,
 * and over sixty cars that is most of a car length.
 */
public record Consist(String symbol, LocalDate day, List<Position> positions) {

    public Consist {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(day, "day");
        positions = List.copyOf(positions);
        if (Text.isBlank(symbol)) {
            throw new ConsistError("a train runs under a symbol");
        }
        Set<CarMark> marks = new HashSet<>();
        for (Position one : positions) {
            if (!marks.add(one.car().mark())) {
                throw new ConsistError("a car appears twice in the consist", Details.of("symbol", symbol));
            }
        }
    }

    public int cars() {
        return positions.size();
    }

    public int loads() {
        return (int) positions.stream().filter(Position::loaded).count();
    }

    public int empties() {
        return cars() - loads();
    }

    /** What the whole train weighs on the rail. */
    public Weight weight() {
        Weight running = Weight.NOTHING;
        for (Position one : positions) {
            running = running.plus(one.weight());
        }
        return running;
    }

    /** Trailing tons, rounded down the way a tonnage rating is read. */
    public long tons() {
        return Math.floorDiv(weight().pounds(), Weight.POUNDS_PER_TON);
    }

    /** Over the pulling faces, with the slack at every coupling counted. */
    public Length length() {
        if (positions.isEmpty()) {
            return Length.ZERO;
        }
        int overCars = positions.stream()
                .mapToInt(one -> one.car().outsideLength().inches())
                .sum();
        return new Length(overCars).plus(Train.SLACK_PER_COUPLING.times(cars() - 1));
    }

    /** Whether a car stands in the train. Anything that is not a car's mark is not. */
    public boolean carries(Object mark) {
        return positions.stream().anyMatch(one -> one.car().mark().equals(mark));
    }

    /** {@code L51 2025-01-14 3 cars 392 tons}. */
    @Override
    public String toString() {
        return symbol + " " + day + " " + cars() + " cars " + tons() + " tons";
    }
}
