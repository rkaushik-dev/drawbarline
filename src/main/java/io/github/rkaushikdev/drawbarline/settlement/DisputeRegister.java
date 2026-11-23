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
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Every dispute raised against us, open or not, and how old the open ones are getting.
 *
 * <p>A dispute is open from the day it is raised until it is settled or withdrawn, and either of
 * those closes it for good. A settlement says how much of the dispute we conceded, which is the
 * credit the road gets; whatever was not conceded is upheld, and the road owes it.
 *
 * <p>A road has {@link #WINDOW_MONTHS} months from the end of a month to dispute it. After that the
 * month is the road's to pay, because a dispute raised a year late is raised against records
 * nobody can check any more.
 */
public final class DisputeRegister {

    /** How many months after a month ends a road may still dispute it. */
    public static final int WINDOW_MONTHS = 6;

    private final Map<Integer, Dispute> disputes = new LinkedHashMap<>();
    private final Map<Integer, Disposal> closed = new HashMap<>();

    /** The last day a road may dispute a month. */
    public static LocalDate lastDayToDispute(Period period) {
        return period.lastDay().plusMonths(WINDOW_MONTHS);
    }

    /** File a dispute as open, refusing a reference already on file or one raised out of time. */
    public Dispute raise(Dispute dispute) {
        if (disputes.containsKey(dispute.reference())) {
            throw new SettlementError("that dispute is already on file", Details.of("reference", dispute.reference()));
        }
        LocalDate by = lastDayToDispute(dispute.period());
        if (dispute.raisedOn().isAfter(by)) {
            throw new SettlementError(
                    "that month is past disputing",
                    Details.of(
                            "reference",
                            dispute.reference(),
                            "period",
                            dispute.period().toString(),
                            "by",
                            by.toString()));
        }
        disputes.put(dispute.reference(), dispute);
        return dispute;
    }

    /** Close an open dispute as settled with the whole amount conceded. */
    public Dispute settle(int reference) {
        return close(reference, Disposal.settled(requireRaised(reference).amount()));
    }

    /** Close an open dispute as settled with part of it conceded, refusing more than was disputed. */
    public Dispute settle(int reference, Money conceded) {
        Dispute found = requireRaised(reference);
        if (conceded.isGreaterThan(found.amount())) {
            throw new SettlementError(
                    "more is conceded than was disputed",
                    Details.of("reference", reference, "cents", conceded.cents()));
        }
        return close(reference, Disposal.settled(conceded));
    }

    /** Close an open dispute as withdrawn by the road. */
    public Dispute withdraw(int reference) {
        return close(reference, Disposal.withdrawn());
    }

    /** Where a dispute stands, refusing a reference never raised. */
    public Standing standingOf(int reference) {
        requireRaised(reference);
        return disposalOf(reference).map(Disposal::standing).orElse(Standing.OPEN);
    }

    /** How a dispute was closed, or nothing while it is open. */
    public Optional<Disposal> disposalOf(int reference) {
        requireRaised(reference);
        return Optional.ofNullable(closed.get(reference));
    }

    /** What the road still owes on a closed dispute: the amount less what was conceded. */
    public Money upheld(int reference) {
        Dispute found = requireRaised(reference);
        Disposal disposal = disposalOf(reference)
                .orElseThrow(
                        () -> new SettlementError("that dispute is still open", Details.of("reference", reference)));
        return found.amount().minus(disposal.conceded());
    }

    /** Everything conceded to one road on its closed disputes, which is what it is credited. */
    public Money conceded(ReportingMark road) {
        return Money.total(disputes.values().stream()
                .filter(one -> one.road().equals(road) && closed.containsKey(one.reference()))
                .map(one -> closed.get(one.reference()).conceded())
                .toList());
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

    private Dispute requireRaised(int reference) {
        Dispute found = disputes.get(reference);
        if (found == null) {
            throw new SettlementError("no such dispute", Details.of("reference", reference));
        }
        return found;
    }

    private Dispute close(int reference, Disposal disposal) {
        Dispute found = requireRaised(reference);
        if (closed.containsKey(reference)) {
            throw new SettlementError("that dispute is already closed", Details.of("reference", reference));
        }
        closed.put(reference, disposal);
        return found;
    }

    private List<Dispute> openWhere(Predicate<Dispute> wanted) {
        return disputes.values().stream()
                .filter(one -> !closed.containsKey(one.reference()))
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
