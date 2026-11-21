package io.github.rkaushikdev.drawbarline.reports;

import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static io.github.rkaushikdev.drawbarline.support.Moves.badOrdered;
import static io.github.rkaushikdev.drawbarline.support.Moves.delivered;
import static io.github.rkaushikdev.drawbarline.support.Moves.held;
import static io.github.rkaushikdev.drawbarline.support.Moves.placed;
import static io.github.rkaushikdev.drawbarline.support.Moves.received;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.clock.Interval;
import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.events.EventKind;
import io.github.rkaushikdev.drawbarline.events.EventLog;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportInterchangeTest {

    static final Interval JANUARY = new Interval(utc(2025, 1, 1, 6), utc(2025, 2, 1, 6));
    static final Instant NOW = utc(2025, 1, 20, 12);

    /** CN 400100 received on the 6th and delivered on the 16th; CN 400101 and DBL 104207 still here. */
    static EventLog aLog() {
        EventLog log = new EventLog();
        received(log, "CN 400100", 6);
        delivered(log, "CN 400100", 16);
        received(log, "CN 400101", 8);
        received(log, "DBL 104207", 9);
        return log;
    }

    static Crossing at(List<Crossing> found, String junction) {
        return found.stream()
                .filter(one -> one.junction().equals(junction))
                .findFirst()
                .orElseThrow();
    }

    static OnHand car(List<OnHand> found, String mark) {
        return found.stream()
                .filter(one -> one.car().toString().equals(mark))
                .findFirst()
                .orElseThrow();
    }

    static String firstStartingWith(List<String> lines, String prefix) {
        return lines.stream()
                .filter(line -> line.startsWith(prefix))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void aJunctionAndRoadPairCountsBothWays() {
        List<Crossing> found = InterchangeReport.crossings(aLog(), JANUARY);
        assertThat(found).hasSize(2);
        Crossing taken = at(found, "ESC");
        assertThat(taken.received()).isEqualTo(3);
        assertThat(taken.delivered()).isEqualTo(0);
    }

    @Test
    void aDeliveryCountsAtTheJunctionItHappenedAt() {
        Crossing given = at(InterchangeReport.crossings(aLog(), JANUARY), "MRN");
        assertThat(given.delivered()).isEqualTo(1);
        assertThat(given.received()).isEqualTo(0);
    }

    @Test
    void theNetGrowsWhenWeTakeMoreThanWeGive() {
        List<Crossing> found = InterchangeReport.crossings(aLog(), JANUARY);
        assertThat(found.stream().mapToInt(Crossing::net).sum()).isEqualTo(2);
    }

    @Test
    void aLogWithNoInterchangeCrossesNothing() {
        assertThat(InterchangeReport.crossings(new EventLog(), JANUARY)).isEmpty();
    }

    @Test
    void movementsAreNotCrossings() {
        EventLog log = new EventLog();
        log.record(Event.builder(EventKind.MOVED, CarMark.parse("CN 400100"), utc(2025, 1, 6, 18), "PWR")
                .tenths(336)
                .build());
        assertThat(InterchangeReport.crossings(log, JANUARY)).isEmpty();
    }

    @Test
    void theBalanceReportOpensWithAHeading() {
        List<String> lines = InterchangeReport.balanceReport(aLog(), JANUARY, "2025-01");
        assertThat(lines.get(0)).isEqualTo("INTERCHANGE BALANCE");
    }

    @Test
    void theBalanceReportHasALineAJunction() {
        List<String> lines = InterchangeReport.balanceReport(aLog(), JANUARY, "2025-01");
        assertThat(lines).anyMatch(line -> line.startsWith("ESC"));
        assertThat(lines).anyMatch(line -> line.startsWith("MRN"));
    }

    @Test
    void theBalanceReportTotalsBothDirections() {
        List<String> lines = InterchangeReport.balanceReport(aLog(), JANUARY, "2025-01");
        String[] words = Text.strip(firstStartingWith(lines, "2 junctions")).split("\\s+");
        assertThat(Arrays.asList(words).subList(words.length - 3, words.length)).containsExactly("3", "1", "2");
    }

    @Test
    void anEmptyMonthBalancesAtNothing() {
        List<String> lines = InterchangeReport.balanceReport(new EventLog(), JANUARY, "2025-01");
        assertThat(lines).anyMatch(line -> line.startsWith("0 junctions"));
    }

    @Test
    void aCarTakenAndGivenBackIsNotOnHand() {
        List<OnHand> found = InterchangeReport.onHand(aLog(), NOW);
        assertThat(found.stream().map(OnHand::car)).doesNotContain(CarMark.parse("CN 400100"));
    }

    @Test
    void aCarStillHereIsOnHand() {
        List<OnHand> found = InterchangeReport.onHand(aLog(), NOW);
        assertThat(found.stream().map(one -> one.car().toString())).containsExactly("CN 400101", "DBL 104207");
    }

    @Test
    void carsOnHandComeBackOldestFirst() {
        List<OnHand> found = InterchangeReport.onHand(aLog(), NOW);
        assertThat(found.get(0).since()).isBefore(found.get(1).since());
    }

    @Test
    void aCarOnHandCountsItsHours() {
        assertThat(InterchangeReport.onHand(aLog(), NOW).get(0).hundredthHours())
                .isEqualTo(28800);
    }

    @Test
    void aPlacedCarReportsTheTrackItIsOn() {
        EventLog log = aLog();
        placed(log, "CN 400101", 10);
        OnHand found = car(InterchangeReport.onHand(log, NOW), "CN 400101");
        assertThat(found.industry()).hasValue("ESCPAP1");
    }

    @Test
    void aBadOrderedCarSaysSo() {
        EventLog log = aLog();
        badOrdered(log, "CN 400101", 10);
        OnHand found = car(InterchangeReport.onHand(log, NOW), "CN 400101");
        assertThat(found.badOrdered()).isTrue();
    }

    @Test
    void aHeldCarSaysSo() {
        EventLog log = aLog();
        held(log, "CN 400101", 10);
        OnHand found = car(InterchangeReport.onHand(log, NOW), "CN 400101");
        assertThat(found.held()).isTrue();
    }

    @Test
    void theReportPrintsTheReasonACarIsStuck() {
        EventLog log = aLog();
        badOrdered(log, "CN 400101", 10);
        List<String> lines = InterchangeReport.onHandReport(log, NOW);
        assertThat(firstStartingWith(lines, "CN 400101")).contains("bad order");
    }

    @Test
    void theReportTotalsTheHoursOnHand() {
        List<String> lines = InterchangeReport.onHandReport(aLog(), NOW);
        assertThat(lines).anyMatch(line -> line.startsWith("2 cars"));
    }

    @Test
    void aRailroadWithNothingOnHandSaysNone() {
        List<String> lines = InterchangeReport.onHandReport(new EventLog(), NOW);
        assertThat(lines).anyMatch(line -> line.startsWith("0 cars"));
    }

    @Test
    void theReportNamesTheMomentItWasTaken() {
        List<String> lines = InterchangeReport.onHandReport(aLog(), NOW);
        assertThat(lines.get(1)).isEqualTo("2025-01-20 12:00Z");
    }

    @Test
    void noLineRunsPastThePaper() {
        for (String line : InterchangeReport.onHandReport(aLog(), NOW)) {
            assertThat(line.length()).isLessThanOrEqualTo(80);
        }
    }

    @Test
    void aMomentBeforeACarArrivedLeavesItOut() {
        assertThat(InterchangeReport.onHand(aLog(), utc(2025, 1, 7, 12)))
                .isNotEqualTo(InterchangeReport.onHand(aLog(), NOW));
    }
}
