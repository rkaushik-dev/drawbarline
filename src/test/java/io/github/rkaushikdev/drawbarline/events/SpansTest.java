package io.github.rkaushikdev.drawbarline.events;

import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static io.github.rkaushikdev.drawbarline.events.Spans.onLineSpans;
import static io.github.rkaushikdev.drawbarline.events.Spans.onTrackSpans;
import static io.github.rkaushikdev.drawbarline.events.Spans.scanPairs;
import static io.github.rkaushikdev.drawbarline.events.Spans.within;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.drawbarline.EventError;
import io.github.rkaushikdev.drawbarline.clock.Interval;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpansTest {

    static final CarMark CAR = CarMark.parse("DBL 104207");
    static final ReportingMark CN = new ReportingMark("CN");
    static final ReportingMark ELS = new ReportingMark("ELS");
    static final Instant CUTOFF = utc(2025, 2, 1);

    static CarStream streamOf(Event... events) {
        CarStream stream = new CarStream(CAR);
        for (Event event : events) {
            stream.record(event);
        }
        return stream;
    }

    static Event received(int day) {
        return received(day, CN);
    }

    static Event received(int day, ReportingMark road) {
        return Event.builder(EventKind.RECEIVED, CAR, utc(2025, 1, day, 14), "ESC")
                .road(road)
                .build();
    }

    static Event delivered(int day) {
        return delivered(day, CN);
    }

    static Event delivered(int day, ReportingMark road) {
        return Event.builder(EventKind.DELIVERED, CAR, utc(2025, 1, day, 9), "MRN")
                .road(road)
                .build();
    }

    static Event placed(int day) {
        return placed(day, EventKind.PLACED);
    }

    static Event placed(int day, EventKind kind) {
        return Event.builder(kind, CAR, utc(2025, 1, day, 8), "ESC")
                .industry("ESCPAP1")
                .build();
    }

    static Event released(int day) {
        return Event.builder(EventKind.RELEASED, CAR, utc(2025, 1, day, 15), "ESC")
                .industry("ESCPAP1")
                .build();
    }

    static Event pulled(int day) {
        return Event.builder(EventKind.PULLED, CAR, utc(2025, 1, day, 17), "ESC")
                .industry("ESCPAP1")
                .build();
    }

    @Test
    void aCarThatNeverCameHasNoSpan() {
        assertThat(onLineSpans(new CarStream(CAR), CUTOFF)).isEmpty();
    }

    @Test
    void aReceiptAndADeliveryMakeOneSpan() {
        List<OnLine> spans = onLineSpans(streamOf(received(14), delivered(16)), CUTOFF);
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).interval()).isEqualTo(new Interval(utc(2025, 1, 14, 14), utc(2025, 1, 16, 9)));
        assertThat(spans.get(0).isOpen()).isFalse();
    }

    @Test
    void aClosedSpanRemembersBothRoads() {
        List<OnLine> spans = onLineSpans(streamOf(received(14, CN), delivered(16, ELS)), CUTOFF);
        assertThat(spans.get(0).receivedFrom()).contains(CN);
        assertThat(spans.get(0).deliveredTo()).contains(ELS);
    }

    @Test
    void aCarStillHereHasAnOpenSpanToTheCutOff() {
        List<OnLine> spans = onLineSpans(streamOf(received(14)), CUTOFF);
        assertThat(spans.get(0).isOpen()).isTrue();
        assertThat(spans.get(0).interval().end()).isEqualTo(CUTOFF);
    }

    @Test
    void twoVisitsMakeTwoSpans() {
        CarStream stream = streamOf(
                received(14),
                delivered(16),
                Event.builder(EventKind.RECEIVED, CAR, utc(2025, 1, 20, 14), "ESC")
                        .road(CN)
                        .build(),
                Event.builder(EventKind.DELIVERED, CAR, utc(2025, 1, 22, 9), "MRN")
                        .road(CN)
                        .build());
        assertThat(onLineSpans(stream, CUTOFF)).hasSize(2);
    }

    @Test
    void aSecondReceiptWithNoDeliveryBetweenIsRefused() {
        CarStream stream = streamOf(
                received(14),
                Event.builder(EventKind.RECEIVED, CAR, utc(2025, 1, 20, 14), "ESC")
                        .road(CN)
                        .build());
        assertThatThrownBy(() -> onLineSpans(stream, CUTOFF)).isInstanceOf(EventError.class);
    }

    @Test
    void aDeliveryWithNoReceiptIsRefused() {
        assertThatThrownBy(() -> onLineSpans(streamOf(delivered(16)), CUTOFF)).isInstanceOf(EventError.class);
    }

    @Test
    void aCutOffBeforeTheDeliveryLeavesTheSpanOpen() {
        List<OnLine> spans = onLineSpans(streamOf(received(14), delivered(16)), utc(2025, 1, 15));
        assertThat(spans.get(0).isOpen()).isTrue();
        assertThat(spans.get(0).interval().end()).isEqualTo(utc(2025, 1, 15));
    }

    @Test
    void aSpanPrintsItsEndsAndWhetherItIsOpen() {
        OnLine span = onLineSpans(streamOf(received(14)), CUTOFF).get(0);
        assertThat(span.toString()).endsWith("open");
    }

    @Test
    void theTwoHalvesOfASplitSpanAddToTheWhole() {
        CarStream stream = streamOf(received(14), delivered(30));
        OnLine whole = onLineSpans(stream, CUTOFF).get(0);
        Interval first = new Interval(utc(2025, 1, 1), utc(2025, 1, 20));
        Interval second = new Interval(utc(2025, 1, 20), utc(2025, 2, 1));
        long halves = whole.clipped(first).minutes() + whole.clipped(second).minutes();
        assertThat(halves).isEqualTo(whole.minutes());
    }

    @Test
    void aCarNeverPlacedHasNoTrackSpan() {
        assertThat(onTrackSpans(streamOf(received(14)), CUTOFF)).isEmpty();
    }

    @Test
    void aPlacementAndAPullMakeOneTrackSpan() {
        CarStream stream = streamOf(received(14), placed(15), released(16), pulled(17));
        List<OnTrack> spans = onTrackSpans(stream, CUTOFF);
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).industry()).isEqualTo("ESCPAP1");
        assertThat(spans.get(0).releasedAt()).contains(utc(2025, 1, 16, 15));
    }

    @Test
    void aCarStillOnTheTrackHasAnOpenSpan() {
        List<OnTrack> spans = onTrackSpans(streamOf(received(14), placed(15)), CUTOFF);
        assertThat(spans.get(0).isOpen()).isTrue();
        assertThat(spans.get(0).releasedAt()).isEmpty();
    }

    @Test
    void aConstructivePlacementIsMarkedAsOne() {
        CarStream stream = streamOf(received(14), placed(15, EventKind.CONSTRUCTIVE));
        assertThat(onTrackSpans(stream, CUTOFF).get(0).constructive()).isTrue();
    }

    @Test
    void anActualPlacementIsNotConstructive() {
        CarStream stream = streamOf(received(14), placed(15));
        assertThat(onTrackSpans(stream, CUTOFF).get(0).constructive()).isFalse();
    }

    @Test
    void aCarPlacedTwiceRunningIsRefused() {
        CarStream stream = streamOf(
                received(14),
                placed(15),
                Event.builder(EventKind.PLACED, CAR, utc(2025, 1, 16, 8), "ESC")
                        .industry("ESCPAP2")
                        .build());
        assertThatThrownBy(() -> onTrackSpans(stream, CUTOFF)).isInstanceOf(EventError.class);
    }

    @Test
    void aReleaseBeforeAnyPlacementIsIgnored() {
        assertThat(onTrackSpans(streamOf(received(14), released(15)), CUTOFF)).isEmpty();
    }

    @Test
    void twoPlacementsAtDifferentTracksMakeTwoSpans() {
        CarStream stream = streamOf(
                received(14),
                placed(15),
                pulled(16),
                Event.builder(EventKind.PLACED, CAR, utc(2025, 1, 17, 8), "ESC")
                        .industry("ESCPAP2")
                        .build(),
                Event.builder(EventKind.PULLED, CAR, utc(2025, 1, 19, 17), "ESC")
                        .industry("ESCPAP2")
                        .build());
        assertThat(onTrackSpans(stream, CUTOFF)).extracting(OnTrack::industry).containsExactly("ESCPAP1", "ESCPAP2");
    }

    @Test
    void onlyTheSpansTouchingAWindowComeBack() {
        CarStream stream = streamOf(received(14), delivered(16));
        List<OnLine> spans = onLineSpans(stream, CUTOFF);
        Interval february = new Interval(utc(2025, 2, 1), utc(2025, 3, 1));
        assertThat(within(spans, february)).isEmpty();
    }

    @Test
    void aSpanThatTouchesAWindowIsCutDownToIt() {
        CarStream stream = streamOf(received(14), delivered(30));
        List<OnLine> spans = onLineSpans(stream, CUTOFF);
        Interval week = new Interval(utc(2025, 1, 20), utc(2025, 1, 27));
        assertThat(within(spans, week).get(0).interval()).isEqualTo(week);
    }

    @Test
    void theWalkPairsAnOpeningWithItsClosing() {
        CarStream stream = streamOf(received(14), delivered(16));
        List<Pair> pairs = scanPairs(stream, EnumSet.of(EventKind.RECEIVED), EnumSet.of(EventKind.DELIVERED), CUTOFF);
        assertThat(pairs).hasSize(1);
        assertThat(pairs.get(0).isOpen()).isFalse();
    }

    @Test
    void theWalkLeavesTheLastPairOpenAtTheCutOff() {
        List<Pair> pairs = scanPairs(
                streamOf(received(14)), EnumSet.of(EventKind.RECEIVED), EnumSet.of(EventKind.DELIVERED), CUTOFF);
        assertThat(pairs.get(0).isOpen()).isTrue();
        assertThat(pairs.get(0).interval().end()).isEqualTo(CUTOFF);
    }

    @Test
    void theWalkGathersTheKindsItIsToldToWatch() {
        CarStream stream = streamOf(received(14), placed(15), released(16), pulled(17));
        List<Pair> pairs = scanPairs(
                stream,
                EnumSet.of(EventKind.PLACED),
                EnumSet.of(EventKind.PULLED),
                CUTOFF,
                EnumSet.of(EventKind.RELEASED));
        assertThat(pairs.get(0).firstInside(EventKind.RELEASED).orElseThrow().when())
                .isEqualTo(utc(2025, 1, 16, 15));
    }

    @Test
    void theWalkIgnoresAWatchedKindOutsideAPair() {
        CarStream stream = streamOf(received(14), released(15));
        List<Pair> pairs = scanPairs(
                stream,
                EnumSet.of(EventKind.PLACED),
                EnumSet.of(EventKind.PULLED),
                CUTOFF,
                EnumSet.of(EventKind.RELEASED));
        assertThat(pairs).isEmpty();
    }

    @Test
    void aPairWithNothingWatchedReportsNothingInside() {
        CarStream stream = streamOf(received(14), placed(15), pulled(17));
        List<Pair> pairs = scanPairs(
                stream,
                EnumSet.of(EventKind.PLACED),
                EnumSet.of(EventKind.PULLED),
                CUTOFF,
                EnumSet.of(EventKind.RELEASED));
        assertThat(pairs.get(0).firstInside(EventKind.RELEASED)).isEmpty();
    }

    @Test
    void theLabelReachesTheMessageTheWalkRaises() {
        CarStream stream = streamOf(received(14), delivered(16));
        EventError caught = catchThrowableOfType(
                EventError.class,
                () -> scanPairs(
                        stream,
                        EnumSet.of(EventKind.DELIVERED),
                        EnumSet.of(EventKind.RECEIVED),
                        CUTOFF,
                        "the delivery"));
        assertThat(caught.message()).contains("the delivery");
    }
}
