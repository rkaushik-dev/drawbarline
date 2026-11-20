package io.github.rkaushikdev.drawbarline.repairs;

import io.github.rkaushikdev.drawbarline.BillingError;
import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.util.Numbers;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One job in the price master, from a date.
 *
 * <p>Labor is in hours, and the hours are not negotiable: the master says a coupler takes an hour
 * and a quarter whether it took the machinist twenty minutes or all afternoon. Material is the
 * part, at the price the master carries on the day the work was done.
 *
 * @param laborHundredths the hours the master allows, in hundredths
 * @param materialCents the new price of the part
 */
public record PriceLine(JobCode job, LocalDate effective, long laborHundredths, long materialCents) {

    /** Hours are carried to two places. */
    public static final int HUNDREDTHS_PER_HOUR = 100;

    public PriceLine {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(effective, "effective");
        if (laborHundredths < 0) {
            throw new BillingError("labor does not run backwards", Details.of("job", job.toString()));
        }
        if (materialCents < 0) {
            throw new BillingError("material does not pay the road", Details.of("job", job.toString()));
        }
    }

    public Money material() {
        return new Money(materialCents);
    }

    /** The allowed hours as a decimal, for a report. */
    public double laborHours() {
        return laborHundredths / (double) HUNDREDTHS_PER_HOUR;
    }

    /** {@code 4312 2.50 hours and $1,280.00}. */
    @Override
    public String toString() {
        return job + " " + Numbers.fixed(laborHours(), 2) + " hours and " + material();
    }
}
