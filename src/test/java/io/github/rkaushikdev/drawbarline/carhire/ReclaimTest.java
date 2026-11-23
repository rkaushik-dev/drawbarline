package io.github.rkaushikdev.drawbarline.carhire;

import static io.github.rkaushikdev.drawbarline.carhire.Reclaim.embargoHolds;
import static io.github.rkaushikdev.drawbarline.carhire.Reclaim.netOfReclaims;
import static io.github.rkaushikdev.drawbarline.carhire.Reclaim.pairedSpans;
import static io.github.rkaushikdev.drawbarline.carhire.Reclaim.reclaimsFor;
import static io.github.rkaushikdev.drawbarline.carhire.Reclaim.repairHolds;
import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;

import io.github.rkaushikdev.drawbarline.EventError;
import io.github.rkaushikdev.drawbarline.clock.Interval;
import io.github.rkaushikdev.drawbarline.events.CarStream;
import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.events.EventKind;
import io.github.rkaushikdev.drawbarline.fleet.Car;
import io.github.rkaushikdev.drawbarline.fleet.CarTypeCode;
import io.github.rkaushikdev.drawbarline.fleet.MechanicalDesignation;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.measure.Length;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ReclaimTest {

    static final CarMark CAR = CarMark.parse("CN 400100");
    static final ReportingMark CN = new ReportingMark("CN");
    static final CarTypeCode HOPPER = new CarTypeCode("L351");
    static final Interval JANUARY = new Interval(utc(2025, 1, 1, 6), utc(2025, 2, 1, 6));

    static Car aCar() {
        return new Car(
                CAR,
                new MechanicalDesignation("LO"),
                HOPPER,
                LocalDate.of(2020, 6, 1),
                new Weight(63500),
                new Weight(286000),
                Length.of(59, 6));
    }

    static RateTable aTable() {
        RateTable table = new RateTable();
        table.add(new Band(HOPPER, 0, 9, LocalDate.of(2024, 7, 1), new Rate(4630, 1210)));
        return table;
    }

    static CarStream streamOf(Event... events) {
        CarStream stream = new CarStream(CAR);
        for (Event event : events) {
            stream.record(event);
        }
        return stream;
    }

    /** An event at Escanaba on a January day and hour. */
    static Event at(int day, int hour, EventKind kind) {
        return Event.of(kind, CAR, utc(2025, 1, day, hour), "ESC");
    }

    /** An interchange at Escanaba on a January day and hour, with the road on the other side. */
    static Event at(int day, int hour, EventKind kind, ReportingMark road) {
        return Event.builder(kind, CAR, utc(2025, 1, day, hour), "ESC")
                .road(road)
                .build();
    }

    /** A car received on the fifth and delivered on the twenty fifth, with these in between. */
    static CarStream aVisit(Event... inner) {
        List<Event> events = new ArrayList<>();
        events.add(at(5, 12, EventKind.RECEIVED, CN));
        events.addAll(Arrays.asList(inner));
        events.add(at(25, 12, EventKind.DELIVERED, CN));
        return streamOf(events.toArray(Event[]::new));
    }

    @Test
    void aReasonCarriesItsRuleNumber() {
        assertThat(ReclaimReason.REPAIRS.rule()).isEqualTo("rule 5");
        assertThat(ReclaimReason.EMBARGO.rule()).isEqualTo("rule 24");
    }

    @Test
    void everyReasonHasReadableText() {
        assertThat(ReclaimReason.values())
                .allSatisfy(reason -> assertThat(reason.text()).isNotEmpty());
    }

    @Test
    void theRuleNumbersAreAllDifferent() {
        Set<Integer> numbers =
                Arrays.stream(ReclaimReason.values()).map(ReclaimReason::value).collect(Collectors.toSet());
        assertThat(numbers).hasSize(ReclaimReason.values().length);
    }

    @Test
    void aBadOrderAndARepairMakeOneHold() {
        CarStream stream = aVisit(at(10, 8, EventKind.BAD_ORDERED), at(14, 8, EventKind.REPAIRED));
        assertThat(repairHolds(stream, JANUARY.end()))
                .containsExactly(new Interval(utc(2025, 1, 10, 8), utc(2025, 1, 14, 8)));
    }

    @Test
    void aCarStillBadOrderedHoldsToTheCutOff() {
        CarStream stream = streamOf(at(5, 12, EventKind.RECEIVED, CN), at(10, 8, EventKind.BAD_ORDERED));
        assertThat(repairHolds(stream, JANUARY.end()).get(0).end()).isEqualTo(JANUARY.end());
    }

    @Test
    void aCarNeverBadOrderedHasNoHold() {
        assertThat(repairHolds(aVisit(), JANUARY.end())).isEmpty();
    }

    @Test
    void anEmbargoMakesItsOwnHold() {
        CarStream stream = aVisit(at(10, 8, EventKind.HELD), at(12, 8, EventKind.FREED));
        assertThat(embargoHolds(stream, JANUARY.end())).hasSize(1);
    }

    @Test
    void twoHoldsOfTheSameKindComeBackSeparately() {
        CarStream stream = aVisit(
                at(10, 8, EventKind.BAD_ORDERED),
                at(11, 8, EventKind.REPAIRED),
                at(15, 8, EventKind.BAD_ORDERED),
                at(16, 8, EventKind.REPAIRED));
        assertThat(repairHolds(stream, JANUARY.end())).hasSize(2);
    }

    @Test
    void openingTheSameStateTwiceIsRefused() {
        CarStream stream = aVisit(at(10, 8, EventKind.BAD_ORDERED), at(11, 8, EventKind.BAD_ORDERED));
        assertThatThrownBy(() -> repairHolds(stream, JANUARY.end())).isInstanceOf(EventError.class);
    }

    @Test
    void closingAStateThatWasNeverOpenedIsRefused() {
        CarStream stream = aVisit(at(10, 8, EventKind.REPAIRED));
        assertThatThrownBy(() -> repairHolds(stream, JANUARY.end())).isInstanceOf(EventError.class);
    }

    @Test
    void aPairOfKindsCanBeAskedForDirectly() {
        CarStream stream = aVisit(at(10, 8, EventKind.HELD), at(12, 8, EventKind.FREED));
        List<Interval> found = pairedSpans(stream, EventKind.HELD, EventKind.FREED, JANUARY.end(), "the hold");
        assertThat(found.get(0).minutes()).isEqualTo(2 * 24 * 60);
    }

    @Test
    void theLabelAHoldIsScannedUnderReachesTheMessage() {
        CarStream stream = aVisit(at(10, 8, EventKind.HELD), at(11, 8, EventKind.HELD));
        EventError caught = catchThrowableOfType(EventError.class, () -> embargoHolds(stream, JANUARY.end()));
        assertThat(caught.message()).contains("the hold");
    }

    @Test
    void aBadOrderScansUnderItsOwnLabel() {
        CarStream stream = aVisit(at(10, 8, EventKind.BAD_ORDERED), at(11, 8, EventKind.BAD_ORDERED));
        EventError caught = catchThrowableOfType(EventError.class, () -> repairHolds(stream, JANUARY.end()));
        assertThat(caught.message()).contains("the bad order");
    }

    @Test
    void aRepairHoldIsReclaimedAtTheHourlyRate() {
        CarStream stream = aVisit(at(10, 8, EventKind.BAD_ORDERED), at(14, 8, EventKind.REPAIRED));
        List<Reclaim> found = reclaimsFor(aCar(), stream, aTable(), JANUARY);
        assertThat(found.get(0).amount()).isEqualTo(new Money(463 * 96));
        assertThat(found.get(0).reason()).isSameAs(ReclaimReason.REPAIRS);
    }

    @Test
    void aReclaimPricesTheSameWayTheChargeDoes() {
        CarStream stream = aVisit(at(10, 8, EventKind.BAD_ORDERED), at(14, 8, EventKind.REPAIRED));
        Interval span = new Interval(utc(2025, 1, 10, 8), utc(2025, 1, 14, 8));
        List<Reclaim> found = reclaimsFor(aCar(), stream, aTable(), JANUARY);
        assertThat(found.get(0).amount()).isEqualTo(Accrual.hourly(aCar(), aTable(), span));
    }

    @Test
    void repairsWeAnswerForAreNotReclaimed() {
        CarStream stream = aVisit(at(10, 8, EventKind.BAD_ORDERED), at(14, 8, EventKind.REPAIRED));
        assertThat(reclaimsFor(aCar(), stream, aTable(), JANUARY, false)).isEmpty();
    }

    @Test
    void anEmbargoIsReclaimedWhoeverCausedTheRepair() {
        CarStream stream = aVisit(at(10, 8, EventKind.HELD), at(12, 8, EventKind.FREED));
        assertThat(reclaimsFor(aCar(), stream, aTable(), JANUARY, false))
                .extracting(Reclaim::reason)
                .containsExactly(ReclaimReason.EMBARGO);
    }

    @Test
    void aHoldOutsideTheWindowReclaimsNothing() {
        CarStream stream = streamOf(
                at(5, 12, EventKind.RECEIVED, CN),
                Event.of(EventKind.BAD_ORDERED, CAR, utc(2025, 2, 10, 8), "ESC"),
                Event.of(EventKind.REPAIRED, CAR, utc(2025, 2, 14, 8), "ESC"));
        assertThat(reclaimsFor(aCar(), stream, aTable(), JANUARY)).isEmpty();
    }

    @Test
    void aHoldStraddlingTheWindowIsCutToIt() {
        CarStream stream = streamOf(
                at(5, 12, EventKind.RECEIVED, CN),
                Event.of(EventKind.BAD_ORDERED, CAR, utc(2025, 1, 30, 6), "ESC"),
                Event.of(EventKind.REPAIRED, CAR, utc(2025, 2, 3, 6), "ESC"));
        List<Reclaim> found = reclaimsFor(aCar(), stream, aTable(), JANUARY);
        assertThat(found.get(0).interval().end()).isEqualTo(JANUARY.end());
    }

    @Test
    void aReclaimReportsItsHours() {
        CarStream stream = aVisit(at(10, 8, EventKind.BAD_ORDERED), at(14, 8, EventKind.REPAIRED));
        assertThat(reclaimsFor(aCar(), stream, aTable(), JANUARY).get(0).hundredthHours())
                .isEqualTo(9600);
    }

    @Test
    void aReclaimPrintsTheCarTheRuleAndTheMoney() {
        Reclaim reclaim = new Reclaim(CAR, ReclaimReason.REPAIRS, JANUARY, new Money(1234));
        assertThat(reclaim).hasToString("CN 400100 rule 5 $12.34");
    }

    @Test
    void aChargeComesDownByItsReclaims() {
        Reclaim reclaim = new Reclaim(CAR, ReclaimReason.REPAIRS, JANUARY, new Money(400));
        assertThat(netOfReclaims(new Money(1000), List.of(reclaim))).isEqualTo(new Money(600));
    }

    @Test
    void reclaimsBiggerThanTheChargeLeaveNothingOwed() {
        Reclaim reclaim = new Reclaim(CAR, ReclaimReason.REPAIRS, JANUARY, new Money(4000));
        assertThat(netOfReclaims(new Money(1000), List.of(reclaim))).isEqualTo(new Money(0));
    }

    @Test
    void aChargeWithNoReclaimsStands() {
        assertThat(netOfReclaims(new Money(1000), List.of())).isEqualTo(new Money(1000));
    }

    /** A placement at the mill track on a January day and hour, loaded on a waybill or empty. */
    static Event placedAt(int day, int hour, boolean loaded) {
        Event.Builder placed = Event.builder(EventKind.PLACED, CAR, utc(2025, 1, day, hour), "ESC")
                .industry("ESCPAP1");
        if (loaded) {
            placed.waybill(1);
        }
        return placed.build();
    }

    static Event onTheTrack(int day, int hour, EventKind kind) {
        return Event.builder(kind, CAR, utc(2025, 1, day, hour), "ESC")
                .industry("ESCPAP1")
                .build();
    }

    static final Set<ReclaimReason> WITH_SHIPPERS =
            Set.of(ReclaimReason.REPAIRS, ReclaimReason.SHIPPER_DELAY, ReclaimReason.EMBARGO);

    @Test
    void aLoadHeldPastTwoDaysIsReclaimedUntilTheShipperReleasesIt() {
        CarStream stream = aVisit(
                placedAt(8, 8, true), onTheTrack(13, 8, EventKind.RELEASED), onTheTrack(14, 8, EventKind.PULLED));
        assertThat(Reclaim.shipperDelays(stream, JANUARY.end()))
                .containsExactly(new Interval(utc(2025, 1, 10, 8), utc(2025, 1, 13, 8)));
        List<Reclaim> found = reclaimsFor(aCar(), stream, aTable(), JANUARY, WITH_SHIPPERS);
        assertThat(found).extracting(Reclaim::reason).containsExactly(ReclaimReason.SHIPPER_DELAY);
        assertThat(found.get(0).amount()).isEqualTo(new Money(463 * 72));
        assertThat(found.get(0)).hasToString("CN 400100 rule 15 $333.36");
    }

    @Test
    void aLoadReleasedInsideTwoDaysReclaimsNothing() {
        CarStream stream = aVisit(
                placedAt(8, 8, true), onTheTrack(10, 8, EventKind.RELEASED), onTheTrack(12, 8, EventKind.PULLED));
        assertThat(Reclaim.shipperDelays(stream, JANUARY.end())).isEmpty();
    }

    @Test
    void anEmptyPlacedForLoadingIsNotHeldUnderLoad() {
        CarStream stream = aVisit(
                placedAt(8, 8, false), onTheTrack(15, 8, EventKind.RELEASED), onTheTrack(16, 8, EventKind.PULLED));
        assertThat(Reclaim.shipperDelays(stream, JANUARY.end())).isEmpty();
    }

    @Test
    void aLoadNeverReleasedIsHeldToThePullOrTheCutOff() {
        CarStream pulled = aVisit(placedAt(8, 8, true), onTheTrack(12, 8, EventKind.PULLED));
        assertThat(Reclaim.shipperDelays(pulled, JANUARY.end()))
                .containsExactly(new Interval(utc(2025, 1, 10, 8), utc(2025, 1, 12, 8)));
        CarStream standing = streamOf(at(5, 12, EventKind.RECEIVED, CN), placedAt(28, 8, true));
        assertThat(Reclaim.shipperDelays(standing, JANUARY.end()))
                .containsExactly(new Interval(utc(2025, 1, 30, 8), JANUARY.end()));
    }

    @Test
    void theOrdinaryRulesLeaveShipperDelayOut() {
        CarStream stream = aVisit(
                placedAt(8, 8, true), onTheTrack(13, 8, EventKind.RELEASED), onTheTrack(14, 8, EventKind.PULLED));
        assertThat(reclaimsFor(aCar(), stream, aTable(), JANUARY)).isEmpty();
        assertThat(Reclaim.ORDINARY).doesNotContain(ReclaimReason.SHIPPER_DELAY);
        assertThat(reclaimsFor(aCar(), stream, aTable(), JANUARY, Set.of())).isEmpty();
    }

    @Test
    void reclaimsComeBackInRuleOrder() {
        CarStream stream = aVisit(
                at(6, 8, EventKind.HELD),
                at(7, 8, EventKind.FREED),
                placedAt(8, 8, true),
                onTheTrack(13, 8, EventKind.RELEASED),
                onTheTrack(14, 8, EventKind.PULLED),
                at(15, 8, EventKind.BAD_ORDERED),
                at(16, 8, EventKind.REPAIRED));
        assertThat(reclaimsFor(aCar(), stream, aTable(), JANUARY, WITH_SHIPPERS))
                .extracting(Reclaim::reason)
                .containsExactly(ReclaimReason.REPAIRS, ReclaimReason.SHIPPER_DELAY, ReclaimReason.EMBARGO);
        assertThat(Reclaim.holds(ReclaimReason.EMBARGO, stream, JANUARY.end())).hasSize(1);
    }

    @Test
    void aBadOrderUnderAnEmbargoIsReclaimedOnceUnderTheLowerRule() {
        CarStream stream = aVisit(
                at(10, 8, EventKind.HELD),
                at(11, 8, EventKind.BAD_ORDERED),
                at(13, 8, EventKind.REPAIRED),
                at(14, 8, EventKind.FREED));
        List<Reclaim> found = reclaimsFor(aCar(), stream, aTable(), JANUARY);
        assertThat(found)
                .extracting(Reclaim::reason, Reclaim::interval)
                .containsExactly(
                        tuple(ReclaimReason.REPAIRS, new Interval(utc(2025, 1, 11, 8), utc(2025, 1, 13, 8))),
                        tuple(ReclaimReason.EMBARGO, new Interval(utc(2025, 1, 10, 8), utc(2025, 1, 11, 8))),
                        tuple(ReclaimReason.EMBARGO, new Interval(utc(2025, 1, 13, 8), utc(2025, 1, 14, 8))));
        Money total = Money.total(found.stream().map(Reclaim::amount).toList());
        assertThat(total).isEqualTo(new Money(463 * 96));
    }

    @Test
    void whenTheOwnerDoesNotAnswerForTheRepairTheEmbargoTakesEveryHour() {
        CarStream stream = aVisit(
                at(10, 8, EventKind.HELD),
                at(11, 8, EventKind.BAD_ORDERED),
                at(13, 8, EventKind.REPAIRED),
                at(14, 8, EventKind.FREED));
        assertThat(reclaimsFor(aCar(), stream, aTable(), JANUARY, false))
                .extracting(Reclaim::interval)
                .containsExactly(new Interval(utc(2025, 1, 10, 8), utc(2025, 1, 14, 8)));
    }

    @Test
    void aShipperDelayUnderAnEmbargoIsNotReclaimedTwice() {
        CarStream stream = aVisit(
                placedAt(8, 8, true),
                at(11, 8, EventKind.HELD),
                at(12, 8, EventKind.FREED),
                onTheTrack(13, 8, EventKind.RELEASED),
                onTheTrack(14, 8, EventKind.PULLED));
        List<Reclaim> found = reclaimsFor(aCar(), stream, aTable(), JANUARY, WITH_SHIPPERS);
        assertThat(found).extracting(Reclaim::reason).containsExactly(ReclaimReason.SHIPPER_DELAY);
        assertThat(found.get(0).hundredthHours()).isEqualTo(7200);
    }
}
