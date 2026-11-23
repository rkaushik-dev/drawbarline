package io.github.rkaushikdev.drawbarline.days;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TariffCalendarTest {

    static final LocalDate TUESDAY = LocalDate.of(2025, 1, 14);
    static final LocalDate WEDNESDAY = LocalDate.of(2025, 1, 15);

    /** The calendar after a blizzard shut the yard on a Wednesday. */
    static TariffCalendar snowedIn() {
        return TariffCalendar.STANDARD.declaring(WEDNESDAY, "blizzard, escanaba yard closed");
    }

    @Test
    void theStandardCalendarIsTheWeekendsAndTheHolidays() {
        assertThat(TariffCalendar.STANDARD.isChargeable(TUESDAY)).isTrue();
        assertThat(TariffCalendar.STANDARD.isChargeable(LocalDate.of(2025, 1, 18)))
                .isFalse();
        assertThat(TariffCalendar.STANDARD.isChargeable(LocalDate.of(2025, 1, 1)))
                .isFalse();
        assertThat(TariffCalendar.STANDARD.declared()).isEmpty();
    }

    @Test
    void aDeclaredDayIsNotChargeable() {
        TariffCalendar found = snowedIn();
        assertThat(found.isChargeable(WEDNESDAY)).isFalse();
        assertThat(found.declaredOn(WEDNESDAY)).contains("blizzard, escanaba yard closed");
        assertThat(found.declaredOn(TUESDAY)).isEmpty();
        assertThat(found.chargeableDays(TUESDAY, LocalDate.of(2025, 1, 17))).isEqualTo(3);
        assertThat(TariffCalendar.STANDARD.chargeableDays(TUESDAY, LocalDate.of(2025, 1, 17)))
                .isEqualTo(4);
    }

    @Test
    void theClockStepsOverADeclaredDay() {
        assertThat(snowedIn().nextChargeable(TUESDAY)).isEqualTo(LocalDate.of(2025, 1, 16));
        assertThat(snowedIn().addChargeableDays(TUESDAY, 3)).isEqualTo(LocalDate.of(2025, 1, 20));
        assertThat(Days.addChargeableDays(TUESDAY, 3)).isEqualTo(LocalDate.of(2025, 1, 17));
    }

    @Test
    void declaringADayLeavesTheStandardCalendarAlone() {
        snowedIn();
        assertThat(TariffCalendar.STANDARD.isChargeable(WEDNESDAY)).isTrue();
        assertThat(Days.isChargeable(WEDNESDAY)).isTrue();
    }

    @Test
    void aDeclaredDayCarriesItsReasonAndIsDeclaredOnce() {
        assertThatThrownBy(() -> TariffCalendar.STANDARD.declaring(WEDNESDAY, " "))
                .isInstanceOf(CalendarError.class);
        assertThatThrownBy(() -> snowedIn().declaring(WEDNESDAY, "again")).isInstanceOf(CalendarError.class);
        TariffCalendar twice = snowedIn().declaring(LocalDate.of(2025, 1, 16), "still snowing");
        assertThat(twice.declared()).containsOnlyKeys(WEDNESDAY, LocalDate.of(2025, 1, 16));
    }

    @Test
    void theCalendarRefusesWhatTheStandardOneRefuses() {
        assertThatThrownBy(() -> snowedIn().chargeableDays(WEDNESDAY, TUESDAY)).isInstanceOf(CalendarError.class);
        assertThatThrownBy(() -> snowedIn().addChargeableDays(TUESDAY, -1)).isInstanceOf(CalendarError.class);
    }

    static final LocalDate THANKSGIVING_FRIDAY = LocalDate.of(2025, 11, 28);
    static final LocalDate CHRISTMAS_EVE = LocalDate.of(2025, 12, 24);

    @Test
    void theStandardTariffChargesTheDayAfterThanksgivingAndChristmasEve() {
        assertThat(TariffCalendar.STANDARD.isChargeable(THANKSGIVING_FRIDAY)).isTrue();
        assertThat(TariffCalendar.STANDARD.isChargeable(CHRISTMAS_EVE)).isTrue();
        assertThat(TariffCalendar.STANDARD.holidays())
                .isEqualTo(HolidayRule.STANDARD)
                .hasSize(6);
    }

    @Test
    void aTariffThatPublishesThemDoesNot() {
        TariffCalendar found = TariffCalendar.STANDARD
                .recognising(HolidayRule.DAY_AFTER_THANKSGIVING)
                .recognising(HolidayRule.CHRISTMAS_EVE);
        assertThat(found.isChargeable(THANKSGIVING_FRIDAY)).isFalse();
        assertThat(found.holidayOn(CHRISTMAS_EVE).orElseThrow().name()).isEqualTo("christmas eve");
        assertThat(found.chargeableDays(LocalDate.of(2025, 11, 26), LocalDate.of(2025, 12, 1)))
                .isEqualTo(2);
        assertThat(TariffCalendar.STANDARD.chargeableDays(LocalDate.of(2025, 11, 26), LocalDate.of(2025, 12, 1)))
                .isEqualTo(3);
    }

    @Test
    void recognisingAHolidayKeepsTheDeclaredDays() {
        TariffCalendar found = snowedIn().recognising(HolidayRule.CHRISTMAS_EVE);
        assertThat(found.declaredOn(WEDNESDAY)).isPresent();
        assertThat(found.isChargeable(WEDNESDAY)).isFalse();
        assertThat(snowedIn().holidays()).doesNotContain(HolidayRule.CHRISTMAS_EVE);
    }

    @Test
    void eachRuleFallsWhereTheCalendarSays() {
        assertThat(HolidayRule.DAY_AFTER_THANKSGIVING.in(2025).kept()).isEqualTo(THANKSGIVING_FRIDAY);
        assertThat(HolidayRule.CHRISTMAS_EVE.in(2022).kept()).isEqualTo(LocalDate.of(2022, 12, 24));
        assertThat(HolidayRule.CHRISTMAS.in(2022).kept()).isEqualTo(LocalDate.of(2022, 12, 26));
        assertThat(HolidayRule.INDEPENDENCE_DAY.in(2026).kept()).isEqualTo(LocalDate.of(2026, 7, 3));
        assertThat(HolidayRule.LABOR_DAY.text()).isEqualTo("labor day");
        assertThat(Days.holidays(2025, Set.of(HolidayRule.CHRISTMAS_EVE, HolidayRule.NEW_YEAR)))
                .extracting(Holiday::name)
                .containsExactly("new year", "christmas eve");
    }
}
