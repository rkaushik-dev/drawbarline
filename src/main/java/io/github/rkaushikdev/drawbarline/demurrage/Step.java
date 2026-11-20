package io.github.rkaushikdev.drawbarline.demurrage;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.TariffError;
import io.github.rkaushikdev.drawbarline.money.Money;

/**
 * A daily price that runs through a numbered chargeable day, or, when {@code through} is zero,
 * for as long as the car is held.
 *
 * @param through the last chargeable day the price covers, or zero for the open ended last step
 * @param cents the price of each of those days
 */
public record Step(int through, long cents) {

    public Step {
        if (through < 0) {
            throw new TariffError("a step runs through a day or through the end", Details.of("through", through));
        }
        if (cents < 0) {
            throw new TariffError("a step does not pay the customer", Details.of("cents", cents));
        }
    }

    /** The last step, which runs for as long as the car is held. */
    public boolean openEnded() {
        return through == 0;
    }

    /** {@code $100.00 through day 2}, or {@code $200.00 and thereafter} for the last step. */
    @Override
    public String toString() {
        String where = openEnded() ? "and thereafter" : "through day " + through;
        return new Money(cents) + " " + where;
    }
}
