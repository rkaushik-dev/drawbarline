package io.github.rkaushikdev.drawbarline.days;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
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
}
