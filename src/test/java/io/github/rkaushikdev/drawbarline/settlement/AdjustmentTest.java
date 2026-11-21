package io.github.rkaushikdev.drawbarline.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.SettlementError;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AdjustmentTest {

    static final ReportingMark CN = new ReportingMark("CN");
    static final Period JANUARY = new Period(2025, 1);
    static final Period MARCH = new Period(2025, 3);

    /** The fields a test adjustment carries unless the test changes one. */
    static final class AnAdjustment {
        Period corrects = JANUARY;
        Period postedIn = MARCH;
        Money amount = new Money(-4500);
        String reason = "car hire on a car we never took";

        AnAdjustment corrects(Period value) {
            corrects = value;
            return this;
        }

        AnAdjustment postedIn(Period value) {
            postedIn = value;
            return this;
        }

        AnAdjustment amount(Money value) {
            amount = value;
            return this;
        }

        AnAdjustment reason(String value) {
            reason = value;
            return this;
        }

        Adjustment build() {
            return new Adjustment(CN, corrects, postedIn, amount, LocalDate.of(2025, 3, 12), reason);
        }
    }

    /** The fields a test remittance carries unless the test changes one. */
    static final class ARemittance {
        int number = 7001;
        Money amount = new Money(120000);
        LocalDate receivedOn = LocalDate.of(2025, 2, 20);
        String reference = "";

        ARemittance number(int value) {
            number = value;
            return this;
        }

        ARemittance amount(Money value) {
            amount = value;
            return this;
        }

        ARemittance receivedOn(LocalDate value) {
            receivedOn = value;
            return this;
        }

        ARemittance reference(String value) {
            reference = value;
            return this;
        }

        Remittance build() {
            return new Remittance(number, CN, JANUARY, amount, receivedOn, reference);
        }
    }

    static AnAdjustment an() {
        return new AnAdjustment();
    }

    static Adjustment anAdjustment() {
        return an().build();
    }

    static ARemittance a() {
        return new ARemittance();
    }

    static Remittance aRemittance() {
        return a().build();
    }

    /** A ledger holding $1,200.00 of January car hire owed to CN, and its register of months. */
    record Books(Ledger ledger, PeriodRegister periods) {}

    static Books aLedger() {
        PeriodRegister periods = new PeriodRegister();
        Ledger ledger = new Ledger(periods);
        ledger.post(new Posting(CN, JANUARY, PostingKind.CAR_HIRE, new Money(120000), JANUARY.lastDay()));
        return new Books(ledger, periods);
    }

    @Test
    void anAdjustmentPrintsWhichMonthItCorrects() {
        assertThat(anAdjustment()).hasToString("$45.00 from CN adjusting 2025-01");
    }

    @Test
    void anAdjustmentTheOtherWayPrintsAsGoingOut() {
        assertThat(an().amount(new Money(4500)).build().toString()).startsWith("$45.00 to CN");
    }

    @Test
    void anAdjustmentOfNothingIsRefused() {
        assertThatThrownBy(() -> an().amount(new Money(0)).build()).isInstanceOf(SettlementError.class);
    }

    @Test
    void anAdjustmentWithNoReasonIsRefused() {
        assertThatThrownBy(() -> an().reason("  ").build()).isInstanceOf(SettlementError.class);
    }

    @Test
    void anAdjustmentIntoTheMonthItCorrectsIsRefused() {
        assertThatThrownBy(() -> an().postedIn(JANUARY).build()).isInstanceOf(SettlementError.class);
    }

    @Test
    void anAdjustmentIntoAnEarlierMonthIsRefused() {
        assertThatThrownBy(() -> an().corrects(MARCH).postedIn(JANUARY).build()).isInstanceOf(SettlementError.class);
    }

    @Test
    void theReferenceNamesTheMonthAndTheReason() {
        assertThat(anAdjustment().reference()).startsWith("adjusts 2025-01: car hire");
    }

    @Test
    void aLongReasonIsCutToTheField() {
        Adjustment found = an().reason("x".repeat(200)).build();
        assertThat(found.reference()).hasSize(60);
    }

    @Test
    void anAdjustmentGoesIntoTheOpenMonth() {
        Books books = aLedger();
        books.periods().close(JANUARY);
        Posting found = Adjustment.postAdjustment(books.ledger(), books.periods(), anAdjustment());
        assertThat(found.period()).isEqualTo(MARCH);
        assertThat(found.kind()).isSameAs(PostingKind.ADJUSTMENT);
    }

    @Test
    void anAdjustmentToAMonthThatIsStillOpenIsRefused() {
        Books books = aLedger();
        Adjustment adjustment = anAdjustment();
        assertThatThrownBy(() -> Adjustment.postAdjustment(books.ledger(), books.periods(), adjustment))
                .isInstanceOf(SettlementError.class);
    }

    @Test
    void anAdjustmentIntoAClosedMonthIsRefused() {
        Books books = aLedger();
        books.periods().close(JANUARY);
        books.periods().close(MARCH);
        Adjustment adjustment = anAdjustment();
        assertThatThrownBy(() -> Adjustment.postAdjustment(books.ledger(), books.periods(), adjustment))
                .isInstanceOf(SettlementError.class);
    }

    @Test
    void theClosedMonthIsLeftExactlyAsItWas() {
        Books books = aLedger();
        books.periods().close(JANUARY);
        Adjustment.postAdjustment(books.ledger(), books.periods(), anAdjustment());
        assertThat(books.ledger().account(CN).balance(JANUARY)).isEqualTo(new Money(120000));
    }

    @Test
    void theAdjustmentMovesTheRunningBalance() {
        Books books = aLedger();
        books.periods().close(JANUARY);
        Adjustment.postAdjustment(books.ledger(), books.periods(), anAdjustment());
        assertThat(Remittance.outstanding(books.ledger(), CN, MARCH)).isEqualTo(new Money(115500));
    }

    @Test
    void aRemittancePrintsWhatItSettles() {
        assertThat(aRemittance()).hasToString("remittance 7001 CN $1,200.00 for 2025-01");
    }

    @Test
    void aRemittanceNumberedBelowOneIsRefused() {
        assertThatThrownBy(() -> a().number(0).build()).isInstanceOf(SettlementError.class);
    }

    @Test
    void aRemittanceForNothingIsRefused() {
        assertThatThrownBy(() -> a().amount(new Money(0)).build()).isInstanceOf(SettlementError.class);
    }

    @Test
    void aPaymentBeforeTheMonthEndedIsRefused() {
        assertThatThrownBy(() -> a().receivedOn(LocalDate.of(2025, 1, 15)).build())
                .isInstanceOf(SettlementError.class);
    }

    @Test
    void aPaymentBringsTheBalanceDown() {
        Ledger ledger = aLedger().ledger();
        Remittance.applyRemittance(ledger, aRemittance());
        assertThat(Remittance.outstanding(ledger, CN, JANUARY)).isEqualTo(new Money(0));
    }

    @Test
    void aPaymentPostsAgainstTheMonthItSettles() {
        Ledger ledger = aLedger().ledger();
        Posting found = Remittance.applyRemittance(ledger, aRemittance());
        assertThat(found.period()).isEqualTo(JANUARY);
        assertThat(found.kind()).isSameAs(PostingKind.PAYMENT);
    }

    @Test
    void aPaymentCarriesItsNumberAsAReference() {
        Ledger ledger = aLedger().ledger();
        assertThat(Remittance.applyRemittance(ledger, aRemittance()).reference())
                .isEqualTo("remittance 7001");
    }

    @Test
    void aPaymentKeepsAReferenceItWasGiven() {
        Ledger ledger = aLedger().ledger();
        Posting found =
                Remittance.applyRemittance(ledger, a().reference("cheque 41207").build());
        assertThat(found.reference()).isEqualTo("cheque 41207");
    }

    @Test
    void aPartPaymentLeavesTheRestOwing() {
        Ledger ledger = aLedger().ledger();
        Remittance.applyRemittance(ledger, a().amount(new Money(80000)).build());
        assertThat(Remittance.outstanding(ledger, CN, JANUARY)).isEqualTo(new Money(40000));
    }

    @Test
    void aRoadThatPaysTooMuchIsFlagged() {
        Ledger ledger = aLedger().ledger();
        Remittance.applyRemittance(ledger, a().amount(new Money(150000)).build());
        assertThat(Remittance.overpaid(ledger, CN, JANUARY)).isTrue();
    }

    @Test
    void aRoadThatPaysExactlyIsNotOverpaid() {
        Ledger ledger = aLedger().ledger();
        Remittance.applyRemittance(ledger, aRemittance());
        assertThat(Remittance.overpaid(ledger, CN, JANUARY)).isFalse();
    }

    @Test
    void aRoadWithNothingPostedOwesNothing() {
        Ledger ledger = aLedger().ledger();
        assertThat(Remittance.outstanding(ledger, new ReportingMark("ELS"), JANUARY))
                .isEqualTo(new Money(0));
    }
}
