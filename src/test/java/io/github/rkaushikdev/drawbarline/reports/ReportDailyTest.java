package io.github.rkaushikdev.drawbarline.reports;

import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static io.github.rkaushikdev.drawbarline.support.Moves.badOrdered;
import static io.github.rkaushikdev.drawbarline.support.Moves.delivered;
import static io.github.rkaushikdev.drawbarline.support.Moves.placed;
import static io.github.rkaushikdev.drawbarline.support.Moves.pulled;
import static io.github.rkaushikdev.drawbarline.support.Moves.received;
import static io.github.rkaushikdev.drawbarline.support.Moves.released;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.clock.Interval;
import io.github.rkaushikdev.drawbarline.distribution.CarOrder;
import io.github.rkaushikdev.drawbarline.distribution.Distribution;
import io.github.rkaushikdev.drawbarline.embargo.Embargo;
import io.github.rkaushikdev.drawbarline.embargo.EmbargoRegister;
import io.github.rkaushikdev.drawbarline.events.EventLog;
import io.github.rkaushikdev.drawbarline.fleet.CarKind;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportDailyTest {

    static final LocalDate DAY = LocalDate.of(2025, 1, 14);
    static final Instant AT = utc(2025, 1, 14, 8);

    /** Three cars received on the 13th, one of them delivered again and one placed. */
    static EventLog aLog() {
        EventLog log = new EventLog();
        received(log, "CN 400100", 13, 9);
        received(log, "CN 400101", 13, 10);
        received(log, "CN 400102", 13, 11);
        delivered(log, "CN 400102", 13, 12);
        placed(log, "CN 400100", 13);
        return log;
    }

    static EmbargoRegister someEmbargoes() {
        EmbargoRegister register = new EmbargoRegister();
        register.issue(Embargo.builder(101, LocalDate.of(2025, 1, 6), "bridge out")
                .station("HRM")
                .build());
        return register;
    }

    /** A distribution with nothing assigned and nothing short. */
    static Distribution nothingDistributed() {
        return Distribution.distribute(List.of(), List.of(), Map.of());
    }

    static List<String> aSheet(EventLog log) {
        return DailyReport.dailySummary(log, new EmbargoRegister(), nothingDistributed(), DAY, AT);
    }

    @Test
    void theWindowIsTheDayBeforeTheMoment() {
        Interval found = DailyReport.windowOf(DAY, AT);
        assertThat(found.end())
                .isEqualTo(DAY.atTime(LocalTime.ofInstant(AT, ZoneOffset.UTC)).toInstant(ZoneOffset.UTC));
        assertThat(found.minutes()).isEqualTo(24 * 60);
    }

    @Test
    void theCountsSplitByKind() {
        DayCounts found = DailyReport.countsIn(aLog(), DailyReport.windowOf(DAY, AT));
        assertThat(found.received()).isEqualTo(3);
        assertThat(found.delivered()).isEqualTo(1);
        assertThat(found.placed()).isEqualTo(1);
    }

    @Test
    void theNetChangeIsWhatWeTookLessWhatWeGave() {
        assertThat(DailyReport.countsIn(aLog(), DailyReport.windowOf(DAY, AT)).netCars())
                .isEqualTo(2);
    }

    @Test
    void theWorkedCountIsPlacementsAndPulls() {
        EventLog log = aLog();
        released(log, "CN 400100", 13);
        pulled(log, "CN 400100", 13);
        assertThat(DailyReport.countsIn(log, DailyReport.windowOf(DAY, AT)).worked())
                .isEqualTo(2);
    }

    @Test
    void anEmptyDayCountsNothing() {
        DayCounts found = DailyReport.countsIn(new EventLog(), DailyReport.windowOf(DAY, AT));
        assertThat(found.received()).isEqualTo(0);
        assertThat(found.netCars()).isEqualTo(0);
    }

    @Test
    void anEventOutsideTheWindowIsNotCounted() {
        EventLog log = new EventLog();
        received(log, "CN 400100", 6);
        assertThat(DailyReport.countsIn(log, DailyReport.windowOf(DAY, AT)).received())
                .isEqualTo(0);
    }

    @Test
    void theSheetOpensWithAHeading() {
        List<String> lines = aSheet(aLog());
        assertThat(lines.get(0)).isEqualTo("MORNING SHEET");
        assertThat(lines.get(1)).isEqualTo("2025-01-14");
    }

    @Test
    void theSheetCarriesTheMovementTable() {
        List<String> lines = aSheet(aLog());
        assertThat(lines).anyMatch(line -> line.startsWith("received in interchange"));
        assertThat(lines).anyMatch(line -> line.startsWith("net change on line"));
    }

    @Test
    void theSheetCountsTheCarsOnHand() {
        assertThat(aSheet(aLog())).anyMatch(line -> line.contains("cars on hand          2"));
    }

    @Test
    void theSheetCountsTheBadOrders() {
        EventLog log = aLog();
        badOrdered(log, "CN 400101", 13, "ESC", 14);
        assertThat(aSheet(log)).anyMatch(line -> line.contains("cars bad ordered      1"));
    }

    @Test
    void aBadOrderedCarIsNamedAtTheBottom() {
        EventLog log = aLog();
        badOrdered(log, "CN 400101", 13, "ESC", 14);
        List<String> lines = aSheet(log);
        assertThat(lines).contains("bad order");
        assertThat(lines).anyMatch(line -> line.contains("CN 400101"));
    }

    @Test
    void theSheetCountsTheLiveEmbargoes() {
        List<String> lines = DailyReport.dailySummary(aLog(), someEmbargoes(), nothingDistributed(), DAY, AT);
        assertThat(lines).anyMatch(line -> line.contains("embargoes in force    1"));
    }

    @Test
    void aLiveEmbargoIsNamedAtTheBottom() {
        List<String> lines = DailyReport.dailySummary(aLog(), someEmbargoes(), nothingDistributed(), DAY, AT);
        assertThat(lines).anyMatch(line -> line.contains("embargo 101 at HRM"));
    }

    @Test
    void aRailroadWithNoEmbargoesNamesNone() {
        assertThat(aSheet(aLog())).doesNotContain("embargoes");
    }

    @Test
    void theSheetCarriesTheCarOrders() {
        CarOrder order = new CarOrder(1, "ESCPAP", "ESCPAP1", CarKind.COVERED_HOPPER, 3, DAY);
        Distribution found = Distribution.distribute(List.of(order), List.of(), Map.of("ESCPAP1", "ESC"));
        assertThat(found.shortages()).isEqualTo(Map.of(1, 3));
        List<String> lines = DailyReport.dailySummary(aLog(), new EmbargoRegister(), found, DAY, AT);
        assertThat(lines).anyMatch(line -> line.contains("car orders short      3"));
    }

    @Test
    void anEmptyRailroadStillProducesASheet() {
        List<String> lines = aSheet(new EventLog());
        assertThat(lines.get(0)).isEqualTo("MORNING SHEET");
        assertThat(lines).anyMatch(line -> line.contains("cars on hand          0"));
    }

    @Test
    void everyLineFitsThePaper() {
        List<String> lines = DailyReport.dailySummary(aLog(), someEmbargoes(), nothingDistributed(), DAY, AT);
        for (String line : lines) {
            assertThat(line.length()).isLessThanOrEqualTo(80);
        }
    }
}
