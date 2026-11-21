package io.github.rkaushikdev.drawbarline.settlement;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.SettlementError;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Everything posted against one road.
 *
 * <p>Netting is just addition. We do not send a road a bill for car hire and receive one for
 * repairs in the same month: the whole account is netted and one of us writes a cheque, which is
 * why the account is by road rather than by kind of charge.
 */
public final class Account {

    private static final Comparator<Posting> IN_ORDER = Comparator.comparing(Posting::period)
            .thenComparing(Posting::on)
            .thenComparing(one -> one.kind().name());

    private static final Predicate<Posting> EVERY = one -> true;

    private final ReportingMark road;
    private final List<Posting> postings = new ArrayList<>();

    public Account(ReportingMark road) {
        this.road = Objects.requireNonNull(road, "road");
    }

    public ReportingMark road() {
        return road;
    }

    /** Add a posting, which has to be for this account's road. */
    public Posting post(Posting posting) {
        if (!posting.road().equals(road)) {
            throw new SettlementError(
                    "that posting belongs to another road",
                    Details.of(
                            "account",
                            road.toString(),
                            "posting",
                            posting.road().toString()));
        }
        postings.add(posting);
        return posting;
    }

    /** Every posting, by month, then date, then kind. */
    public List<Posting> postings() {
        return postingsIn(EVERY);
    }

    /** The postings of one month, by date and then kind. */
    public List<Posting> postings(Period period) {
        return postingsIn(in(period));
    }

    /** Everything ever posted, netted. */
    public Money balance() {
        return balanceIn(EVERY);
    }

    /** One month's postings, netted. */
    public Money balance(Period period) {
        return balanceIn(in(period));
    }

    /** Every posting netted by kind, the kinds in the order they first appear. */
    public Map<PostingKind, Money> byKind() {
        return byKindIn(EVERY);
    }

    /** One month netted by kind, the kinds in the order they first appear. */
    public Map<PostingKind, Money> byKind(Period period) {
        return byKindIn(in(period));
    }

    /** Everything up to and including a month, which is a closing balance. */
    public Money balanceThrough(Period period) {
        return Money.total(postings.stream()
                .filter(one -> one.period().compareTo(period) <= 0)
                .map(Posting::amount)
                .toList());
    }

    /** How many postings the account holds. */
    public int size() {
        return postings.size();
    }

    private static Predicate<Posting> in(Period period) {
        return one -> one.period().equals(period);
    }

    private List<Posting> postingsIn(Predicate<Posting> wanted) {
        return postings.stream().filter(wanted).sorted(IN_ORDER).toList();
    }

    private Money balanceIn(Predicate<Posting> wanted) {
        return Money.total(postingsIn(wanted).stream().map(Posting::amount).toList());
    }

    private Map<PostingKind, Money> byKindIn(Predicate<Posting> wanted) {
        Map<PostingKind, Money> running = new LinkedHashMap<>();
        for (Posting one : postingsIn(wanted)) {
            running.merge(one.kind(), one.amount(), Money::plus);
        }
        return Collections.unmodifiableMap(running);
    }
}
