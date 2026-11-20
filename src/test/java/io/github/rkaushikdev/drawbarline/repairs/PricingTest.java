package io.github.rkaushikdev.drawbarline.repairs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.BillingError;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PricingTest {

    static final JobCode WHEELSET = new JobCode("4312");
    static final JobCode COUPLER = new JobCode("5104");
    static final ReportingMark CN = new ReportingMark("CN");

    static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    static PriceMaster aMaster() {
        PriceMaster master = new PriceMaster();
        master.add(new PriceLine(WHEELSET, date(2024, 7, 1), 250, 128000));
        master.add(new PriceLine(WHEELSET, date(2025, 7, 1), 250, 139000));
        master.add(new PriceLine(COUPLER, date(2024, 7, 1), 125, 84000));
        master.depreciateFamily(new Depreciation("43", 4, 20));
        return master;
    }

    @Test
    void aPriceLinePrintsItsHoursAndMaterial() {
        PriceLine line = new PriceLine(WHEELSET, date(2024, 7, 1), 250, 128000);
        assertThat(line).hasToString("4312 2.50 hours and $1,280.00");
    }

    @Test
    void negativeLaborIsRefused() {
        assertThatThrownBy(() -> new PriceLine(WHEELSET, date(2024, 7, 1), -1, 100))
                .isInstanceOf(BillingError.class);
    }

    @Test
    void negativeMaterialIsRefused() {
        assertThatThrownBy(() -> new PriceLine(WHEELSET, date(2024, 7, 1), 100, -1))
                .isInstanceOf(BillingError.class);
    }

    @Test
    void theMasterHandsBackThePriceInForce() {
        PriceMaster master = aMaster();
        assertThat(master.lineFor(WHEELSET, date(2025, 1, 14)).material()).isEqualTo(new Money(128000));
        assertThat(master.lineFor(WHEELSET, date(2025, 7, 1)).material()).isEqualTo(new Money(139000));
    }

    @Test
    void aJobWithNoPriceYetIsRefused() {
        assertThatThrownBy(() -> aMaster().lineFor(WHEELSET, date(2024, 1, 1))).isInstanceOf(BillingError.class);
    }

    @Test
    void aJobNobodyPricedIsRefused() {
        assertThatThrownBy(() -> aMaster().lineFor(new JobCode("9999"), date(2025, 1, 14)))
                .isInstanceOf(BillingError.class);
    }

    @Test
    void pricingTheSameJobTwiceFromOneDateIsRefused() {
        PriceMaster master = aMaster();
        assertThatThrownBy(() -> master.add(new PriceLine(WHEELSET, date(2024, 7, 1), 250, 1)))
                .isInstanceOf(BillingError.class);
    }

    @Test
    void theMasterCountsItsLines() {
        assertThat(aMaster().size()).isEqualTo(3);
    }

    @Test
    void aNewPartIsPaidInFull() {
        assertThat(new Depreciation("43", 4, 20).payablePercent(0)).isEqualTo(100);
    }

    @Test
    void aPartLosesItsPercentageAYear() {
        assertThat(new Depreciation("43", 4, 20).payablePercent(5)).isEqualTo(80);
    }

    @Test
    void depreciationStopsAtTheFloor() {
        assertThat(new Depreciation("43", 4, 20).payablePercent(40)).isEqualTo(20);
    }

    @Test
    void aVeryOldPartNeverGoesBelowTheFloor() {
        assertThat(new Depreciation("43", 4, 20).payablePercent(99)).isEqualTo(20);
    }

    @Test
    void aNegativeAgeIsRefused() {
        assertThatThrownBy(() -> new Depreciation("43", 4, 20).payablePercent(-1))
                .isInstanceOf(BillingError.class);
    }

    @Test
    void aPercentageOverAHundredIsRefused() {
        assertThatThrownBy(() -> new Depreciation("43", 140, 20)).isInstanceOf(BillingError.class);
    }

    @Test
    void aFloorOverAHundredIsRefused() {
        assertThatThrownBy(() -> new Depreciation("43", 4, 140)).isInstanceOf(BillingError.class);
    }

    @Test
    void depreciationComesOffTheMaterial() {
        assertThat(new Depreciation("43", 4, 20).applied(new Money(128000), 5)).isEqualTo(new Money(102400));
    }

    @Test
    void aFamilyWithNoRuleIsPaidInFull() {
        assertThat(aMaster().materialFor(COUPLER, date(2025, 1, 14), 20)).isEqualTo(new Money(84000));
    }

    @Test
    void aFamilyWithARuleIsDepreciated() {
        assertThat(aMaster().materialFor(WHEELSET, date(2025, 1, 14), 5)).isEqualTo(new Money(102400));
    }

    @Test
    void depreciationFollowsThePriceInForce() {
        assertThat(aMaster().materialFor(WHEELSET, date(2025, 7, 1), 5)).isEqualTo(new Money(111200));
    }

    @Test
    void theRuleForAJobReadsBackOffItsFamily() {
        assertThat(aMaster().depreciationFor(WHEELSET).orElseThrow().family()).isEqualTo("43");
        assertThat(aMaster().depreciationFor(COUPLER)).isEmpty();
    }

    @Test
    void aLaborRatePricesTheHours() {
        LaborRate rate = new LaborRate(CN, date(2024, 7, 1), 4200);
        assertThat(rate.costOf(250)).isEqualTo(new Money(10500));
    }

    @Test
    void aPartHourIsPricedProRata() {
        LaborRate rate = new LaborRate(CN, date(2024, 7, 1), 4200);
        assertThat(rate.costOf(50)).isEqualTo(new Money(2100));
    }

    @Test
    void aNegativeLaborRateIsRefused() {
        assertThatThrownBy(() -> new LaborRate(CN, date(2024, 7, 1), -1)).isInstanceOf(BillingError.class);
    }

    @Test
    void theBookHandsBackTheRateInForce() {
        LaborRates rates = new LaborRates();
        rates.file(new LaborRate(CN, date(2024, 7, 1), 4200));
        rates.file(new LaborRate(CN, date(2025, 7, 1), 4550));
        assertThat(rates.rateFor(CN, date(2025, 1, 14)).centsAnHour()).isEqualTo(4200);
        assertThat(rates.rateFor(CN, date(2025, 7, 1)).centsAnHour()).isEqualTo(4550);
    }

    @Test
    void aRoadWithNoRateFiledIsRefused() {
        assertThatThrownBy(() -> new LaborRates().rateFor(CN, date(2025, 1, 14)))
                .isInstanceOf(BillingError.class);
    }

    @Test
    void aRoadFilingTwiceOnOneDayIsRefused() {
        LaborRates rates = new LaborRates();
        rates.file(new LaborRate(CN, date(2024, 7, 1), 4200));
        assertThatThrownBy(() -> rates.file(new LaborRate(CN, date(2024, 7, 1), 4300)))
                .isInstanceOf(BillingError.class);
    }

    @Test
    void theBookListsTheRoadsThatHaveFiled() {
        LaborRates rates = new LaborRates();
        rates.file(new LaborRate(CN, date(2024, 7, 1), 4200));
        rates.file(new LaborRate(new ReportingMark("ELS"), date(2024, 7, 1), 3900));
        assertThat(rates.roads()).extracting(ReportingMark::toString).containsExactly("CN", "ELS");
    }
}
