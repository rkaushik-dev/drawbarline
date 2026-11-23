package io.github.rkaushikdev.drawbarline.demurrage;

import io.github.rkaushikdev.drawbarline.money.Money;
import java.util.Objects;

/**
 * What a month came to under an average agreement: the credits and debits the shipper's cars
 * earned, the money owed on the debits the credits did not cancel, and the late days past the
 * offset with what they came to at the tariff.
 *
 * @param amount what the net debits come to at the agreement's rate
 * @param late the chargeable days past the offset, every car together
 * @param lateAmount what the late days come to at the tariff
 */
public record Settlement(String shipper, int credits, int debits, Money amount, int late, Money lateAmount) {

    public Settlement {
        Objects.requireNonNull(shipper, "shipper");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(lateAmount, "lateAmount");
    }

    /** A month with no car held past the offset. */
    public Settlement(String shipper, int credits, int debits, Money amount) {
        this(shipper, credits, debits, amount, 0, Money.ZERO);
    }

    /** The debits left once every credit has cancelled one, never below none. */
    public int netDebits() {
        return Math.max(debits - credits, 0);
    }

    /** Credits the shipper earned and had nothing to spend on. */
    public int wastedCredits() {
        return Math.max(credits - debits, 0);
    }

    /** The averaged debits and the late days together, which is the whole bill. */
    public Money total() {
        return amount.plus(lateAmount);
    }

    /** {@code PWRGRN 1 credits 3 debits $240.00}, with {@code 2 late $400.00} when a car ran past the offset. */
    @Override
    public String toString() {
        String tail = late == 0 ? "" : " " + late + " late " + lateAmount;
        return shipper + " " + credits + " credits " + debits + " debits " + amount + tail;
    }
}
