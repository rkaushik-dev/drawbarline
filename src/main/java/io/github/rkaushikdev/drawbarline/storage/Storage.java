package io.github.rkaushikdev.drawbarline.storage;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.TariffError;
import io.github.rkaushikdev.drawbarline.days.Days;
import io.github.rkaushikdev.drawbarline.demurrage.Detention;
import io.github.rkaushikdev.drawbarline.demurrage.Tariff;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Storage, which is what demurrage turns into when nobody is coming.
 *
 * <p>Demurrage is a lever. It climbs so that a customer works their cars, and the climb only makes
 * sense while somebody is trying to. A car that has stood on a track for six weeks is not being
 * worked slowly, it is being stored, and billing storage at the top demurrage step is billing a
 * lever nobody is pulling.
 *
 * <p>So the tariff converts. After a stated number of chargeable days the car comes off the
 * demurrage schedule and onto a flat storage rate, and the storage rate counts every day rather
 * than only chargeable ones, because a car in storage occupies the track on a Sunday exactly as
 * much as on a Tuesday.
 *
 * <p>The conversion day belongs to demurrage. A car on its tenth chargeable day when storage starts
 * at ten is billed demurrage for that day and storage from the next.
 */
public final class Storage {

    private Storage() {}

    /**
     * The last day billed as demurrage, if the car got that far: walking day by day from the end of
     * free time (or the clock start when there was none) until the stated number of chargeable days
     * has been counted.
     */
    public static Optional<LocalDate> conversionDay(Detention detention, StorageTariff storage) {
        if (detention.chargeable() < storage.afterDays()) {
            return Optional.empty();
        }
        LocalDate day = detention.freeUntil().orElse(detention.clockStarts());
        int counted = 0;
        while (counted < storage.afterDays() && !day.isAfter(detention.countedTo())) {
            day = day.plusDays(1);
            if (Days.isChargeable(day)) {
                counted++;
            }
        }
        return Optional.of(day);
    }

    /** Divide a detention between the demurrage schedule and the storage rate. */
    public static Split split(Detention detention, StorageTariff storage) {
        String car = detention.car().toString();
        if (detention.chargeable() < storage.afterDays()) {
            return new Split(car, detention.chargeable(), 0, Optional.empty());
        }
        LocalDate converted = conversionDay(detention, storage)
                .orElseThrow(() -> new TariffError(
                        "a detention past the conversion has a conversion day", Details.of("car", car)));
        long stored = ChronoUnit.DAYS.between(converted, detention.countedTo());
        return new Split(car, storage.afterDays(), (int) Math.max(stored, 0), Optional.of(converted));
    }

    /** What a detention comes to once the conversion is taken into account. */
    public static Money charge(Detention detention, Tariff demurrageTariff, StorageTariff storageTariff) {
        return split(detention, storageTariff).total(demurrageTariff, storageTariff);
    }
}
