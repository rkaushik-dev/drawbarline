package io.github.rkaushikdev.drawbarline.demurrage;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.TariffError;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One shipper, one month, every car that touched the balance under an average agreement.
 *
 * <p>The part that makes it an agreement and not a discount is that the credits expire. They
 * offset this month and no other: a shipper who spends October building up credits cannot spend
 * them in November, and a shipper with a bad November cannot borrow against December. A new month
 * is a new ledger, so there is never a balance to carry forward.
 */
public final class AverageLedger {

    private final AverageAgreement agreement;
    private final LocalDate monthOf;
    private final List<Entry> entries = new ArrayList<>();

    public AverageLedger(AverageAgreement agreement, LocalDate monthOf) {
        Objects.requireNonNull(agreement, "agreement");
        Objects.requireNonNull(monthOf, "monthOf");
        if (!agreement.covers(monthOf)) {
            throw new TariffError(
                    "no agreement was in force that month",
                    Details.of("shipper", agreement.shipper(), "month", monthOf.toString()));
        }
        this.agreement = agreement;
        this.monthOf = monthOf;
    }

    public AverageAgreement agreement() {
        return agreement;
    }

    public LocalDate monthOf() {
        return monthOf;
    }

    /** Put one detention on the balance, as a credit or as debits. */
    public Entry post(Detention detention) {
        Entry entry = Entry.of(detention);
        entries.add(entry);
        return entry;
    }

    /** Every entry, in the order it was posted. */
    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public int credits() {
        return entries.stream().mapToInt(Entry::credits).sum();
    }

    public int debits() {
        return entries.stream().mapToInt(Entry::debits).sum();
    }

    /** The chargeable days past the offset, every car together. */
    public int late() {
        return entries.stream().mapToInt(Entry::late).sum();
    }

    /**
     * Set the month's credits against its debits and price what is left at the agreement's rate,
     * refusing a month with late days in it, which need the tariff to be priced.
     */
    public Settlement settle() {
        if (late() != 0) {
            throw new TariffError(
                    "a car ran past the offset, so the month is settled against the tariff",
                    Details.of("shipper", agreement.shipper(), "late", late()));
        }
        return settle(Optional.empty());
    }

    /** Settle the month, the late days priced up the tariff in force. */
    public Settlement settle(Tariff tariff) {
        return settle(Optional.of(tariff));
    }

    private Settlement settle(Optional<Tariff> tariff) {
        int credits = credits();
        int debits = debits();
        Money averaged = agreement.daily().times(Math.max(debits - credits, 0));
        Money late = Money.total(entries.stream()
                .map(one -> tariff.map(priced -> one.lateCharge(priced)).orElse(Money.ZERO))
                .toList());
        return new Settlement(agreement.shipper(), credits, debits, averaged, late(), late);
    }

    /** How many cars were posted. */
    public int size() {
        return entries.size();
    }
}
