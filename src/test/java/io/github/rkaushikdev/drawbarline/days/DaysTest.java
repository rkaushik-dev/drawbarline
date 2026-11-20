package io.github.rkaushikdev.drawbarline.days;

import static io.github.rkaushikdev.drawbarline.days.Days.addChargeableDays;
import static io.github.rkaushikdev.drawbarline.days.Days.chargeableDays;
import static io.github.rkaushikdev.drawbarline.days.Days.holidayOn;
import static io.github.rkaushikdev.drawbarline.days.Days.holidays;
import static io.github.rkaushikdev.drawbarline.days.Days.isChargeable;
import static io.github.rkaushikdev.drawbarline.days.Days.isHoliday;
import static io.github.rkaushikdev.drawbarline.days.Days.isWeekend;
import static io.github.rkaushikdev.drawbarline.days.Days.nextChargeable;
import static io.github.rkaushikdev.drawbarline.days.Days.observed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DaysTest {

    static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    static Holiday named(int year, String name) {
        return holidays(year).stream()
                .filter(one -> one.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void sixHolidaysAYear() {
        assertThat(holidays(2025)).hasSize(6);
    }

    @Test
    void holidaysComeBackInTheOrderTheyAreKept() {
        assertThat(holidays(2025)).extracting(Holiday::kept).isSorted();
    }

    @Test
    void memorialDayIsTheLastMondayInMay() {
        assertThat(holidayOn(date(2025, 5, 26)).orElseThrow().name()).isEqualTo("memorial day");
    }

    @Test
    void laborDayIsTheFirstMondayInSeptember() {
        assertThat(holidayOn(date(2025, 9, 1)).orElseThrow().name()).isEqualTo("labor day");
    }

    @Test
    void thanksgivingIsTheFourthThursdayInNovember() {
        assertThat(holidayOn(date(2025, 11, 27)).orElseThrow().name()).isEqualTo("thanksgiving");
    }

    @Test
    void thanksgivingMovesWithTheYear() {
        assertThat(holidayOn(date(2026, 11, 26)).orElseThrow().name()).isEqualTo("thanksgiving");
    }

    @Test
    void memorialDayInAMayThatEndsOnAMonday() {
        assertThat(holidayOn(date(2027, 5, 31)).orElseThrow().name()).isEqualTo("memorial day");
    }

    @Test
    void aSaturdayHolidayIsKeptTheFridayBefore() {
        assertThat(observed(date(2026, 7, 4))).isEqualTo(date(2026, 7, 3));
    }

    @Test
    void aSundayHolidayIsKeptTheMondayAfter() {
        assertThat(observed(date(2022, 12, 25))).isEqualTo(date(2022, 12, 26));
    }

    @Test
    void aWeekdayHolidayStaysPut() {
        assertThat(observed(date(2025, 12, 25))).isEqualTo(date(2025, 12, 25));
    }

    @Test
    void aMovedHolidaySaysSo() {
        Holiday fourth = named(2026, "independence day");
        assertThat(fourth.moved()).isTrue();
        assertThat(fourth.falls()).isEqualTo(date(2026, 7, 4));
        assertThat(fourth.kept()).isEqualTo(date(2026, 7, 3));
    }

    @Test
    void aHolidayThatDidNotMoveSaysSo() {
        assertThat(named(2025, "independence day").moved()).isFalse();
    }

    @Test
    void anOrdinaryDayIsNoHoliday() {
        assertThat(holidayOn(date(2025, 3, 12))).isEmpty();
        assertThat(isHoliday(date(2025, 3, 12))).isFalse();
    }

    @Test
    void aYearWeWouldNeverSettleIsRefused() {
        assertThatThrownBy(() -> holidays(1700)).isInstanceOf(CalendarError.class);
    }

    @Test
    void saturdaysAndSundaysAreTheWeekend() {
        assertThat(isWeekend(date(2025, 3, 15))).isTrue();
        assertThat(isWeekend(date(2025, 3, 16))).isTrue();
        assertThat(isWeekend(date(2025, 3, 17))).isFalse();
    }

    @Test
    void aWeekdayThatIsNotAHolidayIsChargeable() {
        assertThat(isChargeable(date(2025, 3, 12))).isTrue();
    }

    @Test
    void aHolidayIsNotChargeable() {
        assertThat(isChargeable(date(2025, 12, 25))).isFalse();
    }

    @Test
    void aWeekendIsNotChargeable() {
        assertThat(isChargeable(date(2025, 3, 15))).isFalse();
    }

    @Test
    void anOrdinaryWeekHoldsFiveChargeableDays() {
        assertThat(chargeableDays(date(2025, 3, 10), date(2025, 3, 16))).isEqualTo(5);
    }

    @Test
    void aWeekWithAHolidayHoldsFour() {
        assertThat(chargeableDays(date(2025, 12, 22), date(2025, 12, 28))).isEqualTo(4);
    }

    @Test
    void oneChargeableDayCountsItself() {
        assertThat(chargeableDays(date(2025, 3, 12), date(2025, 3, 12))).isEqualTo(1);
    }

    @Test
    void aSpanOfOneWeekendDayCountsNothing() {
        assertThat(chargeableDays(date(2025, 3, 15), date(2025, 3, 15))).isZero();
    }

    @Test
    void aSpanThatRunsBackwardsIsRefused() {
        assertThatThrownBy(() -> chargeableDays(date(2025, 3, 16), date(2025, 3, 10)))
                .isInstanceOf(CalendarError.class);
    }

    @Test
    void theNextChargeableDayAfterAFridayIsMonday() {
        assertThat(nextChargeable(date(2025, 3, 14))).isEqualTo(date(2025, 3, 17));
    }

    @Test
    void theNextChargeableDaySkipsAHolidayWeekend() {
        assertThat(nextChargeable(date(2025, 8, 29))).isEqualTo(date(2025, 9, 2));
    }

    @Test
    void freeTimeWalksForwardOverTheWeekend() {
        assertThat(addChargeableDays(date(2025, 3, 13), 2)).isEqualTo(date(2025, 3, 17));
    }

    @Test
    void noFreeTimeExpiresOnTheDayItStarts() {
        assertThat(addChargeableDays(date(2025, 3, 13), 0)).isEqualTo(date(2025, 3, 13));
    }

    @Test
    void freeTimeDoesNotRunBackwards() {
        assertThatThrownBy(() -> addChargeableDays(date(2025, 3, 13), -1)).isInstanceOf(CalendarError.class);
    }

    @Test
    void freeTimeCanCrossAYearEnd() {
        assertThat(addChargeableDays(date(2025, 12, 30), 3)).isEqualTo(date(2026, 1, 5));
    }
}
