package io.github.rkaushikdev.drawbarline.repairs;

import io.github.rkaushikdev.drawbarline.BillingError;
import io.github.rkaushikdev.drawbarline.Details;
import java.util.regex.Pattern;

/**
 * Two digits saying why a repair was made. It is the code that decides who pays, so only the codes
 * the office manual publishes are accepted.
 */
public record WhyMade(String digits) {

    private static final Pattern WHY = Pattern.compile("\\d{2}");

    public WhyMade {
        if (digits == null || !WHY.matcher(digits).matches()) {
            throw new BillingError("a why made code is two digits", Details.of("digits", digits));
        }
        if (!Codes.WHY_MADE.containsKey(digits)) {
            throw new BillingError("no such why made code", Details.of("digits", digits));
        }
    }

    /** What the code means. */
    public String text() {
        return Codes.WHY_MADE.get(digits).text();
    }

    /** Who normally pays, before anybody argues about this particular car. */
    public Responsibility usualResponsibility() {
        return Codes.WHY_MADE.get(digits).usual();
    }

    @Override
    public String toString() {
        return digits;
    }
}
