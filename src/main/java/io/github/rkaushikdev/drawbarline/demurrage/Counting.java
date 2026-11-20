package io.github.rkaushikdev.drawbarline.demurrage;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.TariffError;
import io.github.rkaushikdev.drawbarline.clock.Clock;
import io.github.rkaushikdev.drawbarline.days.Days;
import io.github.rkaushikdev.drawbarline.events.OnTrack;
import io.github.rkaushikdev.drawbarline.network.Station;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Counting the days a car was held.
 *
 * <p>The clock does not start when the car is placed. It starts the first chargeable day after
 * that, because a car set out at four in the afternoon is not a day of anybody's time. From there
 * the customer has their free days, all of them chargeable days, and demurrage runs from the day
 * after the last free one until the day they release it.
 *
 * <p>Every date here is local at the station the car is standing at, not UTC and not the general
 * office clock. A car placed at half past eleven on a Monday night at Escanaba was placed on
 * Monday, and if the same events had happened at Marinette it would still have been Monday, but
 * the two are an hour apart and sooner or later one of them lands the wrong side of midnight.
 * Reading the local date at the station is the only thing that gets both right.
 */
public final class Counting {

    private Counting() {}

    /** The first chargeable day after placement, which is where free time begins. */
    public static LocalDate clockStartsOn(LocalDate placedOn) {
        return Days.nextChargeable(placedOn);
    }

    /** The last free day, or nothing at all when the tariff allows none. */
    public static Optional<LocalDate> freeTimeEnds(LocalDate clockStarts, int freeDays) {
        if (freeDays < 0) {
            throw new TariffError("free time is not negative", Details.of("free_days", freeDays));
        }
        if (freeDays == 0) {
            return Optional.empty();
        }
        return Optional.of(Days.addChargeableDays(clockStarts, freeDays - 1));
    }

    /** The first day demurrage is owed for: the day after free time, or the clock start when there is none. */
    static LocalDate firstCharged(LocalDate clockStarts, Optional<LocalDate> freeUntil) {
        return freeUntil.map(Days::nextChargeable).orElse(clockStarts);
    }

    /** The chargeable days from the first charged day to the day counting stops, both counted. */
    static int chargedDays(LocalDate firstCharged, LocalDate countedTo) {
        return countedTo.isBefore(firstCharged) ? 0 : Days.chargeableDays(firstCharged, countedTo);
    }

    /** Work a placement span out into the days it is charged for, on the station's own calendar. */
    public static Detention count(OnTrack span, Tariff tariff, Station station, boolean loaded) {
        LocalDate placedOn = Clock.toLocal(station, span.interval().start()).toLocalDate();
        Instant endsAt = span.releasedAt().orElse(span.interval().end());
        LocalDate countedTo = Clock.toLocal(station, endsAt).toLocalDate();

        LocalDate clockStarts = clockStartsOn(placedOn);
        Optional<LocalDate> freeUntil = freeTimeEnds(clockStarts, tariff.freeDays(loaded));
        int charged = chargedDays(firstCharged(clockStarts, freeUntil), countedTo);
        return new Detention(
                span.car(),
                span.industry(),
                placedOn,
                clockStarts,
                freeUntil,
                countedTo,
                charged,
                loaded,
                span.constructive());
    }
}
