package io.github.rkaushikdev.drawbarline.clock;

import static io.github.rkaushikdev.drawbarline.clock.Clock.toLocal;
import static io.github.rkaushikdev.drawbarline.clock.Clock.toUtc;
import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import io.github.rkaushikdev.drawbarline.network.Network;
import io.github.rkaushikdev.drawbarline.network.Station;
import io.github.rkaushikdev.drawbarline.reference.Reference;
import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClockTest {

    static final Network LINE = Reference.drawbarLine();
    static final Station ESC = LINE.station("ESC");
    static final Station MRN = LINE.station("MRN");

    @Test
    void aReadingAtEscanabaIsEastern() {
        assertThat(toUtc(ESC, LocalDateTime.of(2025, 1, 14, 9, 0))).isEqualTo(utc(2025, 1, 14, 14, 0));
    }

    @Test
    void theSameReadingAtMarinetteIsAnHourLater() {
        assertThat(toUtc(MRN, LocalDateTime.of(2025, 1, 14, 9, 0))).isEqualTo(utc(2025, 1, 14, 15, 0));
    }

    @Test
    void aCarAcrossTheSeamAtTheSameClockReadingRanAnHour() {
        Instant received = toUtc(ESC, LocalDateTime.of(2025, 1, 14, 9, 0));
        Instant delivered = toUtc(MRN, LocalDateTime.of(2025, 1, 14, 9, 0));
        assertThat(new Interval(received, delivered).minutes()).isEqualTo(60);
    }

    @Test
    void summerReadingsMoveWithTheClocks() {
        assertThat(toUtc(ESC, LocalDateTime.of(2025, 7, 14, 9, 0))).isEqualTo(utc(2025, 7, 14, 13, 0));
    }

    @Test
    void aReadingThatCarriesAZoneIsRefused() throws NoSuchMethodException {
        // A station reading is a wall clock with no zone attached: the reader takes only a local
        // date and time, and a zoned value read from outside is refused where it arrives.
        assertThat(Clock.class
                        .getMethod("toUtc", Station.class, LocalDateTime.class, boolean.class)
                        .getParameterTypes())
                .containsExactly(Station.class, LocalDateTime.class, boolean.class);
        assertThatThrownBy(() -> Clock.requireAware(LocalDateTime.of(2025, 1, 14, 9, 0)))
                .isInstanceOf(DrawbarlineError.class);
    }

    @Test
    void aReadingThatNeverHappenedIsRefused() {
        DrawbarlineError caught =
                catchThrowableOfType(DrawbarlineError.class, () -> toUtc(MRN, LocalDateTime.of(2025, 3, 9, 2, 30)));
        assertThat(caught.message()).contains("clocks went forward");
    }

    @Test
    void anHourThatHappenedTwiceNeedsSayingWhich() {
        Instant first = toUtc(MRN, LocalDateTime.of(2025, 11, 2, 1, 30));
        Instant second = toUtc(MRN, LocalDateTime.of(2025, 11, 2, 1, 30), true);
        assertThat(Duration.between(first, second)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void askingForASecondPassOnAnOrdinaryDayIsRefused() {
        assertThatThrownBy(() -> toUtc(MRN, LocalDateTime.of(2025, 1, 14, 9, 0), true))
                .isInstanceOf(DrawbarlineError.class);
    }

    @Test
    void aMomentReadsBackAsTheLocalClock() {
        assertThat(toLocal(ESC, utc(2025, 1, 14, 14, 0)).getHour()).isEqualTo(9);
        assertThat(toLocal(MRN, utc(2025, 1, 14, 14, 0)).getHour()).isEqualTo(8);
    }

    @Test
    void anIntervalCountsWholeMinutes() {
        assertThat(new Interval(utc(2025, 1, 1, 0, 0), utc(2025, 1, 1, 3, 30)).minutes())
                .isEqualTo(210);
    }

    @Test
    void anIntervalReportsHoursToTwoPlaces() {
        assertThat(new Interval(utc(2025, 1, 1, 0, 0), utc(2025, 1, 1, 3, 30)).hundredthHours())
                .isEqualTo(350);
    }

    @Test
    void anOddNumberOfMinutesRoundsToTheNearestHundredth() {
        assertThat(new Interval(utc(2025, 1, 1, 0, 0), utc(2025, 1, 1, 0, 10)).hundredthHours())
                .isEqualTo(17);
    }

    @Test
    void anIntervalOfNoTimeIsEmpty() {
        assertThat(new Interval(utc(2025, 1, 1), utc(2025, 1, 1)).isEmpty()).isTrue();
    }

    @Test
    void anIntervalThatEndsBeforeItStartsIsRefused() {
        assertThatThrownBy(() -> new Interval(utc(2025, 1, 2), utc(2025, 1, 1))).isInstanceOf(DrawbarlineError.class);
    }

    @Test
    void anIntervalOfNaiveMomentsIsRefused() {
        // Both ends are instants, so a wall clock reading cannot be passed as one; a reading that
        // arrives from outside without its zone is refused before it gets that far.
        assertThat(Interval.class.getRecordComponents())
                .extracting(RecordComponent::getType)
                .containsExactly(Instant.class, Instant.class);
        assertThatThrownBy(() -> Clock.requireAware(LocalDateTime.of(2025, 1, 1, 0, 0), "start"))
                .isInstanceOf(DrawbarlineError.class)
                .hasMessageContaining("what='start'");
    }

    @Test
    void anIntervalIsClosedAtTheStartAndOpenAtTheEnd() {
        Interval span = new Interval(utc(2025, 1, 1), utc(2025, 1, 2));
        assertThat(span.contains(utc(2025, 1, 1))).isTrue();
        assertThat(span.contains(utc(2025, 1, 2))).isFalse();
    }

    @Test
    void intervalsThatTouchDoNotOverlap() {
        Interval first = new Interval(utc(2025, 1, 1), utc(2025, 1, 2));
        Interval second = new Interval(utc(2025, 1, 2), utc(2025, 1, 3));
        assertThat(first.overlaps(second)).isFalse();
    }

    @Test
    void intervalsThatShareTimeOverlap() {
        Interval first = new Interval(utc(2025, 1, 1), utc(2025, 1, 3));
        Interval second = new Interval(utc(2025, 1, 2), utc(2025, 1, 4));
        assertThat(first.overlaps(second)).isTrue();
    }

    @Test
    void clippingKeepsOnlyThePartInsideTheWindow() {
        Interval span = new Interval(utc(2025, 1, 1), utc(2025, 1, 10));
        Interval window = new Interval(utc(2025, 1, 3), utc(2025, 1, 5));
        assertThat(span.clip(window)).isEqualTo(window);
    }

    @Test
    void clippingSomethingWhollyOutsideGivesNothing() {
        Interval span = new Interval(utc(2025, 1, 1), utc(2025, 1, 2));
        Interval window = new Interval(utc(2025, 2, 1), utc(2025, 2, 5));
        assertThat(span.clip(window).isEmpty()).isTrue();
    }

    @Test
    void anIntervalShiftsWhole() {
        Interval span = new Interval(utc(2025, 1, 1), utc(2025, 1, 2));
        assertThat(span.shift(Duration.ofDays(1))).isEqualTo(new Interval(utc(2025, 1, 2), utc(2025, 1, 3)));
    }

    @Test
    void anIntervalPrintsBothEndsInUtc() {
        Interval span = new Interval(utc(2025, 1, 1, 6, 0), utc(2025, 1, 2, 7, 30));
        assertThat(span).hasToString("2025-01-01 06:00Z to 2025-01-02 07:30Z");
    }

    static Interval hours(int from, int to) {
        return new Interval(
                utc(2025, 1, 1, 0).plus(Duration.ofHours(from)),
                utc(2025, 1, 1, 0).plus(Duration.ofHours(to)));
    }

    @Test
    void takingNothingOutLeavesTheWholeInterval() {
        assertThat(hours(0, 10).without(List.of())).containsExactly(hours(0, 10));
        assertThat(hours(0, 10).without(List.of(hours(12, 14)))).containsExactly(hours(0, 10));
        assertThat(hours(0, 10).without(List.of(hours(10, 14)))).containsExactly(hours(0, 10));
    }

    @Test
    void takingOutTheMiddleLeavesBothEnds() {
        assertThat(hours(0, 10).without(List.of(hours(3, 5)))).containsExactly(hours(0, 3), hours(5, 10));
    }

    @Test
    void takingOutAnEdgeLeavesTheOtherSide() {
        assertThat(hours(0, 10).without(List.of(hours(-2, 4)))).containsExactly(hours(4, 10));
        assertThat(hours(0, 10).without(List.of(hours(6, 12)))).containsExactly(hours(0, 6));
    }

    @Test
    void overlappingCutsInAnyOrderAreTakenTogether() {
        assertThat(hours(0, 10).without(List.of(hours(6, 8), hours(2, 4), hours(3, 7))))
                .containsExactly(hours(0, 2), hours(8, 10));
    }

    @Test
    void coveringTheWholeIntervalLeavesNothing() {
        assertThat(hours(0, 10).without(List.of(hours(0, 10)))).isEmpty();
        assertThat(hours(2, 2).without(List.of())).isEmpty();
    }
}
