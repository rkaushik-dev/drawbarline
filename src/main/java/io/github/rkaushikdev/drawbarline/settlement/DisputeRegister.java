package io.github.rkaushikdev.drawbarline.settlement;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.SettlementError;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Every dispute raised against us, open or not, and how old the open ones are getting.
 *
 * <p>A dispute is open from the day it is raised until it is settled or withdrawn, and either of
 * those closes it for good.
 */
public final class DisputeRegister {

    private final Map<Integer, Dispute> disputes = new LinkedHashMap<>();
    private final Map<Integer, Standing> standing = new HashMap<>();

    /** File a dispute as open, refusing a reference already on file. */
    public Dispute raise(Dispute dispute) {
        if (disputes.containsKey(dispute.reference())) {
            throw new SettlementError("that dispute is already on file", Details.of("reference", dispute.reference()));
        }
        disputes.put(dispute.reference(), dispute);
        standing.put(dispute.reference(), Standing.OPEN);
        return dispute;
    }

    /** Close an open dispute as settled. */
    public Dispute settle(int reference) {
        return close(reference, Standing.SETTLED);
    }

    /** Close an open dispute as withdrawn by the road. */
    public Dispute withdraw(int reference) {
        return close(reference, Standing.WITHDRAWN);
    }

    /** Where a dispute stands, refusing a reference never raised. */
    public Standing standingOf(int reference) {
        Standing found = standing.get(reference);
        if (found == null) {
            throw new SettlementError("no such dispute", Details.of("reference", reference));
        }
        return found;
    }

    /** Every open dispute, by reference. */
    public List<Dispute> openDisputes() {
        return openWhere(one -> true);
    }

    /** One road's open disputes, by reference. */
    public List<Dispute> openDisputes(ReportingMark road) {
        return openWhere(one -> one.road().equals(road));
    }

    /** Every open dispute's amount together. */
    public Money openAmount() {
        return amountOf(openDisputes());
    }

    /** One road's open disputes' amount together. */
    public Money openAmount(ReportingMark road) {
        return amountOf(openDisputes(road));
    }

    /** The open disputes in their buckets as of a day. */
    public Aging aging(LocalDate asOf) {
        return agingOf(openDisputes(), asOf);
    }

    /** One road's open disputes in their buckets as of a day. */
    public Aging aging(LocalDate asOf, ReportingMark road) {
        return agingOf(openDisputes(road), asOf);
    }

    /** How many disputes were ever raised, open or not. */
    public int size() {
        return disputes.size();
    }

    private Dispute close(int reference, Standing to) {
        if (!disputes.containsKey(reference)) {
            throw new SettlementError("no such dispute", Details.of("reference", reference));
        }
        if (standing.get(reference) != Standing.OPEN) {
            throw new SettlementError("that dispute is already closed", Details.of("reference", reference));
        }
        standing.put(reference, to);
        return disputes.get(reference);
    }

    private List<Dispute> openWhere(Predicate<Dispute> wanted) {
        return disputes.values().stream()
                .filter(one -> standing.get(one.reference()) == Standing.OPEN)
                .filter(wanted)
                .sorted(Comparator.comparingInt(Dispute::reference))
                .toList();
    }

    private static Money amountOf(List<Dispute> found) {
        return Money.total(found.stream().map(Dispute::amount).toList());
    }

    private static Aging agingOf(List<Dispute> open, LocalDate asOf) {
        Map<String, Money> running = new LinkedHashMap<>();
        for (String label : Aging.bucketLabels()) {
            running.put(label, Money.ZERO);
        }
        for (Dispute one : open) {
            running.merge(Aging.bucketFor(one.daysOld(asOf)), one.amount(), Money::plus);
        }
        List<Aging.Bucket> buckets = new ArrayList<>();
        running.forEach((label, amount) -> buckets.add(new Aging.Bucket(label, amount)));
        return new Aging(buckets);
    }
}
