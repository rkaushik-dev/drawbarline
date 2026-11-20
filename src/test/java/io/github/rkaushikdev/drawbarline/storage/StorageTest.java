package io.github.rkaushikdev.drawbarline.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.TariffError;
import io.github.rkaushikdev.drawbarline.demurrage.Detention;
import io.github.rkaushikdev.drawbarline.demurrage.Step;
import io.github.rkaushikdev.drawbarline.demurrage.Tariff;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StorageTest {

    static final Tariff DEMURRAGE =
            new Tariff("4200", LocalDate.of(2024, 7, 1), 2, 1, List.of(new Step(2, 10000), new Step(0, 20000)));
    static final StorageTariff STORAGE = new StorageTariff("4500", LocalDate.of(2024, 7, 1), 5, 3500);

    static Detention aDetention(int chargeable) {
        return aDetention(chargeable, LocalDate.of(2025, 1, 22));
    }

    static Detention aDetention(int chargeable, LocalDate countedTo) {
        return new Detention(
                CarMark.parse("CN 400100"),
                "ESCPAP1",
                LocalDate.of(2025, 1, 14),
                LocalDate.of(2025, 1, 15),
                Optional.of(LocalDate.of(2025, 1, 16)),
                countedTo,
                chargeable,
                true,
                false);
    }

    /** A car held twenty chargeable days, into the middle of February. */
    static Detention aLongHold() {
        return aDetention(20, LocalDate.of(2025, 2, 14));
    }

    @Test
    void aStorageTariffPrintsItsTerms() {
        assertThat(STORAGE).hasToString("storage 4500 at $35.00 a day after 5 days");
    }

    @Test
    void aStorageTariffWithNoNumberIsRefused() {
        assertThatThrownBy(() -> new StorageTariff("  ", LocalDate.of(2024, 7, 1), 5, 3500))
                .isInstanceOf(TariffError.class);
    }

    @Test
    void storageStartingOnDayZeroIsRefused() {
        assertThatThrownBy(() -> new StorageTariff("4500", LocalDate.of(2024, 7, 1), 0, 3500))
                .isInstanceOf(TariffError.class);
    }

    @Test
    void aStorageRateThatPaysTheCustomerIsRefused() {
        assertThatThrownBy(() -> new StorageTariff("4500", LocalDate.of(2024, 7, 1), 5, -1))
                .isInstanceOf(TariffError.class);
    }

    @Test
    void aShortDetentionNeverConverts() {
        Split found = Storage.split(aDetention(3), STORAGE);
        assertThat(found.converted()).isFalse();
        assertThat(found.demurrageDays()).isEqualTo(3);
        assertThat(found.storageDays()).isZero();
    }

    @Test
    void aShortDetentionHasNoConversionDay() {
        assertThat(Storage.conversionDay(aDetention(3), STORAGE)).isEmpty();
    }

    @Test
    void aDetentionExactlyAtTheThresholdConverts() {
        Split found = Storage.split(aDetention(5, LocalDate.of(2025, 1, 24)), STORAGE);
        assertThat(found.demurrageDays()).isEqualTo(5);
        assertThat(found.convertedOn()).isPresent();
    }

    @Test
    void theConversionDayIsTheFifthChargeableDay() {
        assertThat(Storage.conversionDay(aDetention(5, LocalDate.of(2025, 1, 24)), STORAGE))
                .contains(LocalDate.of(2025, 1, 23));
    }

    @Test
    void theConversionWalksOverTheWeekend() {
        Detention held = aDetention(10, LocalDate.of(2025, 2, 3));
        assertThat(Storage.conversionDay(held, STORAGE)).contains(LocalDate.of(2025, 1, 23));
    }

    @Test
    void aLongDetentionBillsFiveDaysOfDemurrage() {
        assertThat(Storage.split(aLongHold(), STORAGE).demurrageDays()).isEqualTo(5);
    }

    @Test
    void storageCountsEveryDayAndNotOnlyChargeableOnes() {
        Split found = Storage.split(aLongHold(), STORAGE);
        assertThat(found.storageDays())
                .isEqualTo(ChronoUnit.DAYS.between(LocalDate.of(2025, 1, 23), LocalDate.of(2025, 2, 14)));
    }

    @Test
    void theDemurrageHalfIsPricedUpTheSteps() {
        Split found = Storage.split(aLongHold(), STORAGE);
        assertThat(found.demurrage(DEMURRAGE)).isEqualTo(new Money(10000 + 10000 + 20000 + 20000 + 20000));
    }

    @Test
    void theStorageHalfIsPricedFlat() {
        Split found = Storage.split(aLongHold(), STORAGE);
        assertThat(found.storage(STORAGE)).isEqualTo(new Money(3500L * found.storageDays()));
    }

    @Test
    void theTotalIsTheTwoHalves() {
        Split found = Storage.split(aLongHold(), STORAGE);
        assertThat(found.total(DEMURRAGE, STORAGE))
                .isEqualTo(found.demurrage(DEMURRAGE).plus(found.storage(STORAGE)));
    }

    @Test
    void theChargeHelperAgreesWithTheSplit() {
        Detention one = aLongHold();
        assertThat(Storage.charge(one, DEMURRAGE, STORAGE))
                .isEqualTo(Storage.split(one, STORAGE).total(DEMURRAGE, STORAGE));
    }

    @Test
    void aShortDetentionChargesOnlyDemurrage() {
        Detention one = aDetention(3);
        assertThat(Storage.charge(one, DEMURRAGE, STORAGE)).isEqualTo(DEMURRAGE.charge(3));
    }

    @Test
    void convertingMakesALongHoldCheaperThanTheTopStep() {
        Detention one = aLongHold();
        assertThat(Storage.charge(one, DEMURRAGE, STORAGE)).isLessThan(DEMURRAGE.charge(20));
    }

    @Test
    void aShortSplitPrintsAsDemurrageOnly() {
        assertThat(Storage.split(aDetention(3), STORAGE)).hasToString("CN 400100 3 days demurrage");
    }

    @Test
    void aConvertedSplitPrintsBothHalves() {
        assertThat(Storage.split(aLongHold(), STORAGE).toString()).contains("days storage from 2025-01-23");
    }

    @Test
    void aCarReleasedOnTheConversionDayStoresNothing() {
        Split found = Storage.split(aDetention(5, LocalDate.of(2025, 1, 23)), STORAGE);
        assertThat(found.storageDays()).isZero();
        assertThat(found.converted()).isFalse();
    }
}
