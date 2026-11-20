package io.github.rkaushikdev.drawbarline.distribution;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.fleet.CarKind;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Objects;

/**
 * A shipper asking for cars of a kind on a day.
 *
 * @param number the order number, from one
 * @param shipper the shipper's code
 * @param industry the track the cars are wanted on
 * @param kind the kind of car wanted
 * @param cars how many
 * @param wantedOn the day they are wanted
 */
public record CarOrder(int number, String shipper, String industry, CarKind kind, int cars, LocalDate wantedOn) {

    public CarOrder {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(wantedOn, "wantedOn");
        if (number < 1) {
            throw new DistributionError("an order is numbered from one", Details.of("number", number));
        }
        if (cars < 1) {
            throw new DistributionError("an order asks for at least one car", Details.of("number", number));
        }
        if (Text.isBlank(shipper) || Text.isBlank(industry)) {
            throw new DistributionError("an order names a shipper and a track", Details.of("number", number));
        }
    }

    /** Oldest order first, then by number, which is what a shipper can check. */
    public Priority priority() {
        return new Priority(wantedOn, number);
    }

    /** {@code order 1 ESCPAP 4 covered hopper for 2025-01-14}. */
    @Override
    public String toString() {
        return "order " + number + " " + shipper + " " + cars + " " + kind.description() + " for " + wantedOn;
    }

    /** Where an order stands in the queue: the day it is wanted, then its number. */
    public record Priority(LocalDate wantedOn, int number) implements Comparable<Priority> {

        private static final Comparator<Priority> ORDER =
                Comparator.comparing(Priority::wantedOn).thenComparingInt(Priority::number);

        @Override
        public int compareTo(Priority other) {
            return ORDER.compare(this, other);
        }
    }
}
