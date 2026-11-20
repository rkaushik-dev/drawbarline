package io.github.rkaushikdev.drawbarline.events;

import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static io.github.rkaushikdev.drawbarline.events.Event.WITHIN_A_MINUTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.EventError;
import io.github.rkaushikdev.drawbarline.clock.Clock;
import io.github.rkaushikdev.drawbarline.clock.ClockError;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.mileage.MileageError;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventRecordTest {

    static final CarMark CAR = CarMark.parse("DBL 104207");
    static final ReportingMark CN = new ReportingMark("CN");
    static final Comparator<Event> BY_ORDER = Comparator.comparing(Event::order);

    /** The event every test starts from: a receipt from CN at Escanaba. */
    static Event.Builder anEvent() {
        return Event.builder(EventKind.RECEIVED, CAR, utc(2025, 1, 14, 14, 30), "ESC")
                .road(CN);
    }

    static Event.Builder anEvent(EventKind kind) {
        return anEvent(kind, utc(2025, 1, 14, 14, 30));
    }

    static Event.Builder anEvent(EventKind kind, Instant when) {
        return Event.builder(kind, CAR, when, "ESC").road(CN);
    }

    @Test
    void anInterchangeEventPrintsTheRoad() {
        assertThat(anEvent().build()).hasToString("2025-01-14 14:30Z DBL 104207 received at ESC CN");
    }

    @Test
    void aPlacementPrintsTheTrackInsteadOfTheStation() {
        Event event = anEvent(EventKind.PLACED).road(null).industry("ESCPAP1").build();
        assertThat(event.toString()).endsWith("placed at ESCPAP1");
    }

    @Test
    void aNaiveMomentIsRefused() {
        // The moment of an event is an instant, so a wall clock reading cannot stand in for it; one
        // read from outside without its zone is refused where it arrives.
        assertThat(Event.class.getRecordComponents())
                .filteredOn(component -> component.getName().equals("when"))
                .extracting(RecordComponent::getType)
                .containsExactly(Instant.class);
        assertThatThrownBy(() -> Clock.requireAware(LocalDateTime.of(2025, 1, 14, 14, 30), "when"))
                .isInstanceOf(ClockError.class);
    }

    @Test
    void aStationWeDoNotServeIsRefused() {
        assertThatThrownBy(() -> Event.builder(EventKind.RECEIVED, CAR, utc(2025, 1, 14, 14, 30), "CHI")
                        .road(CN)
                        .build())
                .isInstanceOf(MileageError.class);
    }

    @Test
    void anInterchangeWithoutARoadIsRefused() {
        assertThatThrownBy(() -> anEvent().road(null).build()).isInstanceOf(EventError.class);
    }

    @Test
    void aRoadOnAnEventThatHasNoRoadIsRefused() {
        assertThatThrownBy(() -> anEvent(EventKind.WEIGHED).build()).isInstanceOf(EventError.class);
    }

    @Test
    void aPlacementWithoutATrackIsRefused() {
        assertThatThrownBy(() -> anEvent(EventKind.PLACED).road(null).build()).isInstanceOf(EventError.class);
    }

    @Test
    void aTrackOnAnEventThatHasNoTrackIsRefused() {
        assertThatThrownBy(() -> anEvent().industry("ESCPAP1").build()).isInstanceOf(EventError.class);
    }

    @Test
    void aMovementCarriesItsDistance() {
        Event event = anEvent(EventKind.MOVED).road(null).tenths(336).build();
        assertThat(event.tenths()).contains(336);
    }

    @Test
    void aMovementWithoutADistanceIsRefused() {
        assertThatThrownBy(() -> anEvent(EventKind.MOVED).road(null).build()).isInstanceOf(EventError.class);
    }

    @Test
    void aMovementThatCoversNoGroundIsRefused() {
        assertThatThrownBy(() -> anEvent(EventKind.MOVED).road(null).tenths(0).build())
                .isInstanceOf(EventError.class);
    }

    @Test
    void aDistanceOnSomethingThatIsNotAMovementIsRefused() {
        assertThatThrownBy(() -> anEvent().tenths(336).build()).isInstanceOf(EventError.class);
    }

    @Test
    void aWaybillNumberBelowOneIsRefused() {
        assertThatThrownBy(() -> anEvent().waybill(0).build()).isInstanceOf(EventError.class);
    }

    @Test
    void aWaybillNumberRidesAlong() {
        assertThat(anEvent().waybill(41207).build().waybill()).contains(41207);
    }

    @Test
    void aNoteLongerThanTheFieldIsRefused() {
        assertThatThrownBy(() -> anEvent().note("x".repeat(121)).build()).isInstanceOf(EventError.class);
    }

    @Test
    void aNoteAtTheLimitIsAllowed() {
        assertThat(anEvent().note("x".repeat(120)).build().note()).hasSize(120);
    }

    @Test
    void aNoteCanBeAddedToACopy() {
        Event noted = anEvent().build().withNote("keyed from the wrong sheet");
        assertThat(noted.note()).isEqualTo("keyed from the wrong sheet");
        assertThat(noted.when()).isEqualTo(anEvent().build().when());
    }

    @Test
    void addingANoteLeavesTheOriginalAlone() {
        Event original = anEvent().build();
        original.withNote("something");
        assertThat(original.note()).isEmpty();
    }

    @Test
    void theSortKeyLeadsWithTheMoment() {
        assertThat(anEvent().build().order().when()).isEqualTo(utc(2025, 1, 14, 14, 30));
    }

    @Test
    void everyKindHasAPlaceInTheMinute() {
        assertThat(WITHIN_A_MINUTE.keySet()).isEqualTo(EnumSet.allOf(EventKind.class));
    }

    @Test
    void theRanksWithinAMinuteAreAllDifferent() {
        assertThat(new HashSet<>(WITHIN_A_MINUTE.values())).hasSize(EventKind.values().length);
    }

    @Test
    void aReleaseSortsBeforeAPullInTheSameMinute() {
        Event released = anEvent(EventKind.RELEASED, utc(2025, 1, 14, 8, 0))
                .road(null)
                .industry("ESCPAP1")
                .build();
        Event pulled = anEvent(EventKind.PULLED, utc(2025, 1, 14, 8, 0))
                .road(null)
                .industry("ESCPAP1")
                .build();
        assertThat(List.of(pulled, released).stream().sorted(BY_ORDER).toList()).containsExactly(released, pulled);
    }

    @Test
    void aReceiptSortsBeforeADeliveryInTheSameMinute() {
        Event received = anEvent(EventKind.RECEIVED, utc(2025, 1, 14, 8, 0)).build();
        Event delivered = anEvent(EventKind.DELIVERED, utc(2025, 1, 14, 8, 0)).build();
        assertThat(List.of(delivered, received).stream().sorted(BY_ORDER).toList())
                .containsExactly(received, delivered);
    }

    @Test
    void twoCarsAtTheSameMomentSortByMark() {
        Event first = Event.builder(EventKind.RECEIVED, CarMark.parse("CN 1"), utc(2025, 1, 14, 8, 0), "ESC")
                .road(CN)
                .build();
        Event second = Event.builder(EventKind.RECEIVED, CarMark.parse("DBL 1"), utc(2025, 1, 14, 8, 0), "ESC")
                .road(CN)
                .build();
        assertThat(List.of(second, first).stream().sorted(BY_ORDER).toList()).containsExactly(first, second);
    }

    @Test
    void twoRecordsOfTheSameEventCompareEqual() {
        assertThat(anEvent().build()).isEqualTo(anEvent().build());
    }
}
