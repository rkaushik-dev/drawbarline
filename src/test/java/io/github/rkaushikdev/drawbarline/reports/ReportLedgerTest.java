package io.github.rkaushikdev.drawbarline.reports;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.settlement.Ledger;
import io.github.rkaushikdev.drawbarline.settlement.Period;
import io.github.rkaushikdev.drawbarline.settlement.Posting;
import io.github.rkaushikdev.drawbarline.settlement.PostingKind;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportLedgerTest {

    static final ReportingMark CN = new ReportingMark("CN");
    static final ReportingMark ELS = new ReportingMark("ELS");
    static final Period JANUARY = new Period(2025, 1);
    static final Period FEBRUARY = new Period(2025, 2);

    static void post(Ledger ledger, ReportingMark road, Period period, PostingKind kind, long cents) {
        ledger.post(new Posting(road, period, kind, new Money(cents), period.lastDay()));
    }

    /** January: CN car hire and repairs, ELS car hire. */
    static Ledger aLedger() {
        Ledger ledger = new Ledger();
        post(ledger, CN, JANUARY, PostingKind.CAR_HIRE, 120000);
        post(ledger, CN, JANUARY, PostingKind.REPAIRS, -45000);
        post(ledger, ELS, JANUARY, PostingKind.CAR_HIRE, 8000);
        return ledger;
    }

    @Test
    void aLineCarriesTheThreeNumbers() {
        BalanceLine found = LedgerReport.trialBalance(aLedger(), JANUARY).get(0);
        assertThat(found.opening()).isEqualTo(new Money(0));
        assertThat(found.movement()).isEqualTo(new Money(75000));
        assertThat(found.closing()).isEqualTo(new Money(75000));
    }

    @Test
    void aLinePrintsTheIdentity() {
        BalanceLine found = LedgerReport.trialBalance(aLedger(), JANUARY).get(0);
        assertThat(found).hasToString("CN $0.00 + $750.00 = $750.00");
    }

    @Test
    void everyLineBalances() {
        assertThat(LedgerReport.trialBalance(aLedger(), JANUARY)).allMatch(BalanceLine::balances);
    }

    @Test
    void aLineCountsThePostingsInTheMonth() {
        assertThat(LedgerReport.trialBalance(aLedger(), JANUARY).get(0).postings())
                .isEqualTo(2);
    }

    @Test
    void theSecondMonthOpensWhereTheFirstClosed() {
        Ledger ledger = aLedger();
        post(ledger, CN, FEBRUARY, PostingKind.CAR_HIRE, 5000);
        BalanceLine first = LedgerReport.trialBalance(ledger, JANUARY).get(0);
        BalanceLine second = LedgerReport.trialBalance(ledger, FEBRUARY).get(0);
        assertThat(second.opening()).isEqualTo(first.closing());
    }

    @Test
    void theWholePoolBalances() {
        Ledger ledger = aLedger();
        assertThat(LedgerReport.balances(LedgerReport.trialBalance(ledger, JANUARY), ledger, JANUARY))
                .isTrue();
    }

    @Test
    void anEmptyLedgerBalances() {
        Ledger ledger = new Ledger();
        assertThat(LedgerReport.balances(LedgerReport.trialBalance(ledger, JANUARY), ledger, JANUARY))
                .isTrue();
    }

    @Test
    void theReportOpensWithAHeading() {
        List<String> lines = LedgerReport.trialBalanceReport(aLedger(), JANUARY);
        assertThat(lines.get(0)).isEqualTo("TRIAL BALANCE");
        assertThat(lines.get(1)).isEqualTo("2025-01");
    }

    @Test
    void theReportHasALineARoad() {
        List<String> lines = LedgerReport.trialBalanceReport(aLedger(), JANUARY);
        assertThat(lines).anyMatch(line -> line.startsWith("CN"));
        assertThat(lines).anyMatch(line -> line.startsWith("ELS"));
    }

    @Test
    void theReportTotalsEveryRoad() {
        List<String> lines = LedgerReport.trialBalanceReport(aLedger(), JANUARY);
        String total = lines.stream()
                .filter(line -> line.startsWith("2 roads"))
                .findFirst()
                .orElseThrow();
        assertThat(total).contains("$830.00");
    }

    @Test
    void theReportSaysItBalances() {
        List<String> lines = LedgerReport.trialBalanceReport(aLedger(), JANUARY);
        assertThat(lines.get(lines.size() - 1)).isEqualTo("balances");
    }

    @Test
    void aMonthWithNothingInItStillBalances() {
        List<String> lines = LedgerReport.trialBalanceReport(new Ledger(), JANUARY);
        assertThat(lines.get(lines.size() - 1)).isEqualTo("balances");
    }

    @Test
    void theKindReportSplitsTheMonth() {
        List<String> lines = LedgerReport.byKindReport(aLedger(), JANUARY);
        assertThat(lines.get(0)).isEqualTo("POSTINGS BY KIND");
        assertThat(lines).anyMatch(line -> line.startsWith("car hire"));
        assertThat(lines).anyMatch(line -> line.startsWith("repairs"));
    }

    @Test
    void theKindReportTotalsToTheSameNumber() {
        List<String> kinds = LedgerReport.byKindReport(aLedger(), JANUARY);
        List<String> balance = LedgerReport.trialBalanceReport(aLedger(), JANUARY);
        assertThat(kinds).anyMatch(line -> line.contains("$830.00"));
        assertThat(balance).anyMatch(line -> line.contains("$830.00"));
    }

    @Test
    void aKindWithNothingPostedIsLeftOff() {
        List<String> lines = LedgerReport.byKindReport(aLedger(), JANUARY);
        assertThat(lines).noneMatch(line -> line.startsWith("demurrage"));
    }

    @Test
    void theKindsComeBackInTheirPublishedOrder() {
        Ledger ledger = aLedger();
        post(ledger, CN, JANUARY, PostingKind.PAYMENT, -1000);
        List<String> lines = LedgerReport.byKindReport(ledger, JANUARY);
        List<String> wanted = List.of("car hire", "repairs", "payment");
        List<String> rows = lines.stream()
                .filter(line -> wanted.stream().anyMatch(line::startsWith))
                .map(line -> line.split("  ")[0])
                .toList();
        assertThat(rows).containsExactly("car hire", "repairs", "payment");
    }

    @Test
    void everyLineFitsThePaper() {
        for (String line : LedgerReport.trialBalanceReport(aLedger(), JANUARY)) {
            assertThat(line.length()).isLessThanOrEqualTo(80);
        }
    }

    @Test
    void aMonthBeforeAnythingWasPostedOpensAtNothing() {
        List<BalanceLine> found = LedgerReport.trialBalance(aLedger(), new Period(2024, 12));
        assertThat(found).allMatch(one -> one.opening().equals(new Money(0)));
    }
}
