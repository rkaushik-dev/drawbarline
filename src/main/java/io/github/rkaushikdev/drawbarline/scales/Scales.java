package io.github.rkaushikdev.drawbarline.scales;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.lading.Commodity;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.time.LocalDate;
import java.util.ArrayList;
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
 *
 * <p>Two agreements covering the same shipper's commodity on the same day are not a choice of
 * weights, they are a filing error, and billing on whichever one happened to be filed first is how
 * a shipper ends up appealing a whole quarter. So an overlap is refused when a car is billed, and
 * {@link #overlapping} finds them before anybody bills anything.
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
        Optional<WeightAgreement> agreed = agreementFor(agreements, shipper, commodity, day);
        if (agreed.isPresent()) {
            return agreed.get().agreed();
        }
        return weighed.orElseThrow(() -> new ScaleError(
                "no agreement and no weight, so nothing to bill on",
                Details.of("shipper", shipper, "commodity", commodity.code().toString())));
    }

    /** The one agreement that bills a shipper's commodity on a day, refusing two that both claim to. */
    public static Optional<WeightAgreement> agreementFor(
            List<WeightAgreement> agreements, String shipper, Commodity commodity, LocalDate day) {
        List<WeightAgreement> applying = agreements.stream()
                .filter(one -> one.appliesTo(shipper, commodity, day))
                .toList();
        if (applying.size() > 1) {
            throw new ScaleError(
                    "two weight agreements cover that commodity on that day",
                    Details.of("shipper", shipper, "commodity", commodity.code().toString(), "on", day.toString()));
        }
        return applying.stream().findFirst();
    }

    /** Whether two agreements are for the same shipper's commodity and share a day. */
    public static boolean clash(WeightAgreement one, WeightAgreement other) {
        return one.shipper().equals(other.shipper())
                && one.commodity().code().equals(other.commodity().code())
                && one.term().overlaps(other.term());
    }

    /** Every pair of agreements that clash, each pair once, in the order they were filed. */
    public static List<List<WeightAgreement>> overlapping(List<WeightAgreement> agreements) {
        List<List<WeightAgreement>> found = new ArrayList<>();
        for (int at = 0; at < agreements.size(); at++) {
            for (int later = at + 1; later < agreements.size(); later++) {
                if (clash(agreements.get(at), agreements.get(later))) {
                    found.add(List.of(agreements.get(at), agreements.get(later)));
                }
            }
        }
        return List.copyOf(found);
    }
}
