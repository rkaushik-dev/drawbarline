package io.github.rkaushikdev.drawbarline.money;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.util.Numbers;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An amount in whole cents, which may be negative.
 *
 * <p>Unlike a length or a weight, money here is signed. A settlement between two roads runs both
 * ways in the same month, and the whole point of the netting is that one side of it is negative.
 *
 * <p>Splitting matters more than it looks. A car hire charge shared between two roads cannot lose
 * a cent to rounding, because the two roads each compute their own half and then compare. {@link
 * #allocate} hands out the whole amount every time, the odd cents going to the largest remainders,
 * which is the rule both sides can reproduce.
 */
public record Money(long cents) implements Comparable<Money> {

    public static final long CENTS_PER_DOLLAR = 100;

    /** No money at all. */
    public static final Money ZERO = new Money(0);

    private static final Pattern MONEY = Pattern.compile("(?<sign>-)?\\$?(?<dollars>[\\d,]+)(?:\\.(?<cents>\\d{2}))?");

    /** Dollars and cents together: {@code dollars(12, 34)} is $12.34. */
    public static Money dollars(long dollars, long cents) {
        return new Money(dollars * CENTS_PER_DOLLAR + cents);
    }

    /** Whole dollars. */
    public static Money dollars(long dollars) {
        return dollars(dollars, 0);
    }

    /** Read {@code $1,234.56}, {@code 1234.56} or {@code -$4.00}. */
    public static Money parse(String text) {
        Matcher found = MONEY.matcher(Text.strip(text));
        String whole = found.matches() ? found.group("dollars").replace(",", "") : "";
        if (whole.isEmpty()) {
            throw new MoneyError("cannot read that as an amount", Details.of("text", text));
        }
        long cents = Long.parseLong(whole) * CENTS_PER_DOLLAR;
        String part = found.group("cents");
        cents += part == null ? 0 : Long.parseLong(part);
        return new Money(found.group("sign") != null ? -cents : cents);
    }

    public boolean isZero() {
        return cents == 0;
    }

    public Money plus(Money other) {
        return new Money(cents + other.cents);
    }

    public Money minus(Money other) {
        return new Money(cents - other.cents);
    }

    public Money negate() {
        return new Money(-cents);
    }

    public Money abs() {
        return new Money(Math.abs(cents));
    }

    public Money times(long count) {
        return new Money(cents * count);
    }

    public boolean isLessThan(Money other) {
        return cents < other.cents;
    }

    public boolean isGreaterThan(Money other) {
        return cents > other.cents;
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(cents, other.cents);
    }

    /** Dollars grouped by commas, the sign before the dollar: {@code -$1,234.56}. */
    @Override
    public String toString() {
        String sign = cents < 0 ? "-" : "";
        long magnitude = Math.abs(cents);
        return sign + "$" + Numbers.grouped(magnitude / CENTS_PER_DOLLAR) + "."
                + String.format(Locale.ROOT, "%02d", magnitude % CENTS_PER_DOLLAR);
    }

    /** The sum, which is zero over nothing. */
    public static Money total(Iterable<Money> amounts) {
        long running = 0;
        for (Money amount : amounts) {
            running += amount.cents;
        }
        return new Money(running);
    }

    /**
     * Split an amount by weights, losing nothing.
     *
     * <p>Each share is the floor of its proportion, then the cents left over go one each to the
     * shares with the largest remainders. Ties break toward the earlier weight so two parties
     * running this independently agree.
     */
    public static List<Money> allocate(Money amount, List<Long> weights) {
        if (weights.isEmpty()) {
            throw new MoneyError("nothing to allocate across");
        }
        if (weights.stream().anyMatch(weight -> weight < 0)) {
            throw new MoneyError("an allocation weight is not negative", Details.of("weights", List.copyOf(weights)));
        }
        long divisor = weights.stream().mapToLong(Long::longValue).sum();
        if (divisor == 0) {
            throw new MoneyError("allocation weights are all zero", Details.of("weights", List.copyOf(weights)));
        }
        long sign = amount.cents < 0 ? -1 : 1;
        long magnitude = Math.abs(amount.cents);
        int count = weights.size();
        long[] shares = new long[count];
        long[] remainders = new long[count];
        long handed = 0;
        for (int at = 0; at < count; at++) {
            shares[at] = magnitude * weights.get(at) / divisor;
            remainders[at] = magnitude * weights.get(at) - shares[at] * divisor;
            handed += shares[at];
        }
        Integer[] order = new Integer[count];
        Arrays.setAll(order, at -> at);
        Arrays.sort(
                order,
                Comparator.comparingLong((Integer at) -> remainders[at])
                        .reversed()
                        .thenComparingInt(at -> at));
        long left = magnitude - handed;
        for (int at = 0; at < left; at++) {
            shares[order[at]] += 1;
        }
        List<Money> found = new ArrayList<>(count);
        for (long share : shares) {
            found.add(new Money(sign * share));
        }
        return List.copyOf(found);
    }

    /** Split by integer weights, the way a count of hours or cars weights a share. */
    public static List<Money> allocate(Money amount, int... weights) {
        return allocate(
                amount, Arrays.stream(weights).mapToObj(weight -> (long) weight).toList());
    }

    /** An even split, which is an allocation across equal weights. */
    public static List<Money> split(Money amount, int ways) {
        if (ways < 1) {
            throw new MoneyError("cannot split fewer than one way", Details.of("ways", ways));
        }
        int[] weights = new int[ways];
        Arrays.fill(weights, 1);
        return allocate(amount, weights);
    }
}
