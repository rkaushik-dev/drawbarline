package io.github.rkaushikdev.drawbarline.settlement;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.SettlementError;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Disputed money by how long it has been sitting.
 *
 * <p>The number that matters to the office is not how many disputes there are, it is how old they
 * are, and a dispute nobody has touched in four months is a different problem from one raised last
 * week. Buckets are thirty days wide, oldest last, and the ninety day bucket is kept open ended
 * because there is no useful distinction between a dispute four months old and one nine months
 * old except how embarrassing it is.
 */
public record Aging(List<Bucket> buckets) {

    /** How wide each bucket is, in days. */
    public static final int BUCKET_DAYS = 30;

    /** Where the open ended bucket begins, in days. */
    public static final int OLDEST_BUCKET = 90;

    /** One bucket of an aging: its label and the money in it. */
    public record Bucket(String label, Money amount) {

        public Bucket {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(amount, "amount");
        }
    }

    public Aging {
        buckets = List.copyOf(buckets);
    }

    /** Every bucket together. */
    public Money total() {
        return Money.total(buckets.stream().map(Bucket::amount).toList());
    }

    /** The money in one bucket, refusing a label the aging has no bucket for. */
    public Money bucket(String label) {
        for (Bucket one : buckets) {
            if (one.label().equals(label)) {
                return one.amount();
            }
        }
        throw new SettlementError("no such bucket", Details.of("label", label));
    }

    /** The money in the last, open ended bucket. */
    public Money oldest() {
        if (buckets.isEmpty()) {
            throw new SettlementError("an aging with no buckets has no oldest one");
        }
        return buckets.get(buckets.size() - 1).amount();
    }

    /** {@code 1 to 30 $450.00 31 to 60 $0.00 61 to 90 $0.00 over 90 $0.00 total $450.00}. */
    @Override
    public String toString() {
        String parts =
                buckets.stream().map(one -> one.label() + " " + one.amount()).collect(Collectors.joining(" "));
        return parts + " total " + total();
    }

    /** The bucket labels, youngest first: {@code 1 to 30}, {@code 31 to 60}, {@code 61 to 90}, {@code over 90}. */
    public static List<String> bucketLabels() {
        List<String> labels = new ArrayList<>();
        int start = 0;
        while (start < OLDEST_BUCKET) {
            labels.add((start + 1) + " to " + (start + BUCKET_DAYS));
            start += BUCKET_DAYS;
        }
        labels.add("over " + OLDEST_BUCKET);
        return List.copyOf(labels);
    }

    /** The label of the bucket a dispute so many days old belongs in. A fresh one is in the first. */
    public static String bucketFor(long days) {
        List<String> labels = bucketLabels();
        if (days >= OLDEST_BUCKET) {
            return labels.get(labels.size() - 1);
        }
        return labels.get((int) (Math.max(days - 1, 0) / BUCKET_DAYS));
    }
}
