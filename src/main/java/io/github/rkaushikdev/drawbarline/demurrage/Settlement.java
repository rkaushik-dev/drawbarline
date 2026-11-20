package io.github.rkaushikdev.drawbarline.demurrage;

import io.github.rkaushikdev.drawbarline.money.Money;
import java.util.Objects;

/**
 * What a month came to under an average agreement: the credits and debits the shipper's cars
 * earned, and the money owed on the debits the credits did not cancel.
 */
public record Settlement(String shipper, int credits, int debits, Money amount) {

    public Settlement {
        Objects.requireNonNull(shipper, "shipper");
        Objects.requireNonNull(amount, "amount");
    }

    /** The debits left once every credit has cancelled one, never below none. */
    public int netDebits() {
        return Math.max(debits - credits, 0);
    }

    /** Credits the shipper earned and had nothing to spend on. */
    public int wastedCredits() {
        return Math.max(credits - debits, 0);
    }

    /** {@code PWRGRN 1 credits 3 debits $240.00}. */
    @Override
    public String toString() {
        return shipper + " " + credits + " credits " + debits + " debits " + amount;
    }
}
