package io.github.rkaushikdev.drawbarline.repairs;

import io.github.rkaushikdev.drawbarline.BillingError;
import io.github.rkaushikdev.drawbarline.Details;
import java.util.regex.Pattern;

/** Four digits saying what was done to a car, which reach a price in the master. */
public record JobCode(String digits) {

    private static final Pattern JOB = Pattern.compile("\\d{4}");

    public JobCode {
        if (digits == null || !JOB.matcher(digits).matches()) {
            throw new BillingError("a job code is four digits", Details.of("digits", digits));
        }
    }

    /** The first two digits, which group the jobs a pricer looks at together. */
    public String family() {
        return digits.substring(0, 2);
    }

    @Override
    public String toString() {
        return digits;
    }
}
