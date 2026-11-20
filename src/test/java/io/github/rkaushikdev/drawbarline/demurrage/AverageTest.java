package io.github.rkaushikdev.drawbarline.demurrage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.TariffError;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AverageTest {

    static final CarMark CAR = CarMark.parse("CN 400100");
    static final AverageAgreement AGREEMENT = new AverageAgreement("PWRGRN", 12000, LocalDate.of(2024, 7, 1));

    static Detention aDetention() {
        return aDetention(0);
    }

    static Detention aDetention(int chargeable) {
        return aDetention(chargeable, LocalDate.of(2025, 1, 15));
    }

    static Detention aDetention(int chargeable, LocalDate countedTo) {
        return new Detention(
                CAR,
                "PWRGRN1",
                LocalDate.of(2025, 1, 14),
                LocalDate.of(2025, 1, 15),
                Optional.of(LocalDate.of(2025, 1, 16)),
                countedTo,
                chargeable,
                true,
                false);
    }

    static AverageLedger january() {
        return new AverageLedger(AGREEMENT, LocalDate.of(2025, 1, 1));
    }

    @Test
    void anAgreementPrintsItsShipperAndRate() {
        assertThat(AGREEMENT).hasToString("PWRGRN at $120.00 a debit from 2024-07-01");
    }

    @Test
    void anAgreementCoversDaysFromItsStart() {
        assertThat(AGREEMENT.covers(LocalDate.of(2024, 6, 30))).isFalse();
        assertThat(AGREEMENT.covers(LocalDate.of(2024, 7, 1))).isTrue();
    }

    @Test
    void anOpenAgreementCoversEverythingAfter() {
        assertThat(AGREEMENT.covers(LocalDate.of(2099, 1, 1))).isTrue();
    }

    @Test
    void anExpiredAgreementStopsCovering() {
        AverageAgreement ending =
                new AverageAgreement("PWRGRN", 12000, LocalDate.of(2024, 7, 1), LocalDate.of(2025, 6, 30));
        assertThat(ending.covers(LocalDate.of(2025, 6, 30))).isTrue();
        assertThat(ending.covers(LocalDate.of(2025, 7, 1))).isFalse();
    }

    @Test
    void anAgreementThatExpiresBeforeItStartsIsRefused() {
        assertThatThrownBy(
                        () -> new AverageAgreement("PWRGRN", 12000, LocalDate.of(2025, 7, 1), LocalDate.of(2024, 7, 1)))
                .isInstanceOf(TariffError.class);
    }

    @Test
    void anAgreementThatPaysTheCustomerIsRefused() {
        assertThatThrownBy(() -> new AverageAgreement("PWRGRN", -1, LocalDate.of(2024, 7, 1)))
                .isInstanceOf(TariffError.class);
    }

    @Test
    void aCarTurnedBackOnTheFirstFreeDayEarnsACredit() {
        assertThat(Entry.of(aDetention()).credits()).isEqualTo(1);
    }

    @Test
    void aCarTurnedBackEarlyEarnsACredit() {
        Detention early = aDetention(0, LocalDate.of(2025, 1, 14));
        assertThat(Entry.of(early).credits()).isEqualTo(1);
    }

    @Test
    void aCarHeldIntoFreeTimeEarnsNothing() {
        Detention lazy = aDetention(0, LocalDate.of(2025, 1, 16));
        assertThat(Entry.of(lazy).credits()).isZero();
        assertThat(Entry.of(lazy).debits()).isZero();
    }

    @Test
    void aChargeableDayIsADebit() {
        assertThat(Entry.of(aDetention(3)).debits()).isEqualTo(3);
    }

    @Test
    void aCarNeverEarnsACreditAndADebitTogether() {
        assertThatThrownBy(() -> new Entry(CAR, "PWRGRN1", 1, 1)).isInstanceOf(TariffError.class);
    }

    @Test
    void anEntryPrintsItsCredit() {
        assertThat(new Entry(CAR, "PWRGRN1", 1, 0)).hasToString("CN 400100 1 credit");
    }

    @Test
    void anEntryPrintsItsDebits() {
        assertThat(new Entry(CAR, "PWRGRN1", 0, 3)).hasToString("CN 400100 3 debits");
    }

    @Test
    void aLedgerOutsideItsAgreementIsRefused() {
        assertThatThrownBy(() -> new AverageLedger(AGREEMENT, LocalDate.of(2024, 1, 1)))
                .isInstanceOf(TariffError.class);
    }

    @Test
    void anEmptyMonthSettlesAtNothing() {
        Settlement settled = january().settle();
        assertThat(settled.amount()).isEqualTo(new Money(0));
        assertThat(settled.credits()).isZero();
    }

    @Test
    void creditsCancelDebitsOneForOne() {
        AverageLedger ledger = january();
        ledger.post(aDetention());
        ledger.post(aDetention());
        ledger.post(aDetention(3));
        Settlement settled = ledger.settle();
        assertThat(settled.netDebits()).isEqualTo(1);
        assertThat(settled.amount()).isEqualTo(new Money(12000));
    }

    @Test
    void moreCreditsThanDebitsLeavesNothingOwed() {
        AverageLedger ledger = january();
        for (int car = 0; car < 5; car++) {
            ledger.post(aDetention());
        }
        ledger.post(aDetention(2));
        Settlement settled = ledger.settle();
        assertThat(settled.amount()).isEqualTo(new Money(0));
        assertThat(settled.netDebits()).isZero();
    }

    @Test
    void spareCreditsAreReportedAsWasted() {
        AverageLedger ledger = january();
        for (int car = 0; car < 5; car++) {
            ledger.post(aDetention());
        }
        ledger.post(aDetention(2));
        assertThat(ledger.settle().wastedCredits()).isEqualTo(3);
    }

    @Test
    void aMonthWithNoCreditsPaysEveryDebit() {
        AverageLedger ledger = january();
        ledger.post(aDetention(4));
        assertThat(ledger.settle().amount()).isEqualTo(new Money(48000));
    }

    @Test
    void theLedgerCountsWhatItPosted() {
        AverageLedger ledger = january();
        ledger.post(aDetention());
        ledger.post(aDetention(1));
        assertThat(ledger.size()).isEqualTo(2);
        assertThat(ledger.entries()).hasSize(2);
    }

    @Test
    void theLedgerTotalsBothSides() {
        AverageLedger ledger = january();
        ledger.post(aDetention());
        ledger.post(aDetention(3));
        assertThat(ledger.credits()).isEqualTo(1);
        assertThat(ledger.debits()).isEqualTo(3);
    }

    @Test
    void aSettlementPrintsBothSidesAndTheMoney() {
        AverageLedger ledger = january();
        ledger.post(aDetention());
        ledger.post(aDetention(3));
        assertThat(ledger.settle()).hasToString("PWRGRN 1 credits 3 debits $240.00");
    }

    @Test
    void aNewMonthStartsWithNoCredits() {
        AverageLedger first = january();
        for (int car = 0; car < 5; car++) {
            first.post(aDetention());
        }
        AverageLedger second = new AverageLedger(AGREEMENT, LocalDate.of(2025, 2, 1));
        second.post(aDetention(2));
        assertThat(second.settle().amount()).isEqualTo(new Money(24000));
    }
}
