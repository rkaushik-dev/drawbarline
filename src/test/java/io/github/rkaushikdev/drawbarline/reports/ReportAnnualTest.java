package io.github.rkaushikdev.drawbarline.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.SettlementError;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.settlement.Ledger;
import io.github.rkaushikdev.drawbarline.settlement.Period;
import io.github.rkaushikdev.drawbarline.settlement.Posting;
import io.github.rkaushikdev.drawbarline.settlement.PostingKind;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportAnnualTest {

    static final ReportingMark CN = new ReportingMark("CN");
    static final ReportingMark ELS = new ReportingMark("ELS");

    /** CN car hire for January to March, CN repairs and ELS car hire in January. */
    static Ledger aLedger() {
        Ledger ledger = new Ledger();
        long[][] months = {{1, 120000}, {2, 90000}, {3, 150000}};
        for (long[] month : months) {
            Period period = new Period(2025, (int) month[0]);
            ledger.post(new Posting(CN, period, PostingKind.CAR_HIRE, new Money(month[1]), period.lastDay()));
        }
        Period january = new Period(2025, 1);
        ledger.post(new Posting(CN, january, PostingKind.REPAIRS, new Money(-45000), january.lastDay()));
        ledger.post(new Posting(ELS, january, PostingKind.CAR_HIRE, new Money(8000), january.lastDay()));
        return ledger;
    }

    static MonthLine january() {
        return AnnualReport.annual(aLedger(), 2025).get(0);
    }

    @Test
    void aYearIsTwelveMonths() {
        assertThat(AnnualReport.monthsOf(2025)).hasSize(12);
    }

    @Test
    void aYearWeWouldNeverSettleIsRefused() {
        assertThatThrownBy(() -> AnnualReport.monthsOf(1700)).isInstanceOf(SettlementError.class);
    }

    @Test
    void aLineAMonthComesBack() {
        assertThat(AnnualReport.annual(aLedger(), 2025)).hasSize(12);
    }

    @Test
    void aMonthCarriesItsKinds() {
        MonthLine found = january();
        assertThat(found.amountOf(PostingKind.CAR_HIRE)).isEqualTo(new Money(128000));
        assertThat(found.amountOf(PostingKind.REPAIRS)).isEqualTo(new Money(-45000));
    }

    @Test
    void aKindWithNothingPostedReadsAsNothing() {
        assertThat(january().amountOf(PostingKind.DEMURRAGE)).isEqualTo(new Money(0));
    }

    @Test
    void theMovementIsTheKindsTogether() {
        assertThat(january().movement()).isEqualTo(new Money(83000));
    }

    @Test
    void aMonthCountsItsPostings() {
        assertThat(january().postings()).isEqualTo(3);
    }

    @Test
    void aMonthWithNothingInItMovesNothing() {
        MonthLine found = AnnualReport.annual(aLedger(), 2025).get(11);
        assertThat(found.movement()).isEqualTo(new Money(0));
        assertThat(found.postings()).isEqualTo(0);
    }

    @Test
    void aMonthPrintsItsMovement() {
        assertThat(january()).hasToString("2025-01 $830.00");
    }

    @Test
    void theYearCanBeReadForOneRoad() {
        MonthLine found = AnnualReport.annual(aLedger(), 2025, ELS).get(0);
        assertThat(found.amountOf(PostingKind.CAR_HIRE)).isEqualTo(new Money(8000));
    }

    @Test
    void aRoadWithNothingPostedReadsAsAnEmptyYear() {
        List<MonthLine> found = AnnualReport.annual(aLedger(), 2025, new ReportingMark("CP"));
        assertThat(found).allMatch(one -> one.movement().equals(new Money(0)));
    }

    @Test
    void theReportOpensWithAHeading() {
        List<String> lines = AnnualReport.annualReport(aLedger(), 2025);
        assertThat(lines.get(0)).isEqualTo("ANNUAL SUMMARY");
        assertThat(lines.get(1)).isEqualTo("2025 all roads");
    }

    @Test
    void theReportNamesOneRoadWhenAsked() {
        assertThat(AnnualReport.annualReport(aLedger(), 2025, CN).get(1)).isEqualTo("2025 CN");
    }

    @Test
    void theReportHasARowAMonth() {
        List<String> lines = AnnualReport.annualReport(aLedger(), 2025);
        assertThat(lines.stream().filter(line -> line.startsWith("2025-"))).hasSize(12);
    }

    @Test
    void theReportTotalsTheYear() {
        List<String> lines = AnnualReport.annualReport(aLedger(), 2025);
        List<String> totals = lines.stream()
                .filter(line -> Text.strip(line).startsWith("2025 "))
                .toList();
        assertThat(totals.get(totals.size() - 1)).contains("$3,230.00");
    }

    @Test
    void theYearTotalAgreesWithTheMonths() {
        List<MonthLine> lines = AnnualReport.annual(aLedger(), 2025);
        assertThat(lines.stream().mapToLong(one -> one.movement().cents()).sum())
                .isEqualTo(323000);
    }

    @Test
    void theBusiestMonthIsTheOneThatMovedMost() {
        assertThat(AnnualReport.busiestMonth(AnnualReport.annual(aLedger(), 2025)))
                .hasValueSatisfying(one -> assertThat(one.period()).isEqualTo(new Period(2025, 3)));
    }

    @Test
    void aYearWithNothingInItHasNoBusiestMonth() {
        assertThat(AnnualReport.busiestMonth(AnnualReport.annual(new Ledger(), 2025)))
                .isEmpty();
    }

    @Test
    void everyLineFitsAWideCarriage() {
        for (String line : AnnualReport.annualReport(aLedger(), 2025)) {
            assertThat(line.length()).isLessThanOrEqualTo(132);
        }
    }
}
