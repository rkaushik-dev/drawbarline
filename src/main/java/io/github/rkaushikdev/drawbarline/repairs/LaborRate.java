package io.github.rkaushikdev.drawbarline.repairs;

import io.github.rkaushikdev.drawbarline.BillingError;
import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.LocalDate;
import java.util.Objects;

/** What one road charges an hour for repair labor, from a date. */
public record LaborRate(ReportingMark road, LocalDate effective, long centsAnHour) {

    public LaborRate {
        Objects.requireNonNull(road, "road");
        Objects.requireNonNull(effective, "effective");
        if (centsAnHour < 0) {
            throw new BillingError("labor does not pay the owner", Details.of("road", road.toString()));
        }
    }

    /** What hundredths of an hour come to, to the nearest cent, a tie going to the even one. */
    public Money costOf(long hundredths) {
        return new Money((long) Math.rint(centsAnHour * hundredths / (double) PriceLine.HUNDREDTHS_PER_HOUR));
    }
}
