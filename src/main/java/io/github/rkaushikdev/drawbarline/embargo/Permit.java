package io.github.rkaushikdev.drawbarline.embargo;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.clock.Term;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Leave for one car to move through one embargo, for a few days.
 *
 * <p>An embargo stops everything it names, and the permit is how the road lets a particular car
 * through anyway: the load of feed the farm at Powers cannot do without, the empty the mill needs
 * back to keep its line running. A permit names the car, because a permit for "some cars" is an
 * embargo nobody is enforcing, and it runs out, because an open ended permit outlives the reason it
 * was given.
 *
 * @param embargo the number of the embargo it lets the car through
 * @param issuedTo who asked for it, which is who answers for the move
 */
public record Permit(int number, int embargo, CarMark car, LocalDate issued, LocalDate validThrough, String issuedTo) {

    public Permit {
        Objects.requireNonNull(car, "car");
        Objects.requireNonNull(issued, "issued");
        Objects.requireNonNull(validThrough, "validThrough");
        if (number < 1) {
            throw new EmbargoError("a permit is numbered from one", Details.of("number", number));
        }
        if (Text.isBlank(issuedTo)) {
            throw new EmbargoError("a permit names who asked for it", Details.of("number", number));
        }
        Term.checked(
                issued,
                Optional.of(validThrough),
                () -> new EmbargoError("a permit cannot run out before it is issued", Details.of("number", number)));
    }

    /** The days the permit lets the car through. */
    public Term term() {
        return Term.through(issued, validThrough);
    }

    /** Whether the permit lets this car through on a day. */
    public boolean covers(CarMark car, LocalDate day) {
        return this.car.equals(car) && term().covers(day);
    }

    /** {@code permit 9 on embargo 101 for CN 400100 2025-01-14 to 2025-01-17}. */
    @Override
    public String toString() {
        return "permit " + number + " on embargo " + embargo + " for " + car + " " + term();
    }
}
