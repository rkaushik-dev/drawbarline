package io.github.rkaushikdev.drawbarline.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.SettlementError;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class DisputeTest {

    static final ReportingMark CN = new ReportingMark("CN");
    static final ReportingMark ELS = new ReportingMark("ELS");
    static final Period JANUARY = new Period(2025, 1);

    static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    /** The fields a test dispute carries unless the test changes one. */
    static final class ADispute {
        int reference = 701;
        ReportingMark road = CN;
        Period period = JANUARY;
        Ground ground = Ground.HOURS;
        Money amount = new Money(45000);
        LocalDate raisedOn = date(2025, 2, 12);

        ADispute reference(int value) {
            reference = value;
            return this;
        }

        ADispute road(ReportingMark value) {
            road = value;
            return this;
        }

        ADispute period(Period value) {
            period = value;
            return this;
        }

        ADispute amount(Money value) {
            amount = value;
            return this;
        }

        ADispute raisedOn(LocalDate value) {
            raisedOn = value;
            return this;
        }

        Dispute build() {
            return new Dispute(reference, road, period, ground, amount, raisedOn);
        }
    }

    static ADispute a() {
        return new ADispute();
    }

    static ADispute a(int reference) {
        return new ADispute().reference(reference);
    }

    static Dispute aDispute() {
        return a().build();
    }

    @Test
    void aDisputePrintsTheRoadMonthAndAmount() {
        assertThat(aDispute()).hasToString("dispute 701 CN 2025-01 $450.00");
    }

    @Test
    void aDisputeNumberedBelowOneIsRefused() {
        assertThatThrownBy(() -> a(0).build()).isInstanceOf(SettlementError.class);
    }

    @Test
    void aDisputeForNothingIsRefused() {
        assertThatThrownBy(() -> a().amount(new Money(0)).build()).isInstanceOf(SettlementError.class);
    }

    @Test
    void aDisputeRaisedBeforeTheMonthEndedIsRefused() {
        assertThatThrownBy(() -> a().raisedOn(date(2025, 1, 15)).build()).isInstanceOf(SettlementError.class);
    }

    @Test
    void everyGroundHasReadableText() {
        assertThat(Arrays.stream(Ground.values()).map(Ground::text)).allMatch(text -> !text.isEmpty());
    }

    @Test
    void aDisputeCountsItsDays() {
        assertThat(aDispute().daysOld(date(2025, 3, 14))).isEqualTo(30);
    }

    @Test
    void aDateBeforeTheDisputeIsRefused() {
        Dispute dispute = aDispute();
        assertThatThrownBy(() -> dispute.daysOld(date(2025, 1, 1))).isInstanceOf(SettlementError.class);
    }

    @Test
    void thereAreFourBuckets() {
        assertThat(Aging.bucketLabels()).isEqualTo(List.of("1 to 30", "31 to 60", "61 to 90", "over 90"));
    }

    @Test
    void aFreshDisputeLandsInTheFirstBucket() {
        assertThat(Aging.bucketFor(0)).isEqualTo("1 to 30");
        assertThat(Aging.bucketFor(30)).isEqualTo("1 to 30");
    }

    @Test
    void aMonthOldDisputeLandsInTheSecond() {
        assertThat(Aging.bucketFor(31)).isEqualTo("31 to 60");
        assertThat(Aging.bucketFor(60)).isEqualTo("31 to 60");
    }

    @Test
    void aThreeMonthOldDisputeLandsInTheLast() {
        assertThat(Aging.bucketFor(90)).isEqualTo("over 90");
        assertThat(Aging.bucketFor(400)).isEqualTo("over 90");
    }

    @Test
    void aRaisedDisputeIsOpen() {
        DisputeRegister register = new DisputeRegister();
        register.raise(aDispute());
        assertThat(register.standingOf(701)).isSameAs(Standing.OPEN);
    }

    @Test
    void theSameReferenceTwiceIsRefused() {
        DisputeRegister register = new DisputeRegister();
        register.raise(aDispute());
        Dispute again = aDispute();
        assertThatThrownBy(() -> register.raise(again)).isInstanceOf(SettlementError.class);
    }

    @Test
    void anUnknownReferenceIsRefused() {
        assertThatThrownBy(() -> new DisputeRegister().standingOf(701)).isInstanceOf(SettlementError.class);
    }

    @Test
    void settlingClosesIt() {
        DisputeRegister register = new DisputeRegister();
        register.raise(aDispute());
        register.settle(701);
        assertThat(register.standingOf(701)).isSameAs(Standing.SETTLED);
        assertThat(register.openDisputes()).isEmpty();
    }

    @Test
    void withdrawingClosesItToo() {
        DisputeRegister register = new DisputeRegister();
        register.raise(aDispute());
        register.withdraw(701);
        assertThat(register.standingOf(701)).isSameAs(Standing.WITHDRAWN);
    }

    @Test
    void closingTwiceIsRefused() {
        DisputeRegister register = new DisputeRegister();
        register.raise(aDispute());
        register.settle(701);
        assertThatThrownBy(() -> register.withdraw(701)).isInstanceOf(SettlementError.class);
    }

    @Test
    void settlingSomethingNeverRaisedIsRefused() {
        assertThatThrownBy(() -> new DisputeRegister().settle(701)).isInstanceOf(SettlementError.class);
    }

    @Test
    void theOpenAmountAddsTheOpenOnes() {
        DisputeRegister register = new DisputeRegister();
        register.raise(aDispute());
        register.raise(a(702).amount(new Money(1000)).build());
        register.settle(702);
        assertThat(register.openAmount()).isEqualTo(new Money(45000));
    }

    @Test
    void disputesCanBeReadForOneRoad() {
        DisputeRegister register = new DisputeRegister();
        register.raise(aDispute());
        register.raise(a(702).road(ELS).build());
        assertThat(register.openDisputes(CN).stream().map(Dispute::reference)).containsExactly(701);
    }

    @Test
    void theAgingPutsEachDisputeInItsBucket() {
        DisputeRegister register = new DisputeRegister();
        register.raise(aDispute());
        register.raise(a(702).period(new Period(2025, 4))
                .raisedOn(date(2025, 5, 15))
                .amount(new Money(9000))
                .build());
        Aging aging = register.aging(date(2025, 6, 1));
        assertThat(aging.bucket("1 to 30")).isEqualTo(new Money(9000));
        assertThat(aging.bucket("over 90")).isEqualTo(new Money(45000));
        assertThat(aging.bucket("31 to 60")).isEqualTo(new Money(0));
    }

    @Test
    void theAgingTotalsTheOpenDisputes() {
        DisputeRegister register = new DisputeRegister();
        register.raise(aDispute());
        register.raise(a(702).amount(new Money(9000)).build());
        assertThat(register.aging(date(2025, 3, 1)).total()).isEqualTo(new Money(54000));
    }

    @Test
    void aSettledDisputeLeavesTheAging() {
        DisputeRegister register = new DisputeRegister();
        register.raise(aDispute());
        register.settle(701);
        assertThat(register.aging(date(2025, 3, 1)).total()).isEqualTo(new Money(0));
    }

    @Test
    void theOldestBucketReadsBackOnItsOwn() {
        DisputeRegister register = new DisputeRegister();
        register.raise(a().raisedOn(date(2025, 2, 1)).build());
        assertThat(register.aging(date(2025, 8, 1)).oldest()).isEqualTo(new Money(45000));
    }

    @Test
    void anAgingPrintsEveryBucketAndTheTotal() {
        DisputeRegister register = new DisputeRegister();
        register.raise(aDispute());
        String printed = register.aging(date(2025, 3, 1)).toString();
        assertThat(printed).startsWith("1 to 30 $450.00");
        assertThat(printed).endsWith("total $450.00");
    }

    @Test
    void aBucketNobodyHasIsRefused() {
        assertThat(new DisputeRegister().aging(date(2025, 3, 1)).total()).isEqualTo(new Money(0));
        Aging aging = new DisputeRegister().aging(date(2025, 3, 1));
        assertThatThrownBy(() -> aging.bucket("next year")).isInstanceOf(SettlementError.class);
    }

    @Test
    void theRegisterCountsEveryDisputeEverRaised() {
        DisputeRegister register = new DisputeRegister();
        register.raise(aDispute());
        register.raise(a(702).build());
        register.settle(701);
        assertThat(register.size()).isEqualTo(2);
    }

    @Test
    void anAgingWithNoBucketsHasNoOldestOne() {
        Aging aging = new Aging(List.of());
        assertThat(aging.total()).isEqualTo(new Money(0));
        assertThatThrownBy(aging::oldest).isInstanceOf(SettlementError.class);
    }
}
