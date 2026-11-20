package io.github.rkaushikdev.drawbarline.repairs;

import io.github.rkaushikdev.drawbarline.BillingError;
import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.mileage.Mileage;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * The billing repair card: what one road did to one car on one day, a line for each thing it did.
 *
 * <p>The card goes to the owner and the owner either pays it or takes exception to it. Pricing a
 * card and deciding who pays for it are two different questions and the card answers neither on its
 * own: see {@link PricedCard#price}.
 *
 * @param performedBy the road that did the work and bills for it
 * @param station where the work was done, a station on our line
 */
public record RepairCard(
        int number, CarMark car, ReportingMark performedBy, LocalDate on, String station, List<Defect> defects) {

    public RepairCard {
        Objects.requireNonNull(car, "car");
        Objects.requireNonNull(performedBy, "performedBy");
        Objects.requireNonNull(on, "on");
        defects = List.copyOf(defects);
        if (number < 1) {
            throw new BillingError("a card is numbered from one", Details.of("number", number));
        }
        if (defects.isEmpty()) {
            throw new BillingError("a card with no repairs on it is not a card", Details.of("number", number));
        }
        Mileage.milepost(station);
    }

    /** The job on each line, in line order. */
    public List<JobCode> jobs() {
        return defects.stream().map(Defect::job).toList();
    }

    /** {@code card 88104 DBL 104207 by CN on 2025-01-14}. */
    @Override
    public String toString() {
        return "card " + number + " " + car + " by " + performedBy + " on " + on;
    }
}
