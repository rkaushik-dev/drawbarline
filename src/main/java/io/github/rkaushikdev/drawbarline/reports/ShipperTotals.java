package io.github.rkaushikdev.drawbarline.reports;

import io.github.rkaushikdev.drawbarline.money.Money;
import java.util.Objects;

/**
 * One shipper's month of demurrage: how many cars, how many chargeable days, and what it came to.
 */
public record ShipperTotals(int cars, int days, Money amount) {

    /** Nothing yet. */
    public static final ShipperTotals NONE = new ShipperTotals(0, 0, Money.ZERO);

    public ShipperTotals {
        Objects.requireNonNull(amount, "amount");
    }

    /** These totals with one more car of so many days and so much money. */
    public ShipperTotals plus(int carDays, Money carAmount) {
        return new ShipperTotals(cars + 1, days + carDays, amount.plus(carAmount));
    }
}
