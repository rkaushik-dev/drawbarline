package io.github.rkaushikdev.drawbarline.reports;

import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static io.github.rkaushikdev.drawbarline.support.Moves.badOrdered;
import static io.github.rkaushikdev.drawbarline.support.Moves.delivered;
import static io.github.rkaushikdev.drawbarline.support.Moves.received;
import static io.github.rkaushikdev.drawbarline.support.Moves.repaired;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.EventError;
import io.github.rkaushikdev.drawbarline.events.EventLog;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportShopTest {

    static final Instant NOW = utc(2025, 1, 20, 8);

    /** Each car received from CN on the 5th. */
    static EventLog aLog(String... cars) {
        EventLog log = new EventLog();
        for (String mark : cars) {
            received(log, mark, 5);
        }
        return log;
    }

    @Test
    void aCarNeverBadOrderedHasNoSpan() {
        assertThat(ShopReport.shopSpans(aLog("CN 400100"), NOW)).isEmpty();
    }

    @Test
    void aCarStillDownShowsAsDown() {
        EventLog log = aLog("CN 400100");
        badOrdered(log, "CN 400100", 10);
        List<ShopSpan> found = ShopReport.shopSpans(log, NOW);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).stillDown()).isTrue();
    }

    @Test
    void aCarStillDownCountsToTheCutOff() {
        EventLog log = aLog("CN 400100");
        badOrdered(log, "CN 400100", 10);
        assertThat(ShopReport.shopSpans(log, NOW).get(0).days()).isEqualTo(10.0);
    }

    @Test
    void aCarThatCameBackCountsTheDaysItWasOut() {
        EventLog log = aLog("CN 400100");
        badOrdered(log, "CN 400100", 10);
        repaired(log, "CN 400100", 14);
        ShopSpan found = ShopReport.shopSpans(log, NOW).get(0);
        assertThat(found.stillDown()).isFalse();
        assertThat(found.days()).isEqualTo(4.0);
    }

    @Test
    void aSpanCarriesWhereTheCarWasSetOut() {
        EventLog log = aLog("CN 400100");
        badOrdered(log, "CN 400100", 10, "PWR");
        assertThat(ShopReport.shopSpans(log, NOW).get(0).station()).isEqualTo("PWR");
    }

    @Test
    void aSpanPrintsItsDaysAndState() {
        EventLog log = aLog("CN 400100");
        badOrdered(log, "CN 400100", 10);
        repaired(log, "CN 400100", 14);
        assertThat(ShopReport.shopSpans(log, NOW).get(0)).hasToString("CN 400100 4.0 days back");
    }

    @Test
    void aCarDownTwiceMakesTwoSpans() {
        EventLog log = aLog("CN 400100");
        badOrdered(log, "CN 400100", 8);
        repaired(log, "CN 400100", 9);
        badOrdered(log, "CN 400100", 12);
        repaired(log, "CN 400100", 15);
        assertThat(ShopReport.shopSpans(log, NOW)).hasSize(2);
    }

    @Test
    void theSpansComeBackOldestFirst() {
        EventLog log = aLog("CN 400100", "CN 400101");
        badOrdered(log, "CN 400101", 8);
        badOrdered(log, "CN 400100", 12);
        assertThat(ShopReport.shopSpans(log, NOW).stream().map(one -> one.car().toString()))
                .containsExactly("CN 400101", "CN 400100");
    }

    @Test
    void aRepairWithoutABadOrderIsRefused() {
        EventLog log = aLog("CN 400100");
        repaired(log, "CN 400100", 10);
        assertThatThrownBy(() -> ShopReport.shopSpans(log, NOW)).isInstanceOf(EventError.class);
    }

    @Test
    void theCarsDownAndTheCarsBackAreSeparated() {
        EventLog log = aLog("CN 400100", "CN 400101");
        badOrdered(log, "CN 400100", 10);
        badOrdered(log, "CN 400101", 10);
        repaired(log, "CN 400101", 14);
        List<ShopSpan> spans = ShopReport.shopSpans(log, NOW);
        assertThat(ShopReport.downNow(spans)).hasSize(1);
        assertThat(ShopReport.cameBack(spans)).hasSize(1);
    }

    @Test
    void theTurnTimeIsTheMeanOfTheCarsThatCameBack() {
        EventLog log = aLog("CN 400100", "CN 400101");
        badOrdered(log, "CN 400100", 10);
        repaired(log, "CN 400100", 12);
        badOrdered(log, "CN 400101", 10);
        repaired(log, "CN 400101", 14);
        assertThat(ShopReport.turnDays(ShopReport.shopSpans(log, NOW))).isEqualTo(3.0);
    }

    @Test
    void aCarStillDownIsLeftOutOfTheTurnTime() {
        EventLog log = aLog("CN 400100", "CN 400101");
        badOrdered(log, "CN 400100", 10);
        repaired(log, "CN 400100", 12);
        badOrdered(log, "CN 400101", 10);
        assertThat(ShopReport.turnDays(ShopReport.shopSpans(log, NOW))).isEqualTo(2.0);
    }

    @Test
    void nothingBackGivesNoTurnTime() {
        EventLog log = aLog("CN 400100");
        badOrdered(log, "CN 400100", 10);
        assertThat(ShopReport.turnDays(ShopReport.shopSpans(log, NOW))).isEqualTo(0.0);
    }

    @Test
    void theReportOpensWithAHeading() {
        List<String> lines = ShopReport.shopReport(aLog("CN 400100"), NOW);
        assertThat(lines.get(0)).isEqualTo("BAD ORDER REPORT");
    }

    @Test
    void theReportHasBothSections() {
        List<String> lines = ShopReport.shopReport(aLog("CN 400100"), NOW);
        assertThat(lines).contains("cars down");
        assertThat(lines).contains("cars back in service");
    }

    @Test
    void theReportListsACarThatIsDown() {
        EventLog log = aLog("CN 400100");
        badOrdered(log, "CN 400100", 10);
        List<String> lines = ShopReport.shopReport(log, NOW);
        assertThat(lines).anyMatch(line -> line.startsWith("CN 400100"));
        assertThat(lines).anyMatch(line -> line.startsWith("1 down"));
    }

    @Test
    void theReportCarriesTheTurnTimeOnTheTotal() {
        EventLog log = aLog("CN 400100");
        badOrdered(log, "CN 400100", 10);
        repaired(log, "CN 400100", 14);
        List<String> lines = ShopReport.shopReport(log, NOW);
        String total = lines.stream()
                .filter(line -> line.startsWith("1 back"))
                .findFirst()
                .orElseThrow();
        assertThat(Text.strip(total)).endsWith("4.0");
    }

    @Test
    void aRailroadWithNothingDownReportsNone() {
        List<String> lines = ShopReport.shopReport(aLog("CN 400100"), NOW);
        assertThat(lines).anyMatch(line -> line.startsWith("0 down"));
        assertThat(lines).anyMatch(line -> line.startsWith("0 back"));
    }

    @Test
    void aDeliveredCarStillShowsItsRepairHistory() {
        EventLog log = aLog("CN 400100");
        badOrdered(log, "CN 400100", 10);
        repaired(log, "CN 400100", 12);
        delivered(log, "CN 400100", 14);
        assertThat(ShopReport.shopSpans(log, NOW)).hasSize(1);
    }

    @Test
    void everyLineFitsThePaper() {
        EventLog log = aLog("CN 400100");
        badOrdered(log, "CN 400100", 10);
        for (String line : ShopReport.shopReport(log, NOW)) {
            assertThat(line.length()).isLessThanOrEqualTo(80);
        }
    }
}
