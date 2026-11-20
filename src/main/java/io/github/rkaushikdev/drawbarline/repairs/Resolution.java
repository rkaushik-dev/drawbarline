package io.github.rkaushikdev.drawbarline.repairs;

import io.github.rkaushikdev.drawbarline.BillingError;
import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.util.Objects;

/**
 * How an exception was answered: the outcome and how much of the disputed amount was allowed.
 *
 * @param allowed how much of the disputed amount the billing road gave up
 */
public record Resolution(Objection objection, Outcome outcome, Money allowed) {

    public Resolution {
        Objects.requireNonNull(objection, "objection");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(allowed, "allowed");
        if (allowed.cents() < 0) {
            throw new BillingError("an allowance is not negative");
        }
        if (allowed.isGreaterThan(objection.disputed())) {
            throw new BillingError(
                    "more was allowed than was ever disputed", Details.of("card", objection.cardNumber()));
        }
    }

    /** What the owner still pays of the disputed amount. */
    public Money stillOwed() {
        return objection.disputed().minus(allowed);
    }

    /** The exception, the outcome and the allowance: {@code ... allowed in full $200.00}. */
    @Override
    public String toString() {
        return objection + " " + outcome.value() + " " + allowed;
    }
}
