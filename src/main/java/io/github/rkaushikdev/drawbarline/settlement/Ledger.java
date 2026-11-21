package io.github.rkaushikdev.drawbarline.settlement;

import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The running account with each connection, one account a road.
 *
 * <p>Everything that passes between us and another road for a month ends up as a posting: car hire
 * we owe them, car hire they owe us, repairs billed either way, reclaims, adjustments and payments.
 * The sign convention is stated once and never bent. A positive amount is money we owe them, a
 * negative amount is money they owe us.
 *
 * <p>A posting into a closed month is refused. The month is the unit that gets agreed with the
 * other road, and a late posting into an agreed month is a different document called an
 * adjustment.
 */
public final class Ledger {

    private final Map<ReportingMark, Account> accounts = new HashMap<>();
    private final PeriodRegister periods;

    /** A ledger with every month open. */
    public Ledger() {
        this(new PeriodRegister());
    }

    /** A ledger that asks a register of closed months before it takes a posting. */
    public Ledger(PeriodRegister periods) {
        this.periods = Objects.requireNonNull(periods, "periods");
    }

    /** Post to the road's account, refusing a month that is closed. */
    public Posting post(Posting posting) {
        periods.requireOpen(posting.period());
        return restore(posting);
    }

    /**
     * Put a posting back without asking whether the month is open.
     *
     * <p>Loading last year off the disk has to work, and last year is closed. Only a loader calls
     * this. Everything else calls {@link #post} and gets the check.
     */
    public Posting restore(Posting posting) {
        return accounts.computeIfAbsent(posting.road(), Account::new).post(posting);
    }

    /** The road's account, or an empty one when nothing has been posted to it. */
    public Account account(ReportingMark road) {
        Account found = accounts.get(road);
        return found != null ? found : new Account(road);
    }

    /** Every road with an account, in mark order. */
    public List<ReportingMark> roads() {
        return accounts.keySet().stream().sorted().toList();
    }

    /** Each road's balance over everything posted, in mark order. */
    public Map<ReportingMark, Money> net() {
        Map<ReportingMark, Money> found = new LinkedHashMap<>();
        for (ReportingMark road : roads()) {
            found.put(road, account(road).balance());
        }
        return Collections.unmodifiableMap(found);
    }

    /** Each road's balance for one month, in mark order. */
    public Map<ReportingMark, Money> net(Period period) {
        Map<ReportingMark, Money> found = new LinkedHashMap<>();
        for (ReportingMark road : roads()) {
            found.put(road, account(road).balance(period));
        }
        return Collections.unmodifiableMap(found);
    }

    /** What we owe, added over the roads we owe. */
    public Money weOwe() {
        return owing(net());
    }

    /** What we owe for one month, added over the roads we owe. */
    public Money weOwe(Period period) {
        return owing(net(period));
    }

    /** What is owed to us, as a positive amount, added over the roads that owe it. */
    public Money owedToUs() {
        return owed(net());
    }

    /** What is owed to us for one month, as a positive amount. */
    public Money owedToUs(Period period) {
        return owed(net(period));
    }

    /** The whole pool up to and including a month, which a trial balance checks. */
    public Money positionThrough(Period period) {
        return Money.total(roads().stream()
                .map(road -> account(road).balanceThrough(period))
                .toList());
    }

    /** One number for the whole pool, positive when we are behind. */
    public Money position() {
        return Money.total(net().values());
    }

    /** One number for the whole pool for one month, positive when we are behind. */
    public Money position(Period period) {
        return Money.total(net(period).values());
    }

    /** How many postings the ledger holds across every account. */
    public int size() {
        return accounts.values().stream().mapToInt(Account::size).sum();
    }

    private static Money owing(Map<ReportingMark, Money> net) {
        return Money.total(
                net.values().stream().filter(amount -> amount.cents() > 0).toList());
    }

    private static Money owed(Map<ReportingMark, Money> net) {
        return Money.total(net.values().stream()
                .filter(amount -> amount.cents() < 0)
                .map(Money::abs)
                .toList());
    }
}
