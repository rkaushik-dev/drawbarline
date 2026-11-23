package io.github.rkaushikdev.drawbarline.demurrage;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.TariffError;
import io.github.rkaushikdev.drawbarline.clock.Clock;
import io.github.rkaushikdev.drawbarline.days.TariffCalendar;
import io.github.rkaushikdev.drawbarline.events.OnTrack;
import io.github.rkaushikdev.drawbarline.network.Station;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Counting the days a car was held.
 *
 * <p>The clock does not start when the car is placed. It starts the first chargeable day after
 * that, because a car set out at four in the afternoon is not a day of anybody's time, and a car
 * set out ahead of the day it was ordered for waits for that day. From there the customer has their
 * free days, all of them chargeable days, and demurrage runs from the day after the last free one
 * until the day they release it.
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
        return clockStartsOn(placedOn, TariffCalendar.STANDARD);
    }

    /** The first chargeable day after placement on a calendar. */
    public static LocalDate clockStartsOn(LocalDate placedOn, TariffCalendar calendar) {
        return calendar.nextChargeable(placedOn);
    }

    /**
     * Where free time begins for a car that may have been placed ahead of the day it was ordered
     * for: the ordinary clock start, or the day it was wanted when that is later, moved on to the
     * first chargeable day from there. A customer who ordered a car for Thursday and found it on
     * the track on Monday did not ask for three extra days of our car and is not charged for them.
     */
    public static LocalDate clockStartsOn(LocalDate placedOn, Optional<LocalDate> wantedOn, TariffCalendar calendar) {
        LocalDate ordinary = clockStartsOn(placedOn, calendar);
        return wantedOn.filter(ordinary::isBefore)
                .map(wanted -> calendar.isChargeable(wanted) ? wanted : calendar.nextChargeable(wanted))
                .orElse(ordinary);
    }

    /** The last free day, or nothing at all when the tariff allows none. */
    public static Optional<LocalDate> freeTimeEnds(LocalDate clockStarts, int freeDays) {
        return freeTimeEnds(clockStarts, freeDays, TariffCalendar.STANDARD);
    }

    /** The last free day on a calendar, or nothing at all when the tariff allows none. */
    public static Optional<LocalDate> freeTimeEnds(LocalDate clockStarts, int freeDays, TariffCalendar calendar) {
        if (freeDays < 0) {
            throw new TariffError("free time is not negative", Details.of("free_days", freeDays));
        }
        if (freeDays == 0) {
            return Optional.empty();
        }
        return Optional.of(calendar.addChargeableDays(clockStarts, freeDays - 1));
    }

    /** The first day demurrage is owed for: the day after free time, or the clock start when there is none. */
    static LocalDate firstCharged(LocalDate clockStarts, Optional<LocalDate> freeUntil, TariffCalendar calendar) {
        return freeUntil.map(calendar::nextChargeable).orElse(clockStarts);
    }

    /** The chargeable days from the first charged day to the day counting stops, both counted. */
    static int chargedDays(LocalDate firstCharged, LocalDate countedTo, TariffCalendar calendar) {
        return countedTo.isBefore(firstCharged) ? 0 : calendar.chargeableDays(firstCharged, countedTo);
    }

    /** Work a placement span out into the days it is charged for, on the station's own calendar. */
    public static Detention count(OnTrack span, Tariff tariff, Station station, boolean loaded) {
        return count(span, tariff, station, loaded, TariffCalendar.STANDARD);
    }

    /**
     * Work a placement span out into the days it is charged for, on the station's own clock and the
     * tariff calendar given, so a day the road declared closed is neither free time nor charged.
     */
    public static Detention count(
            OnTrack span, Tariff tariff, Station station, boolean loaded, TariffCalendar calendar) {
        return count(span, tariff, station, loaded, calendar, Optional.empty());
    }

    /**
     * Work a placement span out into the days it is charged for, the clock held back to the day the
     * car was ordered for when it was placed early.
     *
     * @param wantedOn the day the customer's order asked for the car, when the car filled one
     */
    public static Detention count(
            OnTrack span,
            Tariff tariff,
            Station station,
            boolean loaded,
            TariffCalendar calendar,
            Optional<LocalDate> wantedOn) {
        LocalDate placedOn = Clock.toLocal(station, span.interval().start()).toLocalDate();
        LocalDate countedTo = Clock.toLocal(
                        station, span.releasedAt().orElse(span.interval().end()))
                .toLocalDate();
        LocalDate clockStarts = clockStartsOn(placedOn, wantedOn, calendar);
        Optional<LocalDate> freeUntil = freeTimeEnds(clockStarts, tariff.freeDays(loaded), calendar);
        int charged = chargedDays(firstCharged(clockStarts, freeUntil, calendar), countedTo, calendar);
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
