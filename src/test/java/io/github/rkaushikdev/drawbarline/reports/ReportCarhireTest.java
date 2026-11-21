package io.github.rkaushikdev.drawbarline.reports;

import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.carhire.Charge;
import io.github.rkaushikdev.drawbarline.carhire.Liability;
import io.github.rkaushikdev.drawbarline.clock.Interval;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.settlement.Period;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ReportCarhireTest {

    static final Period JANUARY = new Period(2025, 1);
    static final ReportingMark CN = new ReportingMark("CN");
    static final ReportingMark ELS = new ReportingMark("ELS");

    /** Two CN cars and one owed to ELS, each on hand from noon on the 6th to noon on the 16th. */
    static Liability aLiability() {
        Liability found = new Liability(JANUARY.window());
        Interval window = new Interval(utc(2025, 1, 6, 12), utc(2025, 1, 16, 12));
        found.add(new Charge(CarMark.parse("CN 400100"), CN, window, new Money(111120), new Money(4066)));
        found.add(new Charge(CarMark.parse("CN 400101"), CN, window, new Money(111120), new Money(0)));
        found.add(new Charge(CarMark.parse("DBL 104207"), ELS, window, new Money(55560), new Money(1210)));
        return found;
    }

    static String firstStartingWith(List<String> lines, String prefix) {
        return lines.stream()
                .filter(line -> line.startsWith(prefix))
                .findFirst()
                .orElseThrow();
    }

    static Stream<Integer> widths() {
        return CarHireReport.liabilityReport(aLiability(), JANUARY).stream().map(String::length);
    }

    @Test
    void theSummaryOpensWithAHeading() {
        List<String> lines = CarHireReport.liabilityReport(aLiability(), JANUARY);
        assertThat(lines.get(0)).isEqualTo("CAR HIRE LIABILITY");
        assertThat(lines.get(1)).isEqualTo("2025-01");
    }

    @Test
    void theSummaryHasOneLineARoad() {
        List<String> lines = CarHireReport.liabilityReport(aLiability(), JANUARY);
        assertThat(lines.stream().filter(line -> line.startsWith("CN ") || line.startsWith("ELS")))
                .hasSize(2);
    }

    @Test
    void theSummaryCountsTheCarsOnEachRoad() {
        List<String> lines = CarHireReport.liabilityReport(aLiability(), JANUARY);
        assertThat(firstStartingWith(lines, "CN")).contains(" 2 ");
    }

    @Test
    void theSummaryKeepsTheTwoHalvesApart() {
        List<String> lines = CarHireReport.liabilityReport(aLiability(), JANUARY);
        String cn = firstStartingWith(lines, "CN");
        assertThat(cn).contains("$2,222.40");
        assertThat(cn).contains("$40.66");
    }

    @Test
    void theSummaryTotalsEveryRoad() {
        List<String> lines = CarHireReport.liabilityReport(aLiability(), JANUARY);
        assertThat(firstStartingWith(lines, "all roads")).contains("$2,830.76");
    }

    @Test
    void theTotalCountsEveryCar() {
        List<String> lines = CarHireReport.liabilityReport(aLiability(), JANUARY);
        assertThat(firstStartingWith(lines, "all roads")).contains(" 3 ");
    }

    @Test
    void anEmptyLiabilityStillPrintsATotal() {
        List<String> lines = CarHireReport.liabilityReport(new Liability(JANUARY.window()), JANUARY);
        assertThat(lines).anyMatch(line -> line.startsWith("all roads"));
    }

    @Test
    void theDetailNamesTheRoad() {
        List<String> lines = CarHireReport.detailReport(aLiability(), CN, JANUARY);
        assertThat(lines.get(0)).isEqualTo("CAR HIRE DETAIL CN");
    }

    @Test
    void theDetailHasOneLineACar() {
        List<String> lines = CarHireReport.detailReport(aLiability(), CN, JANUARY);
        assertThat(lines.stream().filter(line -> line.startsWith("CN 4001"))).hasSize(2);
    }

    @Test
    void theDetailPrintsTheHours() {
        List<String> lines = CarHireReport.detailReport(aLiability(), CN, JANUARY);
        assertThat(lines).anyMatch(line -> line.contains("240.00"));
    }

    @Test
    void theDetailPrintsTheWindowEnds() {
        List<String> lines = CarHireReport.detailReport(aLiability(), CN, JANUARY);
        assertThat(lines).anyMatch(line -> line.contains("01-06 12:00"));
    }

    @Test
    void theDetailTotalsTheChargesItShows() {
        List<String> lines = CarHireReport.detailReport(aLiability(), CN, JANUARY);
        assertThat(firstStartingWith(lines, "2 charges")).contains("$2,222.40");
    }

    @Test
    void theDetailLeavesOutOtherRoads() {
        List<String> lines = CarHireReport.detailReport(aLiability(), CN, JANUARY);
        assertThat(lines).noneMatch(line -> line.contains("DBL 104207"));
    }

    @Test
    void aRoadWithNothingOwedPrintsAnEmptyDetail() {
        List<String> lines = CarHireReport.detailReport(new Liability(JANUARY.window()), CN, JANUARY);
        assertThat(lines).anyMatch(line -> line.startsWith("0 charges"));
    }

    @Test
    void theSummaryIsAvailableAsData() {
        assertThat(CarHireReport.owedTo(aLiability()))
                .isEqualTo(Map.of("CN", new Money(226306), "ELS", new Money(56770)));
    }

    @Test
    void theDataAndTheReportAgree() {
        Liability found = aLiability();
        List<String> lines = CarHireReport.liabilityReport(found, JANUARY);
        for (Map.Entry<String, Money> owed : CarHireReport.owedTo(found).entrySet()) {
            assertThat(firstStartingWith(lines, owed.getKey()))
                    .contains(owed.getValue().toString());
        }
    }

    @ParameterizedTest
    @MethodSource("widths")
    void noLineRunsPastThePaper(int width) {
        assertThat(width).isLessThanOrEqualTo(80);
    }
}
