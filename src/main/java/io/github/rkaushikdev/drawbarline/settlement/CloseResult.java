package io.github.rkaushikdev.drawbarline.settlement;

import io.github.rkaushikdev.drawbarline.carhire.Liability;
import io.github.rkaushikdev.drawbarline.carhire.Reclaim;
import io.github.rkaushikdev.drawbarline.demurrage.Detention;
import io.github.rkaushikdev.drawbarline.demurrage.Invoice;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.util.List;
import java.util.Objects;

/**
 * What a month close produced, so it can be read without running it again.
 *
 * @param liability the car hire gathered for the month
 * @param postings what the close posted to the ledger, in the order it posted them
 */
public record CloseResult(
        Period period,
        Liability liability,
        List<Reclaim> reclaims,
        List<Detention> detentions,
        List<Invoice> invoices,
        List<Statement> statements,
        List<Posting> postings) {

    public CloseResult {
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(liability, "liability");
        reclaims = List.copyOf(reclaims);
        detentions = List.copyOf(detentions);
        invoices = List.copyOf(invoices);
        statements = List.copyOf(statements);
        postings = List.copyOf(postings);
    }

    /** How many postings the close made. */
    public int posted() {
        return postings.size();
    }

    /** The month's car hire, every road together. */
    public Money carHire() {
        return liability.total();
    }

    /** The car hire taken back off by reclaims. */
    public Money reclaimed() {
        return Money.total(reclaims.stream().map(Reclaim::amount).toList());
    }

    /** What the month's demurrage invoices come to. */
    public Money demurrage() {
        return Money.total(invoices.stream().map(Invoice::total).toList());
    }
}
