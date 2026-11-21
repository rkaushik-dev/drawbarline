package io.github.rkaushikdev.drawbarline.reports;

import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.util.Objects;

/**
 * One road's month on the trial balance: what it opened at, what moved, what it closed at.
 *
 * @param postings how many postings the month holds for the road
 */
public record BalanceLine(ReportingMark road, Money opening, Money movement, Money closing, int postings) {

    public BalanceLine {
        Objects.requireNonNull(road, "road");
        Objects.requireNonNull(opening, "opening");
        Objects.requireNonNull(movement, "movement");
        Objects.requireNonNull(closing, "closing");
    }

    /** Whether the opening and the movement come to the closing. */
    public boolean balances() {
        return opening.plus(movement).equals(closing);
    }

    /** {@code CN $0.00 + $750.00 = $750.00}. */
    @Override
    public String toString() {
        return road + " " + opening + " + " + movement + " = " + closing;
    }
}
