package io.github.rkaushikdev.drawbarline.scales;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One certification test on one track scale: when, how it came out, the error found in tenths of
 * a percent, and who did it. A passing test certifies the scale for {@link Scales#CERTIFICATION_DAYS}.
 */
public record ScaleTest(String scale, LocalDate testedOn, Result result, int errorTenthsPercent, String inspector) {

    public ScaleTest {
        Objects.requireNonNull(scale, "scale");
        Objects.requireNonNull(testedOn, "testedOn");
        Objects.requireNonNull(result, "result");
        if (Text.isBlank(inspector)) {
            throw new ScaleError("a test names who did it", Details.of("scale", scale));
        }
        if (errorTenthsPercent < 0) {
            throw new ScaleError("an error is stated as a magnitude", Details.of("scale", scale));
        }
    }

    public boolean insideTolerance() {
        return errorTenthsPercent <= Scales.TOLERANCE_TENTHS_PERCENT;
    }

    /** The last day a weight taken on the scale stands on this test. */
    public LocalDate certifiesUntil() {
        return testedOn.plusDays(Scales.CERTIFICATION_DAYS);
    }

    /** {@code ESC1 passed on 2025-01-06}. */
    @Override
    public String toString() {
        return scale + " " + result.value() + " on " + testedOn;
    }
}
