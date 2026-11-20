package io.github.rkaushikdev.drawbarline.carhire;

import static io.github.rkaushikdev.drawbarline.carhire.Liability.chargesFor;
import static io.github.rkaushikdev.drawbarline.carhire.Liability.tenthsIn;
import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.UnknownCar;
import io.github.rkaushikdev.drawbarline.clock.Interval;
import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.events.EventKind;
import io.github.rkaushikdev.drawbarline.events.EventLog;
import io.github.rkaushikdev.drawbarline.fleet.Car;
import io.github.rkaushikdev.drawbarline.fleet.CarTypeCode;
import io.github.rkaushikdev.drawbarline.fleet.FleetRegister;
import io.github.rkaushikdev.drawbarline.fleet.MechanicalDesignation;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.measure.Length;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LiabilityTest {

    static final CarTypeCode HOPPER = new CarTypeCode("L351");
    static final ReportingMark HOME = new ReportingMark("DBL");
    static final ReportingMark CN = new ReportingMark("CN");
    static final ReportingMark ELS = new ReportingMark("ELS");
    static final Interval JANUARY = new Interval(utc(2025, 1, 1, 6), utc(2025, 2, 1, 6));

    static Car aCar(String mark) {
        return new Car(
                CarMark.parse(mark),
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

    static void visit(EventLog log, String mark, int receivedDay) {
        visit(log, mark, receivedDay, 0, 0);
    }

    static void visit(EventLog log, String mark, int receivedDay, int deliveredDay) {
        visit(log, mark, receivedDay, deliveredDay, 0);
    }

    /** A receipt, a movement when there are tenths to move, and a delivery when there is a day for one. */
    static void visit(EventLog log, String mark, int receivedDay, int deliveredDay, int tenths) {
        CarMark car = CarMark.parse(mark);
        log.record(Event.builder(EventKind.RECEIVED, car, utc(2025, 1, receivedDay, 12), "ESC")
                .road(CN)
                .build());
        if (tenths != 0) {
            log.record(Event.builder(EventKind.MOVED, car, utc(2025, 1, receivedDay, 18), "PWR")
                    .tenths(tenths)
                    .build());
        }
        if (deliveredDay != 0) {
            log.record(Event.builder(EventKind.DELIVERED, car, utc(2025, 1, deliveredDay, 12), "MRN")
                    .road(CN)
                    .build());
        }
    }

    /** A register of three cars, an empty log and a one-band schedule. */
    record World(FleetRegister register, EventLog log, RateTable table) {}

    static World aWorld() {
        FleetRegister register = new FleetRegister();
        for (String mark : List.of("CN 400100", "DBL 104207", "PLMX 9001")) {
            register.add(aCar(mark));
        }
        return new World(register, new EventLog(), aTable());
    }

    static List<Charge> charges(World world, String mark) {
        return chargesFor(world.register(), world.log(), world.table(), CarMark.parse(mark), JANUARY, HOME);
    }

    static Liability run(World world) {
        return Liability.run(world.register(), world.log(), world.table(), JANUARY, HOME);
    }

    @Test
    void aForeignCarHereTwoDaysEarnsTwoDaysOfHours() {
        World world = aWorld();
        visit(world.log(), "CN 400100", 10, 12);
        List<Charge> found = charges(world, "CN 400100");
        assertThat(found.get(0).hours()).isEqualTo(new Money(463 * 48));
        assertThat(found.get(0).owedTo()).isEqualTo(CN);
    }

    @Test
    void ourOwnCarOnOurOwnRailEarnsNothing() {
        World world = aWorld();
        visit(world.log(), "DBL 104207", 10, 12);
        assertThat(charges(world, "DBL 104207")).isEmpty();
    }

    @Test
    void ourOwnCarOutOnLeaseEarnsForTheLessee() {
        World world = aWorld();
        world.register().ownership(CarMark.parse("DBL 104207")).lease(ELS, LocalDate.of(2025, 1, 1));
        visit(world.log(), "DBL 104207", 10, 12);
        assertThat(charges(world, "DBL 104207").get(0).owedTo()).isEqualTo(ELS);
    }

    @Test
    void aPrivateCarEarnsMileageAndNoHours() {
        World world = aWorld();
        visit(world.log(), "PLMX 9001", 10, 12, 336);
        List<Charge> found = charges(world, "PLMX 9001");
        assertThat(found.get(0).hours()).isEqualTo(new Money(0));
        assertThat(found.get(0).miles()).isEqualTo(new Money(4066));
    }

    @Test
    void aRailroadCarEarnsBothHalves() {
        World world = aWorld();
        visit(world.log(), "CN 400100", 10, 12, 336);
        List<Charge> found = charges(world, "CN 400100");
        assertThat(found.get(0).hours().cents()).isPositive();
        assertThat(found.get(0).miles().cents()).isPositive();
        assertThat(found.get(0).amount())
                .isEqualTo(found.get(0).hours().plus(found.get(0).miles()));
    }

    @Test
    void aCarStillHereIsChargedToTheWindowEnd() {
        World world = aWorld();
        visit(world.log(), "CN 400100", 31);
        assertThat(charges(world, "CN 400100").get(0).window().end()).isEqualTo(JANUARY.end());
    }

    @Test
    void aCarThatCameAfterTheWindowRaisesNothing() {
        World world = aWorld();
        world.log()
                .record(Event.builder(EventKind.RECEIVED, CarMark.parse("CN 400100"), utc(2025, 3, 1, 12), "ESC")
                        .road(CN)
                        .build());
        assertThat(charges(world, "CN 400100")).isEmpty();
    }

    @Test
    void aCarNobodyFiledIsRefused() {
        World world = aWorld();
        assertThatThrownBy(() -> charges(world, "WC 1")).isInstanceOf(UnknownCar.class);
    }

    @Test
    void theMilesInsideAWindowAreTheOnesCounted() {
        World world = aWorld();
        CarMark car = CarMark.parse("CN 400100");
        world.log()
                .record(Event.builder(EventKind.RECEIVED, car, utc(2025, 1, 10, 12), "ESC")
                        .road(CN)
                        .build());
        world.log()
                .record(Event.builder(EventKind.MOVED, car, utc(2025, 1, 11, 12), "PWR")
                        .tenths(336)
                        .build());
        world.log()
                .record(Event.builder(EventKind.MOVED, car, utc(2025, 2, 11, 12), "IMT")
                        .tenths(286)
                        .build());
        assertThat(tenthsIn(world.log().stream(car), JANUARY)).isEqualTo(336);
    }

    @Test
    void aChargePrintsTheCarTheRoadAndTheMoney() {
        Charge charge = new Charge(CarMark.parse("CN 400100"), CN, JANUARY, new Money(1000), new Money(200));
        assertThat(charge).hasToString("CN 400100 to CN $12.00");
    }

    @Test
    void aChargeOfNothingSaysSo() {
        Charge charge = new Charge(CarMark.parse("CN 400100"), CN, JANUARY, new Money(0), new Money(0));
        assertThat(charge.isFree()).isTrue();
    }

    @Test
    void theRunGathersEveryCarInTheLog() {
        World world = aWorld();
        visit(world.log(), "CN 400100", 10, 12);
        visit(world.log(), "PLMX 9001", 10, 12, 100);
        Liability found = run(world);
        assertThat(found.size()).isEqualTo(2);
        assertThat(found.cars()).containsExactly(CarMark.parse("CN 400100"), CarMark.parse("PLMX 9001"));
    }

    @Test
    void theRunSkipsACarWithNoEquipmentRecord() {
        World world = aWorld();
        visit(world.log(), "CN 400100", 10, 12);
        world.log()
                .record(Event.builder(EventKind.RECEIVED, CarMark.parse("WC 1"), utc(2025, 1, 10, 12), "ESC")
                        .road(CN)
                        .build());
        assertThat(run(world).size()).isEqualTo(1);
    }

    @Test
    void theRunTotalsByRoad() {
        World world = aWorld();
        visit(world.log(), "CN 400100", 10, 12);
        world.register().ownership(CarMark.parse("DBL 104207")).lease(ELS, LocalDate.of(2025, 1, 1));
        visit(world.log(), "DBL 104207", 10, 12);
        Map<ReportingMark, Money> byRoad = run(world).byRoad();
        assertThat(byRoad.keySet()).isEqualTo(Set.of(CN, ELS));
    }

    @Test
    void theTotalsSplitIntoHoursAndMiles() {
        World world = aWorld();
        visit(world.log(), "CN 400100", 10, 12, 336);
        Liability found = run(world);
        assertThat(found.total()).isEqualTo(found.hoursTotal().plus(found.milesTotal()));
    }

    @Test
    void chargesForOneRoadComeBackOnTheirOwn() {
        World world = aWorld();
        visit(world.log(), "CN 400100", 10, 12);
        Liability found = run(world);
        assertThat(found.forRoad(CN)).hasSize(1);
        assertThat(found.forRoad(ELS)).isEmpty();
    }

    @Test
    void chargesComeBackSortedByRoadThenCar() {
        World world = aWorld();
        visit(world.log(), "PLMX 9001", 10, 12, 100);
        visit(world.log(), "CN 400100", 10, 12);
        assertThat(run(world).charges())
                .extracting(charge -> charge.car().toString())
                .containsExactly("CN 400100", "PLMX 009001");
    }

    @Test
    void anEmptyLogOwesNothing() {
        assertThat(run(aWorld()).total()).isEqualTo(new Money(0));
    }
}
