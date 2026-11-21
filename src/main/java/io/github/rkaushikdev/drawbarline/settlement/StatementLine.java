package io.github.rkaushikdev.drawbarline.settlement;

import io.github.rkaushikdev.drawbarline.money.Money;
import java.util.Objects;

/** One kind of charge on a monthly statement, summed for the month. */
public record StatementLine(PostingKind kind, Money amount) {

    public StatementLine {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(amount, "amount");
    }

    /** {@code car hire $1,200.00}. */
    @Override
    public String toString() {
        return kind.text() + " " + amount;
    }
}
