package io.github.rkaushikdev.drawbarline.events;

import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.EventError;
import io.github.rkaushikdev.drawbarline.clock.Interval;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventStreamTest {

    static final CarMark CAR = CarMark.parse("DBL 104207");
    static final CarMark OTHER = CarMark.parse("CN 400100");
    static final ReportingMark CN = new ReportingMark("CN");

    static Event received(Instant when) {
        return received(when, CAR);
    }

    static Event received(Instant when, CarMark car) {
        return Event.builder(EventKind.RECEIVED, car, when, "ESC").road(CN).build();
    }

    static Event delivered(Instant when) {
        return Event.builder(EventKind.DELIVERED, CAR, when, "MRN").road(CN).build();
    }

    static Event placed(Instant when) {
        return Event.builder(EventKind.PLACED, CAR, when, "ESC")
                .industry("ESCPAP1")
                .build();
    }

    @Test
    void anEmptyStreamHoldsNothing() {
        CarStream stream = new CarStream(CAR);
        assertThat(stream.size()).isZero();
        assertThat(stream.first()).isEmpty();
        assertThat(stream.last()).isEmpty();
    }

    @Test
    void anEventGoesOnAndComesBack() {
        CarStream stream = new CarStream(CAR);
        Event event = stream.record(received(utc(2025, 1, 14, 14, 0)));
        assertThat(stream).containsExactly(event);
    }

    @Test
    void eventsComeBackInTheOrderTheyHappened() {
        CarStream stream = new CarStream(CAR);
        stream.record(delivered(utc(2025, 1, 16, 9, 0)));
        stream.record(received(utc(2025, 1, 14, 14, 0)));
        stream.record(placed(utc(2025, 1, 15, 8, 0)));
        assertThat(stream)
                .extracting(Event::kind)
                .containsExactly(EventKind.RECEIVED, EventKind.PLACED, EventKind.DELIVERED);
    }

    @Test
    void aLateArrivalLandsInItsOwnPlace() {
        CarStream stream = new CarStream(CAR);
        stream.record(received(utc(2025, 1, 14, 14, 0)));
        stream.record(delivered(utc(2025, 1, 16, 9, 0)));
        stream.record(placed(utc(2025, 1, 15, 8, 0)));
        assertThat(stream.first().orElseThrow().kind()).isSameAs(EventKind.RECEIVED);
        assertThat(stream.last().orElseThrow().kind()).isSameAs(EventKind.DELIVERED);
    }

    @Test
    void anEventForAnotherCarIsRefused() {
        CarStream stream = new CarStream(CAR);
        assertThatThrownBy(() -> stream.record(received(utc(2025, 1, 14, 14, 0), OTHER)))
                .isInstanceOf(EventError.class);
    }

    @Test
    void theSameEventTwiceIsRefused() {
        CarStream stream = new CarStream(CAR);
        Event event = received(utc(2025, 1, 14, 14, 0));
        stream.record(event);
        assertThatThrownBy(() -> stream.record(event)).isInstanceOf(EventError.class);
    }

    @Test
    void aSecondCopyOfASheetIsRefusedEvenWhenRebuilt() {
        CarStream stream = new CarStream(CAR);
        stream.record(received(utc(2025, 1, 14, 14, 0)));
        assertThatThrownBy(() -> stream.record(received(utc(2025, 1, 14, 14, 0))))
                .isInstanceOf(EventError.class);
    }

    @Test
    void theSameKindAtADifferentMinuteIsADifferentEvent() {
        CarStream stream = new CarStream(CAR);
        stream.record(received(utc(2025, 1, 14, 14, 0)));
        stream.record(received(utc(2025, 1, 20, 14, 0)));
        assertThat(stream.size()).isEqualTo(2);
    }

    @Test
    void eventsComeBackByKind() {
        CarStream stream = new CarStream(CAR);
        stream.record(received(utc(2025, 1, 14, 14, 0)));
        stream.record(received(utc(2025, 2, 1, 14, 0)));
        stream.record(delivered(utc(2025, 1, 16, 9, 0)));
        assertThat(stream.ofKind(EventKind.RECEIVED)).hasSize(2);
    }

    @Test
    void theLastOfAKindIsTheLatestOne() {
        CarStream stream = new CarStream(CAR);
        stream.record(received(utc(2025, 1, 14, 14, 0)));
        stream.record(received(utc(2025, 2, 1, 14, 0)));
        assertThat(stream.lastOf(EventKind.RECEIVED).orElseThrow().when()).isEqualTo(utc(2025, 2, 1, 14, 0));
    }

    @Test
    void theFirstOfAKindIsTheEarliestOne() {
        CarStream stream = new CarStream(CAR);
        stream.record(received(utc(2025, 2, 1, 14, 0)));
        stream.record(received(utc(2025, 1, 14, 14, 0)));
        assertThat(stream.firstOf(EventKind.RECEIVED).orElseThrow().when()).isEqualTo(utc(2025, 1, 14, 14, 0));
    }

    @Test
    void aKindThatNeverHappenedHasNoLast() {
        assertThat(new CarStream(CAR).lastOf(EventKind.BAD_ORDERED)).isEmpty();
    }

    @Test
    void eventsComeBackForAWindow() {
        CarStream stream = new CarStream(CAR);
        stream.record(received(utc(2025, 1, 14, 14, 0)));
        stream.record(delivered(utc(2025, 2, 16, 9, 0)));
        Interval january = new Interval(utc(2025, 1, 1), utc(2025, 2, 1));
        assertThat(stream.during(january)).extracting(Event::kind).containsExactly(EventKind.RECEIVED);
    }

    @Test
    void everythingUpToAWindowEndComesBack() {
        CarStream stream = new CarStream(CAR);
        stream.record(received(utc(2024, 12, 30, 14, 0)));
        stream.record(placed(utc(2025, 1, 15, 8, 0)));
        stream.record(delivered(utc(2025, 2, 16, 9, 0)));
        Interval january = new Interval(utc(2025, 1, 1), utc(2025, 2, 1));
        assertThat(stream.upTo(january)).hasSize(2);
    }

    @Test
    void theLogStartsEmpty() {
        assertThat(new EventLog().size()).isZero();
        assertThat(new EventLog().cars()).isEmpty();
    }

    @Test
    void theLogOpensAStreamForANewCar() {
        EventLog log = new EventLog();
        log.record(received(utc(2025, 1, 14, 14, 0)));
        assertThat(log.has(CAR)).isTrue();
        assertThat(log.stream(CAR).size()).isEqualTo(1);
    }

    @Test
    void aCarWithNothingRecordedReadsAsAnEmptyStream() {
        EventLog log = new EventLog();
        assertThat(log.stream(CAR).size()).isZero();
        assertThat(log.has(CAR)).isFalse();
    }

    @Test
    void theLogKeepsCarsApart() {
        EventLog log = new EventLog();
        log.record(received(utc(2025, 1, 14, 14, 0)));
        log.record(received(utc(2025, 1, 14, 14, 0), OTHER));
        assertThat(log.cars()).containsExactly(OTHER, CAR);
        assertThat(log.size()).isEqualTo(2);
    }

    @Test
    void aBatchRecordsInOneCall() {
        EventLog log = new EventLog();
        assertThat(log.recordAll(List.of(received(utc(2025, 1, 14, 14, 0)), placed(utc(2025, 1, 15, 8, 0)))))
                .isEqualTo(2);
        assertThat(log.size()).isEqualTo(2);
    }

    @Test
    void theLogGathersAWindowAcrossCars() {
        EventLog log = new EventLog();
        log.record(received(utc(2025, 1, 14, 14, 0)));
        log.record(received(utc(2025, 1, 14, 12, 0), OTHER));
        Interval january = new Interval(utc(2025, 1, 1), utc(2025, 2, 1));
        assertThat(log.during(january)).extracting(Event::car).containsExactly(OTHER, CAR);
    }

    @Test
    void theLogGathersByStation() {
        EventLog log = new EventLog();
        log.record(received(utc(2025, 1, 14, 14, 0)));
        log.record(delivered(utc(2025, 1, 16, 9, 0)));
        assertThat(log.atStation("ESC")).extracting(Event::station).containsExactly("ESC");
    }

    @Test
    void theLogGathersByKind() {
        EventLog log = new EventLog();
        log.record(received(utc(2025, 1, 14, 14, 0)));
        log.record(received(utc(2025, 1, 14, 12, 0), OTHER));
        assertThat(log.ofKind(EventKind.RECEIVED)).hasSize(2);
    }

    @Test
    void iteratingAStreamDoesNotExposeTheListItKeeps() {
        CarStream stream = new CarStream(CAR);
        stream.record(received(utc(2025, 1, 14, 14, 0)));
        List<Event> taken = new ArrayList<>();
        stream.forEach(taken::add);
        taken.clear();
        assertThat(stream.size()).isEqualTo(1);
        assertThat(stream.iterator()).toIterable().hasSize(1);
    }
}
