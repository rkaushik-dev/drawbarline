package io.github.rkaushikdev.drawbarline.distribution;

import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static io.github.rkaushikdev.drawbarline.distribution.Distribution.distribute;
import static io.github.rkaushikdev.drawbarline.distribution.Distribution.emptiesOnHand;
import static io.github.rkaushikdev.drawbarline.support.Cars.aCar;
import static io.github.rkaushikdev.drawbarline.support.Cars.aFleet;
import static io.github.rkaushikdev.drawbarline.support.Moves.badOrdered;
import static io.github.rkaushikdev.drawbarline.support.Moves.placed;
import static io.github.rkaushikdev.drawbarline.support.Moves.received;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.events.EventLog;
import io.github.rkaushikdev.drawbarline.fleet.CarKind;
import io.github.rkaushikdev.drawbarline.fleet.FleetRegister;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DistributionTest {

    static final Map<String, String> STATIONS = Map.of("ESCPAP1", "ESC", "PWRGRN1", "PWR", "IMTMIN1", "IMT");
    static final Instant NOW = utc(2025, 1, 14, 12);
    static final CarKind HOPPER = CarKind.COVERED_HOPPER;
    static final LocalDate TUESDAY = LocalDate.of(2025, 1, 14);

    static CarOrder anOrder(int number, int cars, String industry, LocalDate wanted, CarKind kind) {
        return new CarOrder(number, "ESCPAP", industry, kind, cars, wanted);
    }

    static CarOrder anOrder() {
        return anOrder(1);
    }

    static CarOrder anOrder(int number) {
        return anOrder(number, 1, "ESCPAP1", TUESDAY, HOPPER);
    }

    static CarOrder anOrder(int number, LocalDate wanted) {
        return anOrder(number, 1, "ESCPAP1", wanted, HOPPER);
    }

    static CarOrder anOrderFor(int cars) {
        return anOrder(1, cars, "ESCPAP1", TUESDAY, HOPPER);
    }

    static CarOrder anOrderOn(String industry) {
        return anOrder(1, 1, industry, TUESDAY, HOPPER);
    }

    static Empty anEmpty(int number) {
        return anEmpty(number, "ESC", HOPPER);
    }

    static Empty anEmpty(int number, String station) {
        return anEmpty(number, station, HOPPER);
    }

    static Empty anEmpty(int number, String station, CarKind kind) {
        return new Empty(CarMark.parse("CN " + number), kind, station);
    }

    static List<String> cars(Distribution found) {
        return found.assignments().stream().map(one -> one.car().toString()).toList();
    }

    static <T> List<T> reversed(List<T> items) {
        List<T> copy = new ArrayList<>(items);
        Collections.reverse(copy);
        return copy;
    }

    @Test
    void anOrderPrintsWhatItAsksFor() {
        assertThat(anOrderFor(4)).hasToString("order 1 ESCPAP 4 covered hopper for 2025-01-14");
    }

    @Test
    void anOrderNumberedBelowOneIsRefused() {
        assertThatThrownBy(() -> anOrder(0)).isInstanceOf(DistributionError.class);
    }

    @Test
    void anOrderForNoCarsIsRefused() {
        assertThatThrownBy(() -> anOrderFor(0)).isInstanceOf(DistributionError.class);
    }

    @Test
    void anOrderWithNoTrackIsRefused() {
        assertThatThrownBy(() -> anOrderOn("  ")).isInstanceOf(DistributionError.class);
    }

    @Test
    void theOldestOrderComesFirst() {
        CarOrder early = anOrder(2, LocalDate.of(2025, 1, 10));
        CarOrder late = anOrder(1, LocalDate.of(2025, 1, 14));
        assertThat(Stream.of(late, early)
                        .sorted(Comparator.comparing(CarOrder::priority))
                        .findFirst())
                .containsSame(early);
    }

    @Test
    void twoOrdersTheSameDayGoByNumber() {
        CarOrder first = anOrder(1);
        CarOrder second = anOrder(2);
        assertThat(Stream.of(second, first)
                        .sorted(Comparator.comparing(CarOrder::priority))
                        .findFirst())
                .containsSame(first);
    }

    @Test
    void oneEmptyFillsOneOrder() {
        Distribution found = distribute(List.of(anOrder()), List.of(anEmpty(400100)), STATIONS);
        assertThat(found.filled()).isEqualTo(1);
        assertThat(found.isComplete()).isTrue();
    }

    @Test
    void anOrderWithNothingSuitableGoesShort() {
        Distribution found = distribute(List.of(anOrderFor(2)), List.of(anEmpty(400100)), STATIONS);
        assertThat(found.filled()).isEqualTo(1);
        assertThat(found.shortages()).isEqualTo(Map.of(1, 1));
        assertThat(found.isComplete()).isFalse();
    }

    @Test
    void aCarOfTheWrongKindIsNotOffered() {
        Distribution found = distribute(List.of(anOrder()), List.of(anEmpty(400100, "ESC", CarKind.TANK)), STATIONS);
        assertThat(found.filled()).isZero();
        assertThat(found.unfilled()).isEqualTo(1);
    }

    @Test
    void theNearestEmptyIsTaken() {
        List<Empty> empties = List.of(anEmpty(400100, "GRB"), anEmpty(400101, "WLS"));
        Distribution found = distribute(List.of(anOrder()), empties, STATIONS);
        assertThat(found.assignments().get(0).car()).hasToString("CN 400101");
    }

    @Test
    void aTieOnDistanceGoesToTheLowerCarNumber() {
        List<Empty> empties = List.of(anEmpty(400102, "WLS"), anEmpty(400101, "WLS"));
        Distribution found = distribute(List.of(anOrder()), empties, STATIONS);
        assertThat(found.assignments().get(0).car()).hasToString("CN 400101");
    }

    @Test
    void twoRunsOfTheSameMorningAgree() {
        List<Empty> empties = List.of(anEmpty(400103, "GRB"), anEmpty(400101, "WLS"), anEmpty(400102, "WLS"));
        List<CarOrder> orders = List.of(anOrder(1), anOrder(2));
        Distribution first = distribute(orders, empties, STATIONS);
        Distribution second = distribute(reversed(orders), reversed(empties), STATIONS);
        assertThat(cars(first)).isEqualTo(cars(second));
    }

    @Test
    void anEmptyIsNeverAssignedTwice() {
        Distribution found = distribute(List.of(anOrder(1), anOrder(2)), List.of(anEmpty(400100)), STATIONS);
        assertThat(found.filled()).isEqualTo(1);
        assertThat(found.shortages()).isEqualTo(Map.of(2, 1));
    }

    @Test
    void theOlderOrderIsFilledFirst() {
        List<CarOrder> orders = List.of(anOrder(1, LocalDate.of(2025, 1, 20)), anOrder(2, LocalDate.of(2025, 1, 10)));
        Distribution found = distribute(orders, List.of(anEmpty(400100)), STATIONS);
        assertThat(found.assignments().get(0).order()).isEqualTo(2);
    }

    @Test
    void anAssignmentCarriesTheDistance() {
        Distribution found = distribute(List.of(anOrder()), List.of(anEmpty(400100, "PWR")), STATIONS);
        assertThat(found.assignments().get(0).tenths()).isEqualTo(336);
    }

    @Test
    void theEmptyMileageAddsUp() {
        List<Empty> empties = List.of(anEmpty(400100, "PWR"), anEmpty(400101, "PWR"));
        Distribution found = distribute(List.of(anOrderFor(2)), empties, STATIONS);
        assertThat(found.emptyTenths()).isEqualTo(672);
    }

    @Test
    void anAssignmentPrintsWhereItGoes() {
        Distribution found = distribute(List.of(anOrder()), List.of(anEmpty(400100, "PWR")), STATIONS);
        assertThat(found.assignments().get(0)).hasToString("CN 400100 PWR to ESC for order 1");
    }

    @Test
    void theAssignmentsForOneOrderComeBack() {
        List<CarOrder> orders = List.of(anOrder(1, 2, "ESCPAP1", TUESDAY, HOPPER), anOrder(2));
        List<Empty> empties = List.of(anEmpty(1), anEmpty(2), anEmpty(3));
        Distribution found = distribute(orders, empties, STATIONS);
        assertThat(found.forOrder(1)).hasSize(2);
    }

    @Test
    void aTrackWithNoStationIsRefused() {
        assertThatThrownBy(() -> distribute(List.of(anOrderOn("NOWHERE")), List.of(anEmpty(400100)), STATIONS))
                .isInstanceOf(DistributionError.class);
    }

    @Test
    void aCompleteDistributionPrintsAsComplete() {
        Distribution found = distribute(List.of(anOrder()), List.of(anEmpty(400100)), STATIONS);
        assertThat(found).hasToString("1 cars assigned, every order filled");
    }

    @Test
    void aShortDistributionSaysHowShort() {
        Distribution found = distribute(List.of(anOrderFor(3)), List.of(anEmpty(400100)), STATIONS);
        assertThat(found).hasToString("1 cars assigned, 2 short");
    }

    @Test
    void anEmptyCarOnOurRailIsOnHand() {
        FleetRegister register = aFleet("CN 400100");
        EventLog log = new EventLog();
        received(log, "CN 400100", 6, 12, null);
        assertThat(emptiesOnHand(register, log, NOW))
                .extracting(one -> one.car().toString())
                .containsExactly("CN 400100");
    }

    @Test
    void aLoadedCarIsNotAnEmpty() {
        FleetRegister register = aFleet("CN 400100");
        EventLog log = new EventLog();
        received(log, "CN 400100", 6, 12, 41207);
        assertThat(emptiesOnHand(register, log, NOW)).isEmpty();
    }

    @Test
    void aPlacedCarIsNotOnHand() {
        FleetRegister register = aFleet("CN 400100");
        EventLog log = new EventLog();
        received(log, "CN 400100", 6, 12, null);
        placed(log, "CN 400100", 7);
        assertThat(emptiesOnHand(register, log, NOW)).isEmpty();
    }

    @Test
    void aBadOrderedCarIsNotOffered() {
        FleetRegister register = aFleet("CN 400100");
        EventLog log = new EventLog();
        received(log, "CN 400100", 6, 12, null);
        badOrdered(log, "CN 400100", 7);
        assertThat(emptiesOnHand(register, log, NOW)).isEmpty();
    }

    @Test
    void aCarWithNoEquipmentRecordIsLeftOut() {
        FleetRegister register = aFleet();
        EventLog log = new EventLog();
        received(log, "CN 400100", 6, 12, null);
        assertThat(emptiesOnHand(register, log, NOW)).isEmpty();
    }

    @Test
    void anEmptyCarriesItsKindAndStation() {
        FleetRegister register = aFleet();
        register.add(aCar("CN 400100"));
        EventLog log = new EventLog();
        received(log, "CN 400100", 6, 12, null);
        Empty found = emptiesOnHand(register, log, NOW).get(0);
        assertThat(found.kind()).isSameAs(HOPPER);
        assertThat(found.station()).isEqualTo("ESC");
        assertThat(found).hasToString("CN 400100 at ESC");
    }
}
