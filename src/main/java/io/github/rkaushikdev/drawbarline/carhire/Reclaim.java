package io.github.rkaushikdev.drawbarline.carhire;

import io.github.rkaushikdev.drawbarline.clock.Interval;
import io.github.rkaushikdev.drawbarline.events.CarStream;
import io.github.rkaushikdev.drawbarline.events.EventKind;
import io.github.rkaushikdev.drawbarline.events.OnTrack;
import io.github.rkaushikdev.drawbarline.events.Pair;
import io.github.rkaushikdev.drawbarline.events.Spans;
import io.github.rkaushikdev.drawbarline.fleet.Car;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A stretch of hours taken back off a charge.
 *
 * <p>Car hire runs from the moment we take a car until we hand it back, and some of that time we do
 * not owe. Each rule a reclaim comes back under is a hold of its own: a car that sat on the rip track
 * waiting on a part for a defect the owner answers for (rule 5), a car placed loaded at a shipper
 * and kept there past {@link #SHIPPER_THRESHOLD} (rule 15, the hours after the threshold until the
 * release), and a car that could not move because an embargo shut the line (rule 24). The walk that
 * finds a hold is the same walk that finds a car cycle, {@link Spans#scanPairs}.
 *
 * <p>Rule 15 is claimed only when somebody asks for it, because the road has to be billing the same
 * shipper demurrage before the owner will accept it; {@link #ORDINARY} is what a month is closed on.
 *
 * <p>A reclaim is priced exactly the way the charge it offsets is priced, by running the same
 * accrual over the reclaimed span, so the two always cancel to the cent.
 */
public record Reclaim(CarMark car, ReclaimReason reason, Interval interval, Money amount) {

    /** How long a loaded car may stand at a shipper before the hours come back under rule 15. */
    public static final Duration SHIPPER_THRESHOLD = Duration.ofHours(48);

    /** The rules a month is reclaimed under unless somebody asks for more. */
    public static final Set<ReclaimReason> ORDINARY = EnumSet.of(ReclaimReason.REPAIRS, ReclaimReason.EMBARGO);

    public Reclaim {
        Objects.requireNonNull(car, "car");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(interval, "interval");
        Objects.requireNonNull(amount, "amount");
    }

    public long hundredthHours() {
        return interval.hundredthHours();
    }

    /** {@code CN 400100 rule 5 $12.34}. */
    @Override
    public String toString() {
        return car + " " + reason.rule() + " " + amount;
    }

    /** The intervals between one kind of event and another, the last cut at a cut off. */
    public static List<Interval> pairedSpans(
            CarStream stream, EventKind opens, EventKind closes, Instant until, String what) {
        return Spans.scanPairs(stream, EnumSet.of(opens), EnumSet.of(closes), until, what).stream()
                .map(Pair::interval)
                .toList();
    }

    /** Every time the car sat bad ordered, up to a cut off. */
    public static List<Interval> repairHolds(CarStream stream, Instant until) {
        return pairedSpans(stream, EventKind.BAD_ORDERED, EventKind.REPAIRED, until, "the bad order");
    }

    /** Every time the car sat held under embargo, up to a cut off. */
    public static List<Interval> embargoHolds(CarStream stream, Instant until) {
        return pairedSpans(stream, EventKind.HELD, EventKind.FREED, until, "the hold");
    }

    /**
     * Every stretch a car placed loaded stood at a shipper past the threshold, up to a cut off: from
     * the end of the threshold to the release, or to the pull or the cut off while it has not been
     * released. An empty placed to be loaded is not held under load and earns nothing here.
     */
    public static List<Interval> shipperDelays(CarStream stream, Instant until) {
        List<Interval> found = new ArrayList<>();
        for (OnTrack span : Spans.onTrackSpans(stream, until)) {
            if (span.openedBy().waybill().isEmpty()) {
                continue;
            }
            Instant reclaimFrom = span.interval().start().plus(SHIPPER_THRESHOLD);
            Instant heldUntil = span.releasedAt().orElse(span.interval().end());
            if (heldUntil.isAfter(reclaimFrom)) {
                found.add(new Interval(reclaimFrom, heldUntil));
            }
        }
        return List.copyOf(found);
    }

    /** Every hold one rule reclaims, up to a cut off. */
    public static List<Interval> holds(ReclaimReason rule, CarStream stream, Instant until) {
        return switch (rule) {
            case REPAIRS -> repairHolds(stream, until);
            case SHIPPER_DELAY -> shipperDelays(stream, until);
            case EMBARGO -> embargoHolds(stream, until);
        };
    }

    /** Every reclaim the stream supports inside a window, repairs counted as the owner's. */
    public static List<Reclaim> reclaimsFor(Car car, CarStream stream, RateTable table, Interval window) {
        return reclaimsFor(car, stream, table, window, true);
    }

    /**
     * Every reclaim the stream supports inside a window.
     *
     * @param ownerRepairs whether the owner answers for the repairs, which is what makes repair
     *     holds reclaimable; an embargo is reclaimed either way
     */
    public static List<Reclaim> reclaimsFor(
            Car car, CarStream stream, RateTable table, Interval window, boolean ownerRepairs) {
        Set<ReclaimReason> rules = EnumSet.copyOf(ORDINARY);
        if (!ownerRepairs) {
            rules.remove(ReclaimReason.REPAIRS);
        }
        return reclaimsFor(car, stream, table, window, rules);
    }

    /** Every reclaim the stream supports inside a window under the rules asked for, in rule order. */
    public static List<Reclaim> reclaimsFor(
            Car car, CarStream stream, RateTable table, Interval window, Set<ReclaimReason> rules) {
        List<Reclaim> found = new ArrayList<>();
        for (ReclaimReason rule : ReclaimReason.values()) {
            if (!rules.contains(rule)) {
                continue;
            }
            for (Interval span : holds(rule, stream, window.end())) {
                found.add(priced(car, table, window, span, rule));
            }
        }
        return found.stream().filter(one -> !one.interval().isEmpty()).toList();
    }

    private static Reclaim priced(Car car, RateTable table, Interval window, Interval span, ReclaimReason reason) {
        Interval cut = span.clip(window);
        return new Reclaim(car.mark(), reason, cut, Accrual.hourly(car, table, cut));
    }

    /** A charge with its reclaims taken off, which never goes below nothing. */
    public static Money netOfReclaims(Money chargeAmount, List<Reclaim> reclaims) {
        Money taken = Money.total(reclaims.stream().map(Reclaim::amount).toList());
        if (taken.isGreaterThan(chargeAmount)) {
            return Money.ZERO;
        }
        return chargeAmount.minus(taken);
    }
}
