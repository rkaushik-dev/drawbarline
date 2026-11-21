package io.github.rkaushikdev.drawbarline.settlement;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.SettlementError;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A payment against a month.
 *
 * <p>A road pays a statement, the payment goes in against the month it settles, and the balance
 * comes down. Payments are never netted against charges in the same posting, because a road that
 * pays part of a statement is disputing the rest and the ledger has to show which part.
 *
 * @param reference what the road wrote on the payment, empty to use the remittance number
 */
public record Remittance(
        int number, ReportingMark road, Period settles, Money amount, LocalDate receivedOn, String reference) {

    public Remittance {
        Objects.requireNonNull(road, "road");
        Objects.requireNonNull(settles, "settles");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(receivedOn, "receivedOn");
        Objects.requireNonNull(reference, "reference");
        if (number < 1) {
            throw new SettlementError("a remittance is numbered from one", Details.of("number", number));
        }
        if (amount.cents() <= 0) {
            throw new SettlementError("a remittance is an amount", Details.of("number", number));
        }
        if (receivedOn.isBefore(settles.lastDay())) {
            throw new SettlementError(
                    "a payment comes after the month it settles",
                    Details.of("number", number, "settles", settles.toString()));
        }
    }

    /** A payment with nothing written on it. */
    public Remittance(int number, ReportingMark road, Period settles, Money amount, LocalDate receivedOn) {
        this(number, road, settles, amount, receivedOn, "");
    }

    /** {@code remittance 7001 CN $1,200.00 for 2025-01}. */
    @Override
    public String toString() {
        return "remittance " + number + " " + road + " " + amount + " for " + settles;
    }

    /** Put a payment in against the month it settles, on that month's last day. */
    public static Posting applyRemittance(Ledger ledger, Remittance remittance) {
        String reference =
                remittance.reference().isEmpty() ? "remittance " + remittance.number() : remittance.reference();
        return ledger.post(new Posting(
                remittance.road(),
                remittance.settles(),
                PostingKind.PAYMENT,
                remittance.amount().negate(),
                remittance.settles().lastDay(),
                Adjustment.clipped(reference)));
    }

    /** What is still owed on one road through a month, payments taken off. */
    public static Money outstanding(Ledger ledger, ReportingMark road, Period through) {
        return ledger.account(road).balanceThrough(through);
    }

    /** Whether a road has paid us more than we ever billed them. */
    public static boolean overpaid(Ledger ledger, ReportingMark road, Period through) {
        return outstanding(ledger, road, through).cents() < 0;
    }
}
