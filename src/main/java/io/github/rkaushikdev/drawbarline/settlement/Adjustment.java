package io.github.rkaushikdev.drawbarline.settlement;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.SettlementError;
import io.github.rkaushikdev.drawbarline.limits.Limits;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A correction to a month that has already been agreed.
 *
 * <p>A closed month is closed. When something in it turns out to be wrong, and something always
 * does, the answer is not to open it: it is to post an adjustment into the current month that says
 * which closed month it corrects and why. Both roads then see the same two numbers, the month as it
 * was agreed and the correction, and nobody has to explain why last February changed.
 *
 * @param corrects the closed month that was wrong
 * @param postedIn the open month the correction goes into
 */
public record Adjustment(
        ReportingMark road, Period corrects, Period postedIn, Money amount, LocalDate on, String reason) {

    public Adjustment {
        Objects.requireNonNull(road, "road");
        Objects.requireNonNull(corrects, "corrects");
        Objects.requireNonNull(postedIn, "postedIn");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(on, "on");
        Objects.requireNonNull(reason, "reason");
        if (amount.isZero()) {
            throw new SettlementError("an adjustment moves something", Details.of("road", road.toString()));
        }
        if (Text.isBlank(reason)) {
            throw new SettlementError("an adjustment carries a reason", Details.of("road", road.toString()));
        }
        if (corrects.compareTo(postedIn) >= 0) {
            throw new SettlementError(
                    "an adjustment is posted after the month it corrects",
                    Details.of("corrects", corrects.toString(), "posted_in", postedIn.toString()));
        }
    }

    /** What the other road reads on the statement line, cut to the reference field. */
    public String reference() {
        return clipped("adjusts " + corrects.label() + ": " + reason);
    }

    /** {@code $45.00 from CN adjusting 2025-01}. */
    @Override
    public String toString() {
        String way = amount.cents() > 0 ? "to" : "from";
        return amount.abs() + " " + way + " " + road + " adjusting " + corrects;
    }

    /**
     * Put an adjustment into the open month it is posted in, naming the closed one it corrects.
     * A month that is still open is corrected in place, so an adjustment to it is refused.
     */
    public static Posting postAdjustment(Ledger ledger, PeriodRegister periods, Adjustment adjustment) {
        if (periods.isOpen(adjustment.corrects())) {
            throw new SettlementError(
                    "that month is still open, so correct it there",
                    Details.of("corrects", adjustment.corrects().toString()));
        }
        return ledger.post(new Posting(
                adjustment.road(),
                adjustment.postedIn(),
                PostingKind.ADJUSTMENT,
                adjustment.amount(),
                adjustment.on(),
                adjustment.reference()));
    }

    /** The first {@link Limits#REFERENCE} characters of the text, counting code points. */
    static String clipped(String text) {
        if (text.codePointCount(0, text.length()) <= Limits.REFERENCE) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, Limits.REFERENCE));
    }
}
