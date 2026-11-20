package io.github.rkaushikdev.drawbarline.carhire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.RateError;
import io.github.rkaushikdev.drawbarline.fleet.CarTypeCode;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RatesTest {

    static final CarTypeCode HOPPER = new CarTypeCode("L351");
    static final CarTypeCode BOX = new CarTypeCode("X202");

    static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    static RateTable aTable() {
        RateTable table = new RateTable();
        table.add(new Band(HOPPER, 0, 9, date(2024, 7, 1), new Rate(4630, 1210)));
        table.add(new Band(HOPPER, 10, 19, date(2024, 7, 1), new Rate(3180, 1210)));
        table.add(new Band(HOPPER, 20, 99, date(2024, 7, 1), new Rate(1470, 1210)));
        table.add(new Band(HOPPER, 0, 9, date(2025, 7, 1), new Rate(4880, 1290)));
        table.add(new Band(BOX, 0, 99, date(2024, 7, 1), new Rate(2240, 980)));
        return table;
    }

    @Test
    void aRatePrintsBothHalves() {
        assertThat(new Rate(4630, 1210)).hasToString("463.0c an hour and 121.0c a mile");
    }

    @Test
    void aRateReadsInCents() {
        Rate rate = new Rate(4630, 1210);
        assertThat(rate.hourlyCents()).isEqualTo(463.0);
        assertThat(rate.mileageCents()).isEqualTo(121.0);
    }

    @Test
    void aNegativeHourlyRateIsRefused() {
        assertThatThrownBy(() -> new Rate(-1, 100)).isInstanceOf(RateError.class);
    }

    @Test
    void aNegativeMileageRateIsRefused() {
        assertThatThrownBy(() -> new Rate(100, -1)).isInstanceOf(RateError.class);
    }

    @Test
    void aFreeRateIsAllowed() {
        assertThat(new Rate(0, 0).hourlyCents()).isZero();
    }

    @Test
    void aBandCoversItsOwnAges() {
        Band band = new Band(HOPPER, 10, 19, date(2024, 7, 1), new Rate(3180, 1210));
        assertThat(band.coversAge(10)).isTrue();
        assertThat(band.coversAge(19)).isTrue();
        assertThat(band.coversAge(20)).isFalse();
    }

    @Test
    void aBandThatEndsBeforeItStartsIsRefused() {
        assertThatThrownBy(() -> new Band(HOPPER, 19, 10, date(2024, 7, 1), new Rate(1, 1)))
                .isInstanceOf(RateError.class);
    }

    @Test
    void aBandStartingBelowZeroIsRefused() {
        assertThatThrownBy(() -> new Band(HOPPER, -1, 10, date(2024, 7, 1), new Rate(1, 1)))
                .isInstanceOf(RateError.class);
    }

    @Test
    void aNewCarTakesTheTopRate() {
        assertThat(aTable().lookup(HOPPER, 3, date(2025, 1, 14))).isEqualTo(new Rate(4630, 1210));
    }

    @Test
    void anOlderCarTakesALowerRate() {
        assertThat(aTable().lookup(HOPPER, 25, date(2025, 1, 14))).isEqualTo(new Rate(1470, 1210));
    }

    @Test
    void theBandBoundaryBelongsToTheLowerBand() {
        assertThat(aTable().lookup(HOPPER, 9, date(2025, 1, 14))).isEqualTo(new Rate(4630, 1210));
        assertThat(aTable().lookup(HOPPER, 10, date(2025, 1, 14))).isEqualTo(new Rate(3180, 1210));
    }

    @Test
    void aRateFiledLaterTakesOverOnItsDate() {
        RateTable table = aTable();
        assertThat(table.lookup(HOPPER, 3, date(2025, 6, 30))).isEqualTo(new Rate(4630, 1210));
        assertThat(table.lookup(HOPPER, 3, date(2025, 7, 1))).isEqualTo(new Rate(4880, 1290));
    }

    @Test
    void aBandWithNoLaterFilingKeepsRunning() {
        assertThat(aTable().lookup(HOPPER, 25, date(2026, 1, 1))).isEqualTo(new Rate(1470, 1210));
    }

    @Test
    void aDateBeforeAnythingWasFiledHasNoRate() {
        assertThatThrownBy(() -> aTable().lookup(HOPPER, 3, date(2024, 1, 1))).isInstanceOf(RateError.class);
    }

    @Test
    void aCarTypeWithNoRateIsRefused() {
        assertThatThrownBy(() -> aTable().lookup(new CarTypeCode("T104"), 3, date(2025, 1, 14)))
                .isInstanceOf(RateError.class);
    }

    @Test
    void aNegativeAgeIsRefused() {
        assertThatThrownBy(() -> aTable().lookup(HOPPER, -1, date(2025, 1, 14))).isInstanceOf(RateError.class);
    }

    @Test
    void anAgePastEveryBandIsRefused() {
        RateTable table = new RateTable();
        table.add(new Band(BOX, 0, 40, date(2024, 7, 1), new Rate(2240, 980)));
        assertThatThrownBy(() -> table.lookup(BOX, 41, date(2025, 1, 14))).isInstanceOf(RateError.class);
    }

    @Test
    void twoBandsOverlappingOnTheSameDateAreRefused() {
        RateTable table = aTable();
        assertThatThrownBy(() -> table.add(new Band(HOPPER, 5, 15, date(2024, 7, 1), new Rate(1, 1))))
                .isInstanceOf(RateError.class);
    }

    @Test
    void theSameAgesOnADifferentDateAreFine() {
        RateTable table = aTable();
        table.add(new Band(HOPPER, 10, 19, date(2026, 1, 1), new Rate(3300, 1290)));
        assertThat(table.lookup(HOPPER, 12, date(2026, 2, 1))).isEqualTo(new Rate(3300, 1290));
    }

    @Test
    void theEffectiveDatesComeBackInOrder() {
        assertThat(aTable().effectiveDates(HOPPER)).containsExactly(date(2024, 7, 1), date(2025, 7, 1));
    }

    @Test
    void aTypeWithNoBandsHasNoEffectiveDates() {
        assertThat(aTable().effectiveDates(new CarTypeCode("T104"))).isEmpty();
    }

    @Test
    void aSpanReportsWhereTheRateSteps() {
        assertThat(aTable().changesBetween(HOPPER, date(2025, 6, 1), date(2025, 8, 1)))
                .containsExactly(date(2025, 7, 1));
    }

    @Test
    void aSpanWithNoStepReportsNothing() {
        assertThat(aTable().changesBetween(HOPPER, date(2025, 1, 1), date(2025, 6, 1)))
                .isEmpty();
    }

    @Test
    void aStepOnTheFirstDayOfTheSpanIsNotASplit() {
        assertThat(aTable().changesBetween(HOPPER, date(2025, 7, 1), date(2025, 8, 1)))
                .isEmpty();
    }

    @Test
    void aStepOnTheLastDayOfTheSpanIsASplit() {
        assertThat(aTable().changesBetween(HOPPER, date(2025, 6, 1), date(2025, 7, 1)))
                .containsExactly(date(2025, 7, 1));
    }

    @Test
    void aBackwardsSpanIsRefused() {
        assertThatThrownBy(() -> aTable().changesBetween(HOPPER, date(2025, 8, 1), date(2025, 6, 1)))
                .isInstanceOf(RateError.class);
    }

    @Test
    void theTableListsTheTypesItCovers() {
        assertThat(aTable().types()).containsExactly(HOPPER, BOX);
    }

    @Test
    void theTableCountsItsBands() {
        assertThat(aTable().size()).isEqualTo(5);
    }
}
