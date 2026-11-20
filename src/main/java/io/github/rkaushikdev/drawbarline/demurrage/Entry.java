package io.github.rkaushikdev.drawbarline.demurrage;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.TariffError;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import java.util.Objects;

/**
 * What one car did to a month's balance under an average agreement: a credit for a car turned back
 * inside the first free day, or a debit for every chargeable day, never both.
 */
public record Entry(CarMark car, String industry, int credits, int debits) {

    public Entry {
        Objects.requireNonNull(car, "car");
        Objects.requireNonNull(industry, "industry");
        if (credits != 0 && debits != 0) {
            throw new TariffError("a car earns a credit or owes debits, never both", Details.of("car", car.toString()));
        }
    }

    /**
     * Read one detention as a credit or as debits. A car with chargeable days owes one debit for
     * each; one released on or before the day its clock started earns a credit; anything between
     * earns nothing.
     */
    public static Entry of(Detention detention) {
        if (detention.chargeable() != 0) {
            return new Entry(detention.car(), detention.industry(), 0, detention.chargeable());
        }
        int earned = detention.countedTo().isAfter(detention.clockStarts()) ? 0 : 1;
        return new Entry(detention.car(), detention.industry(), earned, 0);
    }

    /** {@code CN 400100 1 credit} or {@code CN 400100 3 debits}. */
    @Override
    public String toString() {
        if (credits != 0) {
            return car + " " + credits + " credit";
        }
        return car + " " + debits + " debits";
    }
}
