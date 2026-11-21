package io.github.rkaushikdev.drawbarline.settlement;

import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.SettlementError;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PeriodTest {

    static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    @Test
    void aPeriodPrintsAsAYearAndMonth() {
        assertThat(new Period(2025, 1)).hasToString("2025-01");
    }

    @Test
    void aPeriodReadsOffADate() {
        assertThat(Period.of(date(2025, 3, 17))).isEqualTo(new Period(2025, 3));
    }

    @Test
    void aMonthThatIsNotAMonthIsRefused() {
        assertThatThrownBy(() -> new Period(2025, 13)).isInstanceOf(SettlementError.class);
    }

    @Test
    void aYearWeWouldNeverSettleIsRefused() {
        assertThatThrownBy(() -> new Period(1700, 1)).isInstanceOf(SettlementError.class);
    }

    @Test
    void aPeriodKnowsItsFirstAndLastDay() {
        Period period = new Period(2025, 1);
        assertThat(period.firstDay()).isEqualTo(date(2025, 1, 1));
        assertThat(period.lastDay()).isEqualTo(date(2025, 1, 31));
    }

    @Test
    void februaryEndsWhenFebruaryEnds() {
        assertThat(new Period(2025, 2).lastDay()).isEqualTo(date(2025, 2, 28));
    }

    @Test
    void aLeapFebruaryHasItsExtraDay() {
        assertThat(new Period(2024, 2).lastDay()).isEqualTo(date(2024, 2, 29));
    }

    @Test
    void decemberRunsIntoJanuary() {
        assertThat(new Period(2025, 12).following()).isEqualTo(new Period(2026, 1));
    }

    @Test
    void januaryRunsBackIntoDecember() {
        assertThat(new Period(2025, 1).preceding()).isEqualTo(new Period(2024, 12));
    }

    @Test
    void theWindowStartsAtMidnightOnTheFirst() {
        assertThat(new Period(2025, 1).window().start()).isEqualTo(utc(2025, 1, 1, 6));
    }

    @Test
    void theWindowEndsWhereTheNextOneStarts() {
        assertThat(new Period(2025, 1).window().end())
                .isEqualTo(new Period(2025, 2).window().start());
    }

    @Test
    void aSummerWindowMovesWithTheClocks() {
        assertThat(new Period(2025, 7).window().start()).isEqualTo(utc(2025, 7, 1, 5));
    }

    @Test
    void aPeriodCoversItsOwnDays() {
        Period period = new Period(2025, 1);
        assertThat(period.covers(date(2025, 1, 1))).isTrue();
        assertThat(period.covers(date(2025, 1, 31))).isTrue();
        assertThat(period.covers(date(2025, 2, 1))).isFalse();
    }

    @Test
    void periodsSortInOrder() {
        assertThat(List.of(new Period(2025, 3), new Period(2024, 12), new Period(2025, 1)).stream()
                        .sorted()
                        .toList())
                .containsExactly(new Period(2024, 12), new Period(2025, 1), new Period(2025, 3));
    }

    @Test
    void aMonthStartsOpen() {
        assertThat(new PeriodRegister().isOpen(new Period(2025, 1))).isTrue();
    }

    @Test
    void closingAMonthShutsIt() {
        PeriodRegister register = new PeriodRegister();
        register.close(new Period(2025, 1));
        assertThat(register.isOpen(new Period(2025, 1))).isFalse();
    }

    @Test
    void closingAMonthTwiceIsRefused() {
        PeriodRegister register = new PeriodRegister();
        register.close(new Period(2025, 1));
        assertThatThrownBy(() -> register.close(new Period(2025, 1))).isInstanceOf(SettlementError.class);
    }

    @Test
    void postingToAnOpenMonthIsAllowed() {
        assertThat(new PeriodRegister().requireOpen(new Period(2025, 1))).isEqualTo(new Period(2025, 1));
    }

    @Test
    void postingToAClosedMonthIsRefused() {
        PeriodRegister register = new PeriodRegister();
        register.close(new Period(2025, 1));
        assertThatThrownBy(() -> register.requireOpen(new Period(2025, 1))).isInstanceOf(SettlementError.class);
    }

    @Test
    void reopeningAClosedMonthOpensIt() {
        PeriodRegister register = new PeriodRegister();
        register.close(new Period(2025, 1));
        register.reopen(new Period(2025, 1), date(2025, 3, 4), "cn sent a corrected sheet");
        assertThat(register.isOpen(new Period(2025, 1))).isTrue();
    }

    @Test
    void reopeningAMonthThatIsOpenIsRefused() {
        assertThatThrownBy(() -> new PeriodRegister().reopen(new Period(2025, 1), date(2025, 3, 4), "no reason"))
                .isInstanceOf(SettlementError.class);
    }

    @Test
    void reopeningWithoutAReasonIsRefused() {
        PeriodRegister register = new PeriodRegister();
        register.close(new Period(2025, 1));
        assertThatThrownBy(() -> register.reopen(new Period(2025, 1), date(2025, 3, 4), "   "))
                .isInstanceOf(SettlementError.class);
    }

    @Test
    void aReopeningStaysOnFileAfterTheMonthClosesAgain() {
        PeriodRegister register = new PeriodRegister();
        register.close(new Period(2025, 1));
        register.reopen(new Period(2025, 1), date(2025, 3, 4), "cn sent a corrected sheet");
        register.close(new Period(2025, 1));
        assertThat(register.wasEverReopened(new Period(2025, 1))).isTrue();
    }

    @Test
    void aMonthNeverReopenedSaysSo() {
        PeriodRegister register = new PeriodRegister();
        register.close(new Period(2025, 1));
        assertThat(register.wasEverReopened(new Period(2025, 1))).isFalse();
    }

    @Test
    void theReopeningsForOneMonthComeBackAlone() {
        PeriodRegister register = new PeriodRegister();
        for (Period period : List.of(new Period(2025, 1), new Period(2025, 2))) {
            register.close(period);
            register.reopen(period, date(2025, 4, 1), "late sheet");
        }
        assertThat(register.reopenings(new Period(2025, 1))).hasSize(1);
        assertThat(register.reopenings()).hasSize(2);
    }

    @Test
    void theClosedMonthsComeBackInOrder() {
        PeriodRegister register = new PeriodRegister();
        register.close(new Period(2025, 3));
        register.close(new Period(2025, 1));
        assertThat(register.closedPeriods()).containsExactly(new Period(2025, 1), new Period(2025, 3));
    }

    @Test
    void aReopeningCarriesItsReason() {
        Reopening found = new Reopening(new Period(2025, 1), date(2025, 3, 4), "corrected interchange sheet");
        assertThat(found.reason()).isEqualTo("corrected interchange sheet");
    }
}
