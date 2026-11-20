package io.github.rkaushikdev.drawbarline.storage;

import io.github.rkaushikdev.drawbarline.demurrage.Tariff;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * One detention divided between the demurrage schedule and the storage rate.
 *
 * @param car the car, as its marks are written
 * @param demurrageDays the chargeable days billed up the demurrage steps
 * @param storageDays the days billed at the storage rate, every day counted
 * @param convertedOn the last day billed as demurrage, absent when the car never got that far
 */
public record Split(String car, int demurrageDays, int storageDays, Optional<LocalDate> convertedOn) {

    public Split {
        Objects.requireNonNull(car, "car");
        Objects.requireNonNull(convertedOn, "convertedOn");
    }

    /** Whether any day went to storage. */
    public boolean converted() {
        return storageDays > 0;
    }

    /** The demurrage half, priced up the steps. */
    public Money demurrage(Tariff tariff) {
        return tariff.charge(demurrageDays);
    }

    /** The storage half, priced flat. */
    public Money storage(StorageTariff tariff) {
        return tariff.daily().times(storageDays);
    }

    /** The two halves together. */
    public Money total(Tariff demurrageTariff, StorageTariff storageTariff) {
        return demurrage(demurrageTariff).plus(storage(storageTariff));
    }

    /**
     * {@code CN 400100 3 days demurrage}, or {@code CN 400100 5 days demurrage then 22 days storage
     * from 2025-01-23}.
     */
    @Override
    public String toString() {
        if (!converted()) {
            return car + " " + demurrageDays + " days demurrage";
        }
        return car + " " + demurrageDays + " days demurrage then " + storageDays + " days storage from "
                + convertedOn.map(LocalDate::toString).orElse("None");
    }
}
