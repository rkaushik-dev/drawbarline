package io.github.rkaushikdev.drawbarline.settlement;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.SettlementError;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * One road, one statement, one amount they are not paying.
 *
 * <p>A road that disagrees with a statement does not simply not pay it. They pay the part they
 * agree with and raise a dispute for the rest, and that disputed amount sits on the account until
 * somebody settles it.
 *
 * @param note what the road wrote with it, empty when nothing
 */
public record Dispute(
        int reference,
        ReportingMark road,
        Period period,
        Ground ground,
        Money amount,
        LocalDate raisedOn,
        String note) {

    public Dispute {
        Objects.requireNonNull(road, "road");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(ground, "ground");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(raisedOn, "raisedOn");
        Objects.requireNonNull(note, "note");
        if (reference < 1) {
            throw new SettlementError("a dispute is numbered from one", Details.of("reference", reference));
        }
        if (amount.cents() <= 0) {
            throw new SettlementError("a dispute is for an amount", Details.of("reference", reference));
        }
        if (raisedOn.isBefore(period.lastDay())) {
            throw new SettlementError(
                    "a dispute comes after the month it is against",
                    Details.of("reference", reference, "period", period.toString()));
        }
    }

    /** A dispute with nothing written on it. */
    public Dispute(int reference, ReportingMark road, Period period, Ground ground, Money amount, LocalDate raisedOn) {
        this(reference, road, period, ground, amount, raisedOn, "");
    }

    /** Whole days since the dispute was raised, refusing a day before it. */
    public long daysOld(LocalDate asOf) {
        if (asOf.isBefore(raisedOn)) {
            throw new SettlementError("that date is before the dispute", Details.of("reference", reference));
        }
        return ChronoUnit.DAYS.between(raisedOn, asOf);
    }

    /** {@code dispute 701 CN 2025-01 $450.00}. */
    @Override
    public String toString() {
        return "dispute " + reference + " " + road + " " + period + " " + amount;
    }
}
