package io.github.rkaushikdev.drawbarline.interchange;

import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static io.github.rkaushikdev.drawbarline.interchange.Reconcile.compare;
import static io.github.rkaushikdev.drawbarline.interchange.Reconcile.keyOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.events.EventKind;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReconcileTest {

    static final ReportingMark CN = new ReportingMark("CN");

    static Event received() {
        return received(400100, 12, 0);
    }

    static Event received(int number) {
        return received(number, 12, 0);
    }

    static Event received(int number, int hour) {
        return received(number, hour, 0);
    }

    static Event receivedAt(int hour) {
        return received(400100, hour, 0);
    }

    static Event received(int number, int hour, int minute) {
        return Event.builder(EventKind.RECEIVED, CarMark.parse("CN " + number), utc(2025, 1, 6, hour, minute), "ESC")
                .road(CN)
                .build();
    }

    static Event delivered() {
        return Event.builder(EventKind.DELIVERED, CarMark.parse("CN 400100"), utc(2025, 1, 16, 9), "MRN")
                .road(CN)
                .build();
    }

    static List<String> cars(List<Event> events) {
        return events.stream().map(one -> one.car().toString()).toList();
    }

    @Test
    void theKeyLeavesTheClockOut() {
        assertThat(keyOf(receivedAt(12))).isEqualTo(keyOf(receivedAt(13)));
    }

    @Test
    void theKeySeparatesTwoCars() {
        assertThat(keyOf(received(400100))).isNotEqualTo(keyOf(received(400101)));
    }

    @Test
    void theKeySeparatesTwoKinds() {
        assertThat(keyOf(received())).isNotEqualTo(keyOf(delivered()));
    }

    @Test
    void theKeySeparatesTwoDays() {
        Event later = Event.builder(EventKind.RECEIVED, CarMark.parse("CN 400100"), utc(2025, 1, 7, 12), "ESC")
                .road(CN)
                .build();
        assertThat(keyOf(received())).isNotEqualTo(keyOf(later));
    }

    @Test
    void twoMatchingRecordsAgree() {
        Reconciliation found = compare(List.of(received()), List.of(received()));
        assertThat(found.isClean()).isTrue();
        assertThat(found.agreed()).hasSize(1);
    }

    @Test
    void aRecordOnlyWeHaveShowsUp() {
        Reconciliation found = compare(List.of(received(), received(400101)), List.of(received()));
        assertThat(cars(found.onlyOurs())).containsExactly("CN 400101");
    }

    @Test
    void aRecordOnlyTheyHaveShowsUp() {
        Reconciliation found = compare(List.of(received()), List.of(received(), received(400101)));
        assertThat(cars(found.onlyTheirs())).containsExactly("CN 400101");
    }

    @Test
    void aSmallDifferenceInTimeStillAgrees() {
        Reconciliation found = compare(List.of(receivedAt(12)), List.of(received(400100, 12, 20)));
        assertThat(found.isClean()).isTrue();
    }

    @Test
    void aDifferencePastTheToleranceIsReported() {
        Reconciliation found = compare(List.of(receivedAt(12)), List.of(receivedAt(14)));
        assertThat(found.timings()).hasSize(1);
        assertThat(found.timings().get(0).minutesApart()).isEqualTo(120);
    }

    @Test
    void aTimingSaysWhichWayRoundItIs() {
        Reconciliation found = compare(List.of(receivedAt(14)), List.of(receivedAt(12)));
        assertThat(found.timings().get(0).weAreLater()).isTrue();
        assertThat(found.timings().get(0).toString()).contains("later");
    }

    @Test
    void aTimingTheOtherWaySaysEarlier() {
        Reconciliation found = compare(List.of(receivedAt(12)), List.of(receivedAt(14)));
        assertThat(found.timings().get(0).weAreLater()).isFalse();
        assertThat(found.timings().get(0).toString()).contains("earlier");
    }

    @Test
    void theToleranceCanBeTightened() {
        Reconciliation found =
                compare(List.of(receivedAt(12)), List.of(received(400100, 12, 20)), Duration.ofMinutes(5));
        assertThat(found.timings()).hasSize(1);
    }

    @Test
    void aNegativeToleranceIsRefused() {
        assertThatThrownBy(() -> compare(List.of(), List.of(), Duration.ofMinutes(-1)))
                .isInstanceOf(ReconcileError.class)
                .hasMessageContaining("given='-1 day, 23:59:00'");
    }

    @Test
    void theWorstTimingComesBack() {
        Reconciliation found = compare(
                List.of(received(400100, 12), received(400101, 12)),
                List.of(received(400100, 13), received(400101, 18)));
        assertThat(found.worstTiming().orElseThrow().ours().car()).hasToString("CN 400101");
    }

    @Test
    void nothingOutOfTimeHasNoWorst() {
        assertThat(compare(List.of(received()), List.of(received())).worstTiming())
                .isEmpty();
    }

    @Test
    void twoRecordsOfTheSameHandoffPairUpOneForOne() {
        List<Event> ours = List.of(receivedAt(12), receivedAt(13));
        List<Event> theirs = List.of(receivedAt(12), receivedAt(13));
        Reconciliation found = compare(ours, theirs);
        assertThat(found.agreed()).hasSize(2);
        assertThat(found.onlyOurs()).isEmpty();
    }

    @Test
    void aDuplicateOnOneSideShowsAsOnlyTheirs() {
        Reconciliation found = compare(List.of(received()), List.of(received(), received()));
        assertThat(found.agreed()).hasSize(1);
        assertThat(found.onlyTheirs()).hasSize(1);
    }

    @Test
    void aCleanComparisonPrintsAsAgreeing() {
        assertThat(compare(List.of(received()), List.of(received()))).hasToString("1 records, both sides agree");
    }

    @Test
    void aDirtyComparisonPrintsTheCounts() {
        Reconciliation found = compare(List.of(received(), received(400101)), List.of(received(), received(400102)));
        String printed = found.toString();
        assertThat(printed).contains("1 only ours");
        assertThat(printed).contains("1 only theirs");
    }

    @Test
    void theDifferenceCountAddsTheThreeKinds() {
        Reconciliation found = compare(
                List.of(received(), received(400101), received(400103, 12)),
                List.of(received(), received(400102), received(400103, 20)));
        assertThat(found.differences()).isEqualTo(3);
    }

    @Test
    void twoEmptyRecordsAgreeOnNothing() {
        Reconciliation found = compare(List.of(), List.of());
        assertThat(found.isClean()).isTrue();
        assertThat(found.differences()).isZero();
    }

    @Test
    void recordsOnlyWeHaveComeBackInOrder() {
        Reconciliation found = compare(List.of(received(400103), received(400101)), List.of());
        assertThat(cars(found.onlyOurs())).containsExactly("CN 400101", "CN 400103");
    }
}
