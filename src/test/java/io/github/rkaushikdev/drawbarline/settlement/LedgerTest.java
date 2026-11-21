package io.github.rkaushikdev.drawbarline.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.SettlementError;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LedgerTest {

    static final ReportingMark CN = new ReportingMark("CN");
    static final ReportingMark ELS = new ReportingMark("ELS");
    static final Period JANUARY = new Period(2025, 1);
    static final Period FEBRUARY = new Period(2025, 2);

    static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    /** The fields a test posting carries unless the test changes one. */
    static final class APosting {
        ReportingMark road = CN;
        Period period = JANUARY;
        PostingKind kind = PostingKind.CAR_HIRE;
        Money amount = new Money(120000);
        LocalDate on = date(2025, 1, 31);
        String reference = "cn january";

        APosting road(ReportingMark value) {
            road = value;
            return this;
        }

        APosting period(Period value) {
            period = value;
            return this;
        }

        APosting kind(PostingKind value) {
            kind = value;
            return this;
        }

        APosting amount(Money value) {
            amount = value;
            return this;
        }

        APosting on(LocalDate value) {
            on = value;
            return this;
        }

        Posting build() {
            return new Posting(road, period, kind, amount, on, reference);
        }
    }

    static APosting a() {
        return new APosting();
    }

    static Posting aPosting() {
        return a().build();
    }

    @Test
    void aPostingWeOwePrintsAsGoingOut() {
        assertThat(aPosting()).hasToString("2025-01 car hire to CN $1,200.00");
    }

    @Test
    void aPostingTheyOwePrintsAsComingIn() {
        assertThat(a().amount(new Money(-5000)).build()).hasToString("2025-01 car hire from CN $50.00");
    }

    @Test
    void theSignSaysWhichWayTheMoneyRuns() {
        assertThat(aPosting().weOwe()).isTrue();
        assertThat(a().amount(new Money(-1)).build().theyOwe()).isTrue();
    }

    @Test
    void aPostingDatedOutsideItsMonthIsRefused() {
        assertThatThrownBy(() -> a().on(date(2025, 2, 3)).build()).isInstanceOf(SettlementError.class);
    }

    @Test
    void anAdjustmentMayBeDatedLaterThanItsMonth() {
        Posting found = a().kind(PostingKind.ADJUSTMENT).on(date(2025, 4, 3)).build();
        assertThat(found.period()).isEqualTo(JANUARY);
    }

    @Test
    void everyKindHasReadableText() {
        assertThat(Arrays.stream(PostingKind.values()).map(PostingKind::text)).allMatch(text -> !text.isEmpty());
    }

    @Test
    void anAccountTakesOnlyItsOwnRoad() {
        Account account = new Account(CN);
        Posting other = a().road(ELS).build();
        assertThatThrownBy(() -> account.post(other)).isInstanceOf(SettlementError.class);
    }

    @Test
    void anEmptyAccountBalancesAtNothing() {
        assertThat(new Account(CN).balance()).isEqualTo(new Money(0));
    }

    @Test
    void postingsAddUpWithTheirSigns() {
        Account account = new Account(CN);
        account.post(aPosting());
        account.post(a().kind(PostingKind.REPAIRS).amount(new Money(-45000)).build());
        assertThat(account.balance()).isEqualTo(new Money(75000));
    }

    @Test
    void aBalanceCanBeAskedForOneMonth() {
        Account account = new Account(CN);
        account.post(aPosting());
        account.post(a().period(FEBRUARY)
                .on(date(2025, 2, 28))
                .amount(new Money(9000))
                .build());
        assertThat(account.balance(JANUARY)).isEqualTo(new Money(120000));
        assertThat(account.balance()).isEqualTo(new Money(129000));
    }

    @Test
    void aClosingBalanceRunsThroughAMonth() {
        Account account = new Account(CN);
        account.post(aPosting());
        account.post(a().period(FEBRUARY)
                .on(date(2025, 2, 28))
                .amount(new Money(9000))
                .build());
        assertThat(account.balanceThrough(JANUARY)).isEqualTo(new Money(120000));
        assertThat(account.balanceThrough(FEBRUARY)).isEqualTo(new Money(129000));
    }

    @Test
    void anAccountSplitsByKind() {
        Account account = new Account(CN);
        account.post(aPosting());
        account.post(a().kind(PostingKind.MILEAGE).amount(new Money(3000)).build());
        Map<PostingKind, Money> found = account.byKind();
        assertThat(found.get(PostingKind.CAR_HIRE)).isEqualTo(new Money(120000));
        assertThat(found.get(PostingKind.MILEAGE)).isEqualTo(new Money(3000));
    }

    @Test
    void postingsComeBackInMonthAndDateOrder() {
        Account account = new Account(CN);
        account.post(a().period(FEBRUARY).on(date(2025, 2, 28)).build());
        account.post(a().on(date(2025, 1, 3)).build());
        account.post(aPosting());
        assertThat(account.postings().stream().map(Posting::on))
                .containsExactly(date(2025, 1, 3), date(2025, 1, 31), date(2025, 2, 28));
    }

    @Test
    void aLedgerOpensAnAccountWhenItNeedsOne() {
        Ledger ledger = new Ledger();
        ledger.post(aPosting());
        assertThat(ledger.roads()).isEqualTo(List.of(CN));
        assertThat(ledger.size()).isEqualTo(1);
    }

    @Test
    void aRoadWithNothingPostedReadsAsAnEmptyAccount() {
        assertThat(new Ledger().account(ELS).balance()).isEqualTo(new Money(0));
    }

    @Test
    void aPostingIntoAClosedMonthIsRefused() {
        PeriodRegister periods = new PeriodRegister();
        periods.close(JANUARY);
        Ledger ledger = new Ledger(periods);
        Posting posting = aPosting();
        assertThatThrownBy(() -> ledger.post(posting)).isInstanceOf(SettlementError.class);
    }

    @Test
    void aPostingIntoAMonthReopenedIsAllowedAgain() {
        PeriodRegister periods = new PeriodRegister();
        periods.close(JANUARY);
        periods.reopen(JANUARY, date(2025, 3, 1), "corrected sheet from cn");
        Ledger ledger = new Ledger(periods);
        assertThat(ledger.post(aPosting()).amount()).isEqualTo(new Money(120000));
    }

    @Test
    void theNetIsOneNumberARoad() {
        Ledger ledger = new Ledger();
        ledger.post(aPosting());
        ledger.post(a().kind(PostingKind.REPAIRS).amount(new Money(-45000)).build());
        ledger.post(a().road(ELS).amount(new Money(-8000)).build());
        assertThat(ledger.net()).isEqualTo(Map.of(CN, new Money(75000), ELS, new Money(-8000)));
    }

    @Test
    void theTwoDirectionsAreReportedApart() {
        Ledger ledger = new Ledger();
        ledger.post(aPosting());
        ledger.post(a().road(ELS).amount(new Money(-8000)).build());
        assertThat(ledger.weOwe()).isEqualTo(new Money(120000));
        assertThat(ledger.owedToUs()).isEqualTo(new Money(8000));
    }

    @Test
    void thePositionIsTheTwoDirectionsTogether() {
        Ledger ledger = new Ledger();
        ledger.post(aPosting());
        ledger.post(a().road(ELS).amount(new Money(-8000)).build());
        assertThat(ledger.position()).isEqualTo(new Money(112000));
    }

    @Test
    void aMonthCanBeNettedOnItsOwn() {
        Ledger ledger = new Ledger();
        ledger.post(aPosting());
        ledger.post(a().period(FEBRUARY)
                .on(date(2025, 2, 28))
                .amount(new Money(500))
                .build());
        assertThat(ledger.net(JANUARY)).isEqualTo(Map.of(CN, new Money(120000)));
    }

    @Test
    void aPaymentMovesTheBalanceTheOtherWay() {
        Ledger ledger = new Ledger();
        ledger.post(aPosting());
        ledger.post(a().kind(PostingKind.PAYMENT).amount(new Money(-120000)).build());
        assertThat(ledger.net().get(CN)).isEqualTo(new Money(0));
    }

    @Test
    void anEmptyLedgerIsSquare() {
        Ledger ledger = new Ledger();
        assertThat(ledger.position()).isEqualTo(new Money(0));
        assertThat(ledger.roads()).isEmpty();
    }
}
