package io.github.rkaushikdev.drawbarline.reports;

import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.settlement.Period;
import io.github.rkaushikdev.drawbarline.settlement.PostingKind;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One month of the annual summary, by kind of charge, read straight back off the ledger.
 *
 * @param amounts the month netted by kind, only the kinds that have postings
 * @param postings how many postings the month holds
 */
public record MonthLine(Period period, Map<PostingKind, Money> amounts, int postings) {

    public MonthLine {
        Objects.requireNonNull(period, "period");
        amounts = Collections.unmodifiableMap(new LinkedHashMap<>(amounts));
    }

    /** The month's amount for one kind, nothing when nothing of that kind was posted. */
    public Money amountOf(PostingKind kind) {
        return amounts.getOrDefault(kind, Money.ZERO);
    }

    /** Every kind together. */
    public Money movement() {
        return Money.total(amounts.values());
    }

    /** {@code 2025-01 $830.00}. */
    @Override
    public String toString() {
        return period + " " + movement();
    }
}
