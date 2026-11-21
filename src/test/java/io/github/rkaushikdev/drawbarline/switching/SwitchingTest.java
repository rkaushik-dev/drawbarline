package io.github.rkaushikdev.drawbarline.switching;

import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static io.github.rkaushikdev.drawbarline.support.Moves.badOrdered;
import static io.github.rkaushikdev.drawbarline.support.Moves.held;
import static io.github.rkaushikdev.drawbarline.support.Moves.placed;
import static io.github.rkaushikdev.drawbarline.support.Moves.received;
import static io.github.rkaushikdev.drawbarline.support.Moves.released;
import static io.github.rkaushikdev.drawbarline.switching.Switching.carsOnTrack;
import static io.github.rkaushikdev.drawbarline.switching.Switching.plan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.customers.CustomerRegister;
import io.github.rkaushikdev.drawbarline.customers.Industry;
import io.github.rkaushikdev.drawbarline.customers.Shipper;
import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.events.EventKind;
import io.github.rkaushikdev.drawbarline.events.EventLog;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SwitchingTest {

    static final Shipper MILL = new Shipper("ESCPAP", "Escanaba Paper");
    static final LocalDate TUESDAY = LocalDate.of(2025, 1, 14);
    static final LocalDate SATURDAY = LocalDate.of(2025, 1, 18);
    static final Instant NOW = utc(2025, 1, 14, 12);

    static CustomerRegister customers() {
        return customers(3);
    }

    static CustomerRegister customers(int spots) {
        CustomerRegister register = new CustomerRegister();
        register.addShipper(MILL);
        register.addIndustry(new Industry("ESCPAP1", "mill house", "ESC", MILL, spots));
        register.addIndustry(new Industry("ESCPAP2", "mill north", "ESC", MILL, 2, Set.of("mon", "wed")));
        register.addIndustry(new Industry("PWRGRN1", "elevator", "PWR", MILL, 20));
        return register;
    }

    static EventLog aLog(String... marks) {
        EventLog log = new EventLog();
        for (int at = 0; at < marks.length; at++) {
            received(log, marks[at], at + 1);
        }
        return log;
    }

    static List<Placement> spot(String mark, String industry) {
        return List.of(new Placement(CarMark.parse(mark), industry));
    }

    static List<MoveKind> kinds(SwitchList found) {
        return found.moves().stream().map(Move::kind).toList();
    }

    @Test
    void aTrackWithNothingOnItHoldsNothing() {
        assertThat(carsOnTrack(aLog("CN 400100"), "ESCPAP1", NOW)).isEmpty();
    }

    @Test
    void aPlacedCarStandsOnItsTrack() {
        EventLog log = aLog("CN 400100");
        placed(log, "CN 400100", 5);
        assertThat(carsOnTrack(log, "ESCPAP1", NOW)).containsExactly(CarMark.parse("CN 400100"));
    }

    @Test
    void aReleasedCarIsPulled() {
        EventLog log = aLog("CN 400100");
        placed(log, "CN 400100", 5);
        released(log, "CN 400100", 8);
        SwitchList found = plan(log, customers(), "ESC", TUESDAY, NOW, List.of());
        assertThat(kinds(found)).containsExactly(MoveKind.PULL);
    }

    @Test
    void aCarTheCustomerHasNotReleasedStaysPut() {
        EventLog log = aLog("CN 400100");
        placed(log, "CN 400100", 5);
        assertThat(plan(log, customers(), "ESC", TUESDAY, NOW, List.of()).moves())
                .isEmpty();
    }

    @Test
    void aCarIsPlacedWhenThereIsRoom() {
        EventLog log = aLog("CN 400100");
        SwitchList found = plan(log, customers(), "ESC", TUESDAY, NOW, spot("CN 400100", "ESCPAP1"));
        assertThat(kinds(found)).containsExactly(MoveKind.PLACE);
    }

    @Test
    void aFullTrackTakesAConstructivePlacement() {
        EventLog log = aLog("CN 400100", "CN 400101", "CN 400102");
        for (String mark : List.of("CN 400100", "CN 400101")) {
            placed(log, mark, 5);
        }
        SwitchList found = plan(log, customers(2), "ESC", TUESDAY, NOW, spot("CN 400102", "ESCPAP1"));
        assertThat(kinds(found)).containsExactly(MoveKind.CONSTRUCTIVE);
    }

    @Test
    void aConstructivePlacementSaysHowManyWereOnHand() {
        EventLog log = aLog("CN 400100", "CN 400101", "CN 400102");
        for (String mark : List.of("CN 400100", "CN 400101")) {
            placed(log, mark, 5);
        }
        SwitchList found = plan(log, customers(2), "ESC", TUESDAY, NOW, spot("CN 400102", "ESCPAP1"));
        assertThat(found.moves().get(0).note()).isEqualTo("2 on hand");
    }

    @Test
    void aPullMakesRoomForAPlacement() {
        EventLog log = aLog("CN 400100", "CN 400101", "CN 400102");
        for (String mark : List.of("CN 400100", "CN 400101")) {
            placed(log, mark, 5);
        }
        released(log, "CN 400100", 8);
        SwitchList found = plan(log, customers(2), "ESC", TUESDAY, NOW, spot("CN 400102", "ESCPAP1"));
        assertThat(kinds(found)).containsExactly(MoveKind.PULL, MoveKind.PLACE);
    }

    @Test
    void aTrackWeDoNotSwitchTodayIsNotWorked() {
        EventLog log = aLog("CN 400100");
        SwitchList found = plan(log, customers(), "ESC", TUESDAY, NOW, spot("CN 400100", "ESCPAP2"));
        assertThat(kinds(found)).containsExactly(MoveKind.NOT_SWITCHED);
    }

    @Test
    void aReleasedCarOnATrackWeDoNotSwitchStays() {
        EventLog log = aLog("CN 400100");
        log.record(Event.builder(EventKind.PLACED, CarMark.parse("CN 400100"), utc(2025, 1, 5, 20), "ESC")
                .industry("ESCPAP2")
                .build());
        assertThat(plan(log, customers(), "ESC", TUESDAY, NOW, List.of()).moves())
                .isEmpty();
    }

    @Test
    void aBadOrderedCarDoesNotMove() {
        EventLog log = aLog("CN 400100");
        badOrdered(log, "CN 400100", 6);
        SwitchList found = plan(log, customers(), "ESC", TUESDAY, NOW, spot("CN 400100", "ESCPAP1"));
        assertThat(found.moves().get(0).kind()).isSameAs(MoveKind.STOPPED);
        assertThat(found.moves().get(0).note()).isEqualTo("bad order");
    }

    @Test
    void aHeldCarDoesNotMove() {
        EventLog log = aLog("CN 400100");
        held(log, "CN 400100", 6);
        SwitchList found = plan(log, customers(), "ESC", TUESDAY, NOW, spot("CN 400100", "ESCPAP1"));
        assertThat(found.moves().get(0).note()).isEqualTo("held");
    }

    @Test
    void aCarBothBadOrderedAndHeldSaysBoth() {
        EventLog log = aLog("CN 400100");
        badOrdered(log, "CN 400100", 6);
        held(log, "CN 400100", 7);
        SwitchList found = plan(log, customers(), "ESC", TUESDAY, NOW, spot("CN 400100", "ESCPAP1"));
        assertThat(found.moves().get(0).note()).isEqualTo("bad order and held");
    }

    @Test
    void aBadOrderedCarOnATrackIsNotPulled() {
        EventLog log = aLog("CN 400100");
        placed(log, "CN 400100", 5);
        released(log, "CN 400100", 6);
        badOrdered(log, "CN 400100", 7);
        SwitchList found = plan(log, customers(), "ESC", TUESDAY, NOW, List.of());
        assertThat(found.moves().get(0).kind()).isSameAs(MoveKind.STOPPED);
    }

    @Test
    void aCarNotOnOurRailCannotBeOrdered() {
        assertThatThrownBy(() -> plan(new EventLog(), customers(), "ESC", TUESDAY, NOW, spot("CN 400100", "ESCPAP1")))
                .isInstanceOf(SwitchError.class);
    }

    @Test
    void aTrackAtAnotherStationIsRefused() {
        EventLog log = aLog("CN 400100");
        assertThatThrownBy(() -> plan(log, customers(), "ESC", TUESDAY, NOW, spot("CN 400100", "PWRGRN1")))
                .isInstanceOf(SwitchError.class);
    }

    @Test
    void theListCountsWhatTheCrewWorks() {
        EventLog log = aLog("CN 400100", "CN 400101");
        placed(log, "CN 400100", 5);
        released(log, "CN 400100", 8);
        SwitchList found = plan(log, customers(), "ESC", TUESDAY, NOW, spot("CN 400101", "ESCPAP1"));
        assertThat(found.worked()).isEqualTo(2);
        assertThat(found.size()).isEqualTo(2);
    }

    @Test
    void theListSeparatesWhatItCouldNotDo() {
        EventLog log = aLog("CN 400100");
        badOrdered(log, "CN 400100", 6);
        SwitchList found = plan(log, customers(), "ESC", TUESDAY, NOW, spot("CN 400100", "ESCPAP1"));
        assertThat(found.worked()).isZero();
        assertThat(found.stopped()).hasSize(1);
    }

    @Test
    void aMovePrintsWhatTheCrewDoes() {
        EventLog log = aLog("CN 400100");
        SwitchList found = plan(log, customers(), "ESC", TUESDAY, NOW, spot("CN 400100", "ESCPAP1"));
        assertThat(found.moves().get(0)).hasToString("place CN 400100 ESCPAP1");
    }

    @Test
    void aSaturdayWorksNothingAtAWeekdayTrack() {
        EventLog log = aLog("CN 400100");
        placed(log, "CN 400100", 5);
        released(log, "CN 400100", 8);
        assertThat(plan(log, customers(), "ESC", SATURDAY, NOW, List.of()).moves())
                .isEmpty();
    }

    @Test
    void everyKindSaysWhetherTheCrewTouchesTheCar() {
        assertThat(MoveKind.PULL.worked()).isTrue();
        assertThat(MoveKind.PLACE.worked()).isTrue();
        assertThat(MoveKind.CONSTRUCTIVE.worked()).isFalse();
        assertThat(MoveKind.STOPPED.worked()).isFalse();
    }
}
