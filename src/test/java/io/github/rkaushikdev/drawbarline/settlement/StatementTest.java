package io.github.rkaushikdev.drawbarline.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.SettlementError;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatementTest {

    static final ReportingMark CN = new ReportingMark("CN");
    static final ReportingMark ELS = new ReportingMark("ELS");
    static final Period JANUARY = new Period(2025, 1);
    static final Period FEBRUARY = new Period(2025, 2);
    static final LocalDate ISSUED = LocalDate.of(2025, 2, 10);

    /** Post car hire to CN for January on its last day unless told otherwise. */
    static void post(Ledger ledger, ReportingMark road, Period period, PostingKind kind, long cents) {
        ledger.post(new Posting(road, period, kind, new Money(cents), period.lastDay()));
    }

    static void post(Ledger ledger, PostingKind kind, long cents) {
        post(ledger, CN, JANUARY, kind, cents);
    }

    static void post(Ledger ledger, long cents) {
        post(ledger, PostingKind.CAR_HIRE, cents);
    }

    static Ledger aLedger() {
        Ledger ledger = new Ledger();
        post(ledger, 120000);
        post(ledger, PostingKind.REPAIRS, -45000);
        post(ledger, PostingKind.MILEAGE, 3000);
        return ledger;
    }

    static Statement january(Ledger ledger) {
        return Statement.cut(ledger, CN, JANUARY, 1, ISSUED);
    }

    @Test
    void aStatementPrintsTheRoadMonthAndDirection() {
        Statement statement = Statement.cut(aLedger(), CN, JANUARY, 9001, ISSUED);
        assertThat(statement).hasToString("statement 9001 CN 2025-01 $780.00 we pay CN");
    }

    @Test
    void aStatementNumberedBelowOneIsRefused() {
        assertThatThrownBy(() -> new Statement(0, CN, JANUARY, ISSUED, new Money(0), List.of()))
                .isInstanceOf(SettlementError.class);
    }

    @Test
    void aStatementCutBeforeTheMonthEndsIsRefused() {
        assertThatThrownBy(() -> new Statement(1, CN, JANUARY, LocalDate.of(2025, 1, 20), new Money(0), List.of()))
                .isInstanceOf(SettlementError.class);
    }

    @Test
    void aStatementCutOnTheLastDayOfTheMonthIsAllowed() {
        assertThat(new Statement(1, CN, JANUARY, LocalDate.of(2025, 1, 31), new Money(0), List.of()).isSquare())
                .isTrue();
    }

    @Test
    void aFirstMonthOpensAtNothing() {
        assertThat(january(aLedger()).opening()).isEqualTo(new Money(0));
    }

    @Test
    void theMonthMovementIsThePostingsInIt() {
        assertThat(january(aLedger()).movement()).isEqualTo(new Money(78000));
    }

    @Test
    void theClosingBalanceIsTheOpeningPlusTheMovement() {
        Statement statement = january(aLedger());
        assertThat(statement.closing()).isEqualTo(statement.opening().plus(statement.movement()));
    }

    @Test
    void theSecondMonthOpensWhereTheFirstClosed() {
        Ledger ledger = aLedger();
        post(ledger, CN, FEBRUARY, PostingKind.CAR_HIRE, 5000);
        Statement first = Statement.cut(ledger, CN, JANUARY, 1, ISSUED);
        Statement second = Statement.cut(ledger, CN, FEBRUARY, 2, LocalDate.of(2025, 3, 10));
        assertThat(second.opening()).isEqualTo(first.closing());
    }

    @Test
    void aStatementListsOneLineAKind() {
        assertThat(january(aLedger()).lines().stream().map(StatementLine::kind))
                .containsExactly(PostingKind.CAR_HIRE, PostingKind.MILEAGE, PostingKind.REPAIRS);
    }

    @Test
    void aLinePrintsItsKindAndAmount() {
        assertThat(january(aLedger()).lines().get(0)).hasToString("car hire $1,200.00");
    }

    @Test
    void aKindWithNothingPostedReadsAsNothing() {
        assertThat(january(aLedger()).amountFor(PostingKind.DEMURRAGE)).isEqualTo(new Money(0));
    }

    @Test
    void aKindWithSomethingPostedReadsBack() {
        assertThat(january(aLedger()).amountFor(PostingKind.REPAIRS)).isEqualTo(new Money(-45000));
    }

    @Test
    void aRoadThatOwesUsIsTheOneThatPays() {
        Ledger ledger = new Ledger();
        post(ledger, -9000);
        Statement statement = january(ledger);
        assertThat(statement.wePay()).isFalse();
        assertThat(statement.direction()).isEqualTo("CN pays us");
    }

    @Test
    void aBalancedMonthIsSquare() {
        Ledger ledger = new Ledger();
        post(ledger, 9000);
        post(ledger, PostingKind.PAYMENT, -9000);
        Statement statement = january(ledger);
        assertThat(statement.isSquare()).isTrue();
        assertThat(statement.direction()).isEqualTo("square");
    }

    @Test
    void aRoadWithNoPostingsGetsAnEmptyStatement() {
        Statement statement = Statement.cut(aLedger(), ELS, JANUARY, 1, ISSUED);
        assertThat(statement.lines()).isEmpty();
        assertThat(statement.closing()).isEqualTo(new Money(0));
    }

    @Test
    void everyRoadWithAnAccountGetsAStatement() {
        Ledger ledger = aLedger();
        post(ledger, ELS, JANUARY, PostingKind.CAR_HIRE, -8000);
        List<Statement> found = Statement.cutAll(ledger, JANUARY, 9001, ISSUED);
        assertThat(found.stream().map(Statement::road)).containsExactly(CN, ELS);
    }

    @Test
    void statementsAreNumberedInRoadOrder() {
        Ledger ledger = aLedger();
        post(ledger, ELS, JANUARY, PostingKind.CAR_HIRE, -8000);
        List<Statement> found = Statement.cutAll(ledger, JANUARY, 9001, ISSUED);
        assertThat(found.stream().map(Statement::number)).containsExactly(9001, 9002);
    }

    @Test
    void anEmptyLedgerCutsNothing() {
        assertThat(Statement.cutAll(new Ledger(), JANUARY, 1, ISSUED)).isEmpty();
    }

    @Test
    void aPaymentInALaterMonthDoesNotChangeTheEarlierStatement() {
        Ledger ledger = aLedger();
        post(ledger, CN, FEBRUARY, PostingKind.PAYMENT, -78000);
        assertThat(january(ledger).closing()).isEqualTo(new Money(78000));
    }

    @Test
    void thePaymentSquaresTheFollowingMonth() {
        Ledger ledger = aLedger();
        post(ledger, CN, FEBRUARY, PostingKind.PAYMENT, -78000);
        Statement second = Statement.cut(ledger, CN, FEBRUARY, 2, LocalDate.of(2025, 3, 10));
        assertThat(second.closing()).isEqualTo(new Money(0));
    }
}
