package io.github.rkaushikdev.drawbarline.scales;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.lading.Commodity;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Track scales, and the weights nobody actually weighs.
 *
 * <p>A track scale has to be tested on a cycle by a qualified inspector, and a weight taken on a
 * scale out of certification is not a weight anybody has to accept. So a weight is only good if the
 * scale was in certification on the day. The other half is the weights agreed rather than taken
 * (see {@link WeightAgreement}). Neither is a rounding convenience: both are things a shipper can
 * appeal, which is why both are dated and both are on file.
 */
public final class Scales {

    /** How long a passing test certifies a scale for. */
    public static final int CERTIFICATION_DAYS = 365;

    /** The largest error, in tenths of a percent, a scale may show and stay inside tolerance. */
    public static final int TOLERANCE_TENTHS_PERCENT = 2;

    private Scales() {}

    /** The most recent test on a scale as of a day. */
    public static Optional<ScaleTest> lastTest(List<ScaleTest> tests, String scale, LocalDate on) {
        return tests.stream()
                .filter(one -> one.scale().equals(scale) && !one.testedOn().isAfter(on))
                .max(Comparator.comparing(ScaleTest::testedOn));
    }

    /** Whether a weight taken on that day would stand up. */
    public static boolean certifiedOn(List<ScaleTest> tests, String scale, LocalDate day) {
        return lastTest(tests, scale, day)
                .map(found -> found.result().usable() && !day.isAfter(found.certifiesUntil()))
                .orElse(false);
    }

    /** Refuse a weight taken on a scale that was out of certification. */
    public static void requireCertified(List<ScaleTest> tests, String scale, LocalDate day) {
        if (!certifiedOn(tests, scale, day)) {
            throw new ScaleError(
                    "that scale was not in certification", Details.of("scale", scale, "on", day.toString()));
        }
    }

    /** What a car bills at when nothing was weighed: the agreement, or a refusal. */
    public static Weight billingWeight(
            List<WeightAgreement> agreements, String shipper, Commodity commodity, LocalDate day) {
        return billingWeight(agreements, shipper, commodity, day, Optional.empty());
    }

    /** What a car bills at: the agreement if there is one, else the scale. */
    public static Weight billingWeight(
            List<WeightAgreement> agreements, String shipper, Commodity commodity, LocalDate day, Weight weighed) {
        return billingWeight(agreements, shipper, commodity, day, Optional.of(weighed));
    }

    /** What a car bills at: the agreement if there is one, else the scale if it was weighed. */
    public static Weight billingWeight(
            List<WeightAgreement> agreements,
            String shipper,
            Commodity commodity,
            LocalDate day,
            Optional<Weight> weighed) {
        for (WeightAgreement one : agreements) {
            if (one.appliesTo(shipper, commodity, day)) {
                return one.agreed();
            }
        }
        return weighed.orElseThrow(() -> new ScaleError(
                "no agreement and no weight, so nothing to bill on",
                Details.of("shipper", shipper, "commodity", commodity.code().toString())));
    }
}
