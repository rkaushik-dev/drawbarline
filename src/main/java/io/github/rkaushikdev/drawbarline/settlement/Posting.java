package io.github.rkaushikdev.drawbarline.settlement;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.SettlementError;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One line of the account with a road. Positive is money we owe them; negative is money they owe
 * us.
 *
 * <p>There is no second flag saying which way the money runs, because two representations of a
 * direction is how a statement ends up backwards. A posting falls inside the month it is posted
 * to, except an adjustment, which is dated when it was made and names the month it corrects.
 *
 * @param reference what the other road reads on the statement line, empty when nothing is said
 */
public record Posting(
        ReportingMark road, Period period, PostingKind kind, Money amount, LocalDate on, String reference) {

    public Posting {
        Objects.requireNonNull(road, "road");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(on, "on");
        Objects.requireNonNull(reference, "reference");
        if (!period.covers(on) && kind != PostingKind.ADJUSTMENT) {
            throw new SettlementError(
                    "a posting falls inside the month it is posted to",
                    Details.of("period", period.toString(), "on", on.toString()));
        }
    }

    /** A posting with nothing written against it. */
    public Posting(ReportingMark road, Period period, PostingKind kind, Money amount, LocalDate on) {
        this(road, period, kind, amount, on, "");
    }

    /** Whether the money runs from us to the road. */
    public boolean weOwe() {
        return amount.cents() > 0;
    }

    /** Whether the money runs from the road to us. */
    public boolean theyOwe() {
        return amount.cents() < 0;
    }

    /** {@code 2025-01 car hire to CN $1,200.00}, the direction in words and the amount unsigned. */
    @Override
    public String toString() {
        String way = weOwe() ? "to" : "from";
        return period + " " + kind.text() + " " + way + " " + road + " " + amount.abs();
    }
}
