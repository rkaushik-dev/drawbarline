package io.github.rkaushikdev.drawbarline.demurrage;

import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.TariffError;
import io.github.rkaushikdev.drawbarline.clock.Interval;
import io.github.rkaushikdev.drawbarline.days.TariffCalendar;
import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.events.EventKind;
import io.github.rkaushikdev.drawbarline.events.EventLog;
import io.github.rkaushikdev.drawbarline.events.OnTrack;
import io.github.rkaushikdev.drawbarline.events.Spans;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.network.Network;
import io.github.rkaushikdev.drawbarline.network.Station;
import io.github.rkaushikdev.drawbarline.reference.Reference;
import io.github.rkaushikdev.drawbarline.support.Moves;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Counting a placement out into free days and chargeable ones, on the station's own calendar. */
class DetentionTest {

    static final Network LINE = Reference.drawbarLine();
    static final Station ESC = LINE.station("ESC");
    static final Station MRN = LINE.station("MRN");
    static final String CAR = "CN 400100";
    static final Tariff TARIFF =
            new Tariff("4200", LocalDate.of(2024, 7, 1), 2, 1, List.of(new Step(2, 10000), new Step(0, 20000)));
    static final Tariff NO_FREE_TIME = new Tariff("4300", LocalDate.of(2024, 7, 1), 0, 0, List.of(new Step(0, 10000)));

    /** A placement span straight from its ends, without going through a log. */
    static OnTrack span(
            Station station, Instant placed, Instant ends, Optional<Instant> released, boolean constructive) {
        CarMark car = CarMark.parse(CAR);
        EventKind kind = constructive ? EventKind.CONSTRUCTIVE : EventKind.PLACED;
        Event opened = Event.builder(kind, car, placed, station.code())
                .industry("ESCPAP1")
                .build();
        return new OnTrack(
                car, new Interval(placed, ends), opened, Optional.empty(), "ESCPAP1", constructive, released);
    }

    /** The one placement span a log holds for the car, cut off at the end of January. */
    static OnTrack onlySpan(EventLog log) {
        List<OnTrack> spans = Spans.onTrackSpans(log.stream(CarMark.parse(CAR)), utc(2025, 2, 1));
        assertThat(spans).hasSize(1);
        return spans.get(0);
    }

    /** Placed at Escanaba on the fourteenth, released on {@code releasedOn}, pulled the day after. */
    static OnTrack heldUntil(int releasedOn) {
        EventLog log = new EventLog();
        Moves.placed(log, CAR, 14);
        Moves.released(log, CAR, releasedOn);
        Moves.pulled(log, CAR, releasedOn + 1);
        return onlySpan(log);
    }

    @Test
    void aPlacementIsDatedOnTheStationsOwnCalendar() {
        // Half past three in the morning UTC on the fourteenth is the evening of the thirteenth at
        // Escanaba.
        OnTrack late = span(ESC, utc(2025, 1, 14, 3, 30), utc(2025, 1, 20), Optional.empty(), false);
        assertThat(Counting.count(late, TARIFF, ESC, true).placedOn()).isEqualTo(LocalDate.of(2025, 1, 13));
    }

    @Test
    void theSameMomentFallsOnDifferentDaysEitherSideOfTheZoneSeam() {
        // Half past five UTC is half past midnight at Escanaba and half past eleven the night
        // before at Marinette.
        Instant placed = utc(2025, 1, 14, 5, 30);
        OnTrack span = span(ESC, placed, utc(2025, 1, 20), Optional.empty(), false);
        assertThat(Counting.count(span, TARIFF, ESC, true).placedOn()).isEqualTo(LocalDate.of(2025, 1, 14));
        assertThat(Counting.count(span, TARIFF, MRN, true).placedOn()).isEqualTo(LocalDate.of(2025, 1, 13));
    }

    @Test
    void theClockStartsTheFirstChargeableDayAfterPlacement() {
        assertThat(Counting.clockStartsOn(LocalDate.of(2025, 1, 14))).isEqualTo(LocalDate.of(2025, 1, 15));
        assertThat(Counting.clockStartsOn(LocalDate.of(2025, 1, 17))).isEqualTo(LocalDate.of(2025, 1, 20));
    }

    @Test
    void aLoadedCarCountsItsChargeableDaysAfterTwoFree() {
        Detention found = Counting.count(heldUntil(22), TARIFF, ESC, true);
        assertThat(found.placedOn()).isEqualTo(LocalDate.of(2025, 1, 14));
        assertThat(found.clockStarts()).isEqualTo(LocalDate.of(2025, 1, 15));
        assertThat(found.freeUntil()).contains(LocalDate.of(2025, 1, 16));
        assertThat(found.countedTo()).isEqualTo(LocalDate.of(2025, 1, 22));
        // Friday the seventeenth, then Monday to Wednesday: the weekend is not chargeable.
        assertThat(found.chargeable()).isEqualTo(4);
        assertThat(found.loaded()).isTrue();
        assertThat(found.constructive()).isFalse();
    }

