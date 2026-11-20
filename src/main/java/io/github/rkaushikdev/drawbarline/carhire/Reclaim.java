package io.github.rkaushikdev.drawbarline.carhire;

import io.github.rkaushikdev.drawbarline.clock.Interval;
import io.github.rkaushikdev.drawbarline.events.CarStream;
import io.github.rkaushikdev.drawbarline.events.EventKind;
import io.github.rkaushikdev.drawbarline.events.Pair;
import io.github.rkaushikdev.drawbarline.events.Spans;
import io.github.rkaushikdev.drawbarline.fleet.Car;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * A stretch of hours taken back off a charge.
 *
 * <p>Car hire runs from the moment we take a car until we hand it back, and some of that time we do
 * not owe. A car that arrived with a broken wheel and sat four days on the rip track waiting on a
 * part is the owner's problem, and a car that could not move because an embargo shut the line is
 * nobody's fault but not ours to pay for either. The walk that finds a hold is the same walk that
 * finds a car cycle, {@link Spans#scanPairs}.
 *
 * <p>A reclaim is priced exactly the way the charge it offsets is priced, by running the same
 * accrual over the reclaimed span, so the two always cancel to the cent.
 */
public record Reclaim(CarMark car, ReclaimReason reason, Interval interval, Money amount) {

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
        List<Reclaim> found = new ArrayList<>();
        if (ownerRepairs) {
            for (Interval span : repairHolds(stream, window.end())) {
                found.add(priced(car, table, window, span, ReclaimReason.REPAIRS));
            }
        }
        for (Interval span : embargoHolds(stream, window.end())) {
            found.add(priced(car, table, window, span, ReclaimReason.EMBARGO));
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
