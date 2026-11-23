package io.github.rkaushikdev.drawbarline.interchange;

import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static io.github.rkaushikdev.drawbarline.support.Cars.aFleet;
import static io.github.rkaushikdev.drawbarline.support.Moves.received;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.agreements.InterchangeAgreement;
import io.github.rkaushikdev.drawbarline.embargo.Embargo;
import io.github.rkaushikdev.drawbarline.embargo.EmbargoRegister;
import io.github.rkaushikdev.drawbarline.embargo.Permit;
import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.events.EventKind;
import io.github.rkaushikdev.drawbarline.events.EventLog;
import io.github.rkaushikdev.drawbarline.fleet.CarKind;
import io.github.rkaushikdev.drawbarline.fleet.FleetRegister;
import io.github.rkaushikdev.drawbarline.lading.Commodity;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.network.Station;
import io.github.rkaushikdev.drawbarline.reference.Reference;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeliveryTest {

    static final ReportingMark CN = new ReportingMark("CN");
    static final CarMark CAR = CarMark.parse("CN 400100");
    static final Station ESC = Reference.drawbarLine().station("ESC");
    static final LocalDateTime MORNING = LocalDateTime.of(2025, 1, 14, 9, 0);

    static InterchangeAgreement anAgreement() {
        return anAgreement(Set.of(CarKind.COVERED_HOPPER), false);
    }

    static InterchangeAgreement anAgreement(Set<CarKind> kinds) {
        return anAgreement(kinds, false);
    }

    static InterchangeAgreement anAgreement(Set<CarKind> kinds, boolean hazardous) {
        return new InterchangeAgreement(
                CN,
                "ESC",
                LocalDate.of(2024, 7, 1),
                kinds,
                2,
                LocalTime.of(7, 0),
                LocalTime.of(19, 0),
                hazardous,
                Optional.empty());
    }

    static EventLog aLog() {
        return aLog(0);
    }

    static EventLog aLog(int delivered) {
        EventLog log = new EventLog();
        received(log, "CN 400100", 6);
        for (int at = 0; at < delivered; at++) {
            CarMark car = CarMark.parse("CN " + (400200 + at));
            log.record(Event.builder(EventKind.DELIVERED, car, utc(2025, 1, 14, 14), "ESC")
                    .road(CN)
                    .build());
        }
        return log;
    }

    /** What the crew offers, each part the usual one unless a test changes it. */
    static final class Offer {
        FleetRegister fleet = aFleet("CN 400100");
        EventLog log = aLog();
        EmbargoRegister embargoes = new EmbargoRegister();
        InterchangeAgreement agreement = anAgreement();
        Station station = ESC;
        LocalDateTime local = MORNING;
        Optional<Commodity> commodity = Optional.empty();

        Verdict verdict() {
            return Delivery.canDeliver(CAR, fleet, log, embargoes, agreement, station, local, commodity);
        }
    }

    static Verdict aVerdict() {
        return new Offer().verdict();
    }

    static EmbargoRegister oneEmbargo(Embargo embargo) {
        EmbargoRegister embargoes = new EmbargoRegister();
        embargoes.issue(embargo);
        return embargoes;
    }

    @Test
    void nothingDeliveredYetCountsNone() {
        assertThat(Delivery.deliveredToday(aLog(), "ESC", CN, LocalDate.of(2025, 1, 14), ESC))
                .isZero();
    }

    @Test
    void aDeliveryTodayIsCounted() {
        assertThat(Delivery.deliveredToday(aLog(2), "ESC", CN, LocalDate.of(2025, 1, 14), ESC))
                .isEqualTo(2);
    }

    @Test
    void aDeliveryToAnotherRoadIsNotCounted() {
        EventLog log = aLog();
        log.record(Event.builder(EventKind.DELIVERED, CarMark.parse("CN 400300"), utc(2025, 1, 14, 14), "ESC")
                .road(new ReportingMark("ELS"))
                .build());
        assertThat(Delivery.deliveredToday(log, "ESC", CN, LocalDate.of(2025, 1, 14), ESC))
                .isZero();
    }

    @Test
    void aDeliveryOnAnotherDayIsNotCounted() {
        assertThat(Delivery.deliveredToday(aLog(2), "ESC", CN, LocalDate.of(2025, 1, 15), ESC))
                .isZero();
    }

    @Test
    void aSoundCarGoesAcross() {
        Verdict found = aVerdict();
        assertThat(found.goes()).isTrue();
        assertThat(found).hasToString("CN 400100 goes to CN at ESC");
    }

    @Test
    void aCarNobodyFiledIsHeld() {
        Verdict found = Delivery.canDeliver(CAR, aFleet(), aLog(), new EmbargoRegister(), anAgreement(), ESC, MORNING);
        assertThat(found.goes()).isFalse();
        assertThat(found.findings().get(0)).contains("no equipment record");
    }

    @Test
    void aRetiredCarIsHeld() {
        FleetRegister fleet = aFleet("CN 400100");
        fleet.retire(CAR, LocalDate.of(2024, 12, 31));
        Offer offer = new Offer();
        offer.fleet = fleet;
        assertThat(offer.verdict().findings()).anyMatch(one -> one.contains("retired"));
    }

    @Test
    void anEmbargoAtTheJunctionHoldsTheCar() {
        Offer offer = new Offer();
        offer.embargoes = oneEmbargo(Embargo.builder(101, LocalDate.of(2025, 1, 6), "bridge out")
                .station("ESC")
                .build());
        assertThat(offer.verdict().findings()).anyMatch(one -> one.contains("embargo 101"));
    }

    @Test
    void anEmbargoSomewhereElseDoesNot() {
        Offer offer = new Offer();
        offer.embargoes = oneEmbargo(Embargo.builder(101, LocalDate.of(2025, 1, 6), "bridge out")
                .station("HRM")
                .build());
        assertThat(offer.verdict().goes()).isTrue();
    }

    @Test
    void aCommodityEmbargoHoldsACarCarryingIt() {
        Offer offer = new Offer();
        offer.embargoes = oneEmbargo(Embargo.builder(101, LocalDate.of(2025, 1, 6), "elevator full")
                .commodityFamily("01")
                .build());
        offer.commodity = Optional.of(Commodity.WHEAT);
        assertThat(offer.verdict().goes()).isFalse();
    }

    @Test
    void aKindTheRoadDoesNotTakeIsHeld() {
        Offer offer = new Offer();
        offer.agreement = anAgreement(Set.of(CarKind.TANK));
        assertThat(offer.verdict().findings()).anyMatch(one -> one.contains("does not take"));
    }

    @Test
    void hazardousMaterialIsHeldUnlessTheAgreementAllowsIt() {
        Offer offer = new Offer();
        offer.commodity = Optional.of(Commodity.SODIUM_HYDROXIDE);
        assertThat(offer.verdict().findings()).anyMatch(one -> one.contains("hazardous"));
    }

    @Test
    void anAgreementThatAllowsHazardousLetsItGo() {
        Offer offer = new Offer();
        offer.agreement = anAgreement(Set.of(CarKind.COVERED_HOPPER), true);
        offer.commodity = Optional.of(Commodity.SODIUM_HYDROXIDE);
        assertThat(offer.verdict().goes()).isTrue();
    }

    @Test
    void aCarOfferedWhenTheJunctionIsShutIsHeld() {
        Offer offer = new Offer();
        offer.local = LocalDateTime.of(2025, 1, 14, 22, 0);
        assertThat(offer.verdict().findings()).anyMatch(one -> one.contains("closed at 22:00"));
    }

    @Test
    void aCarPastTheDailyCapIsHeld() {
        Offer offer = new Offer();
        offer.log = aLog(2);
        assertThat(offer.verdict().findings()).anyMatch(one -> one.contains("taken its 2 cars"));
    }

    @Test
    void theCarThatMakesTheCapStillGoes() {
        Offer offer = new Offer();
        offer.log = aLog(1);
        assertThat(offer.verdict().goes()).isTrue();
    }

    @Test
    void everyReasonComesBackTogether() {
        Offer offer = new Offer();
        offer.embargoes = oneEmbargo(Embargo.builder(101, LocalDate.of(2025, 1, 6), "bridge out")
                .station("ESC")
                .build());
        offer.agreement = anAgreement(Set.of(CarKind.TANK));
        offer.local = LocalDateTime.of(2025, 1, 14, 22, 0);
        assertThat(offer.verdict().findings()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void aHeldCarPrintsTheFirstReason() {
        Offer offer = new Offer();
        offer.agreement = anAgreement(Set.of(CarKind.TANK));
        assertThat(offer.verdict().toString()).startsWith("CN 400100 held at ESC:");
    }

    @Test
    void aCarWithAPermitGoesThroughTheEmbargoItWasGrantedOn() {
        Offer offer = new Offer();
        offer.embargoes = oneEmbargo(Embargo.builder(101, LocalDate.of(2025, 1, 6), "bridge out")
                .station("ESC")
                .permitsAllowed(true)
                .build());
        assertThat(offer.verdict().goes()).isFalse();
        offer.embargoes.grant(new Permit(9, 101, CAR, LocalDate.of(2025, 1, 13), LocalDate.of(2025, 1, 15), "ESCPAP"));
        assertThat(offer.verdict().goes()).isTrue();
        offer.local = LocalDateTime.of(2025, 1, 16, 9, 0);
        assertThat(offer.verdict().goes()).isFalse();
    }
}
