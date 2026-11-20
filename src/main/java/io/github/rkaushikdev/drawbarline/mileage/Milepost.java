package io.github.rkaushikdev.drawbarline.mileage;

import io.github.rkaushikdev.drawbarline.Details;
import java.util.Locale;
import java.util.Objects;

/**
 * A place on one of the road's two lines, in tenths of a mile from that line's zero.
 *
 * <p>Tenths are the resolution the valuation maps carry, and a tenth of a mile at the car hire
 * mileage rate is real money over a fleet.
 */
public record Milepost(String line, int tenths) {

    public Milepost {
        if (!Objects.equals(line, Mileage.MAIN) && !Objects.equals(line, Mileage.BRANCH)) {
            throw new MileageError("this road has a main and a branch", Details.of("line", line));
        }
        if (tenths < 0) {
            throw new MileageError("a milepost does not run below zero", Details.of("tenths", tenths));
        }
    }

    /** Decimal miles, for a report. */
    public double miles() {
        return tenths / (double) Mileage.TENTHS_PER_MILE;
    }

    /** The line letter and the miles to one place: {@code M33.6}. */
    @Override
    public String toString() {
        return String.format(
                Locale.ROOT, "%s%d.%d", line, tenths / Mileage.TENTHS_PER_MILE, tenths % Mileage.TENTHS_PER_MILE);
    }
}