    @Test
    void anEmptyGetsItsOwnFreeTime() {
        Detention found = Counting.count(heldUntil(22), TARIFF, ESC, false);
        assertThat(found.freeUntil()).contains(LocalDate.of(2025, 1, 15));
        assertThat(found.chargeable()).isEqualTo(5);
    }

    @Test
    void theReleaseStopsTheCountWhileTheCarStillStands() {
        EventLog log = new EventLog();
        Moves.placed(log, CAR, 14);
        Moves.released(log, CAR, 22);
        Moves.pulled(log, CAR, 28);
        Detention found = Counting.count(onlySpan(log), TARIFF, ESC, true);
        assertThat(found.countedTo()).isEqualTo(LocalDate.of(2025, 1, 22));
        assertThat(found.chargeable()).isEqualTo(4);
    }

    @Test
    void anUnreleasedCarIsCountedToTheEndOfItsSpan() {
        OnTrack open = span(ESC, utc(2025, 1, 14, 20), utc(2025, 1, 24, 20), Optional.empty(), false);
        Detention found = Counting.count(open, TARIFF, ESC, true);
        assertThat(found.countedTo()).isEqualTo(LocalDate.of(2025, 1, 24));
        assertThat(found.chargeable()).isEqualTo(6);
    }

    @Test
    void aCarBackInsideFreeTimeIsStillFree() {
        Detention found = Counting.count(heldUntil(16), TARIFF, ESC, true);
        assertThat(found.chargeable()).isZero();
        assertThat(found.stillFree()).isTrue();
        assertThat(found.charge(TARIFF)).isEqualTo(Money.ZERO);
        assertThat(found).hasToString("CN 400100 at ESCPAP1 inside free time");
    }

    @Test
    void aTariffWithNoFreeTimeChargesFromTheClockStart() {
        Detention found = Counting.count(heldUntil(17), NO_FREE_TIME, ESC, true);
        assertThat(found.freeUntil()).isEmpty();
        assertThat(found.chargeable()).isEqualTo(3);
    }

    @Test
    void negativeFreeTimeIsRefused() {
        assertThatThrownBy(() -> Counting.freeTimeEnds(LocalDate.of(2025, 1, 15), -1))
                .isInstanceOf(TariffError.class)
                .hasMessageContaining("free_days=-1");
    }

    @Test
    void aHolidayIsNeitherFreeTimeNorACharge() {
        // Placed Monday the twenty second of December: free Tuesday and Wednesday, Christmas
        // skipped, charged the Friday and the Monday.
        OnTrack span =
                span(ESC, utc(2025, 12, 22, 20), utc(2025, 12, 30, 23), Optional.of(utc(2025, 12, 29, 21)), false);
        Detention found = Counting.count(span, TARIFF, ESC, true);
        assertThat(found.freeUntil()).contains(LocalDate.of(2025, 12, 24));
        assertThat(found.chargeable()).isEqualTo(2);
    }

    @Test
    void aConstructivePlacementIsCarriedThrough() {
        OnTrack span = span(ESC, utc(2025, 1, 14, 20), utc(2025, 1, 20), Optional.empty(), true);
        assertThat(Counting.count(span, TARIFF, ESC, true).constructive()).isTrue();
    }

    @Test
    void theChargeClimbsTheTariffSteps() {
        Detention found = Counting.count(heldUntil(22), TARIFF, ESC, true);
        assertThat(found.charge(TARIFF)).isEqualTo(new Money(10000 + 10000 + 20000 + 20000));
        assertThat(found).hasToString("CN 400100 at ESCPAP1 4 chargeable days");
    }

    @Test
    void aDayTheRoadDeclaredClosedIsNeitherFreeNorCharged() {
        TariffCalendar storm = TariffCalendar.STANDARD.declaring(LocalDate.of(2025, 1, 20), "blizzard");
        Detention found = Counting.count(heldUntil(22), TARIFF, ESC, true, storm);
        assertThat(found.freeUntil()).contains(LocalDate.of(2025, 1, 16));
        assertThat(found.chargeable()).isEqualTo(3);
        assertThat(Counting.count(heldUntil(22), TARIFF, ESC, true).chargeable())
                .isEqualTo(4);
    }

    @Test
    void aDeclaredDayInsideFreeTimePushesTheFreeTimeOn() {
        TariffCalendar storm = TariffCalendar.STANDARD.declaring(LocalDate.of(2025, 1, 15), "blizzard");
        Detention found = Counting.count(heldUntil(22), TARIFF, ESC, true, storm);
        assertThat(found.clockStarts()).isEqualTo(LocalDate.of(2025, 1, 16));
        assertThat(found.freeUntil()).contains(LocalDate.of(2025, 1, 17));
        assertThat(found.chargeable()).isEqualTo(3);
        assertThat(Counting.clockStartsOn(LocalDate.of(2025, 1, 14), storm)).isEqualTo(LocalDate.of(2025, 1, 16));
        assertThat(Counting.freeTimeEnds(LocalDate.of(2025, 1, 14), 0, storm)).isEmpty();
    }
}
