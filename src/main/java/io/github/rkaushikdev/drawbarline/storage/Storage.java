package io.github.rkaushikdev.drawbarline.storage;

import io.github.rkaushikdev.drawbarline.days.TariffCalendar;
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
        return conversionDay(detention, storage, TariffCalendar.STANDARD);
    }

    /** The last day billed as demurrage, counting chargeable days on a tariff calendar. */
    public static Optional<LocalDate> conversionDay(
            Detention detention, StorageTariff storage, TariffCalendar calendar) {
        if (detention.chargeable() < storage.afterDays()) {
            return Optional.empty();
        }
        LocalDate day = detention.freeUntil().orElse(detention.clockStarts());
        return Optional.of(calendar.addChargeableDays(day, storage.afterDays()));
    }

    /** Divide a detention between the demurrage schedule and the storage rate. */
    public static Split split(Detention detention, StorageTariff storage) {
        return split(detention, storage, TariffCalendar.STANDARD);
    }

    /** Divide a detention under the storage tariff in force on the day the car was placed. */
    public static Split split(Detention detention, StorageTariffs published) {
        return split(detention, published.inEffectOn(detention.placedOn()), TariffCalendar.STANDARD);
    }

    /** Divide a detention between the demurrage schedule and the storage rate, on a tariff calendar. */
    public static Split split(Detention detention, StorageTariff storage, TariffCalendar calendar) {
        String car = detention.car().toString();
        Optional<LocalDate> converted = conversionDay(detention, storage, calendar);
        if (converted.isEmpty()) {
            return new Split(car, detention.chargeable(), 0, Optional.empty());
        }
        long stored = ChronoUnit.DAYS.between(converted.get(), detention.countedTo());
        return new Split(car, storage.afterDays(), (int) Math.max(stored, 0), converted);
    }

    /** What a detention comes to once the conversion is taken into account. */
    public static Money charge(Detention detention, Tariff demurrageTariff, StorageTariff storageTariff) {
        return split(detention, storageTariff).total(demurrageTariff, storageTariff);
    }
}
