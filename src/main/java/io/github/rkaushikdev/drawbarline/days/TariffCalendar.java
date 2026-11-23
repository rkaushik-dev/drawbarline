package io.github.rkaushikdev.drawbarline.days;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The days a demurrage clock runs on: every day but a Saturday, a Sunday, one of the six holidays,
 * and any day the road has declared closed.
 *
 * <p>The declared days are the ones nobody can put in a table. When a blizzard shuts the
 * Escanaba yard for two days the road declares an emergency, and a customer whose track is under
 * four feet of snow is not charged for the days nobody could have pulled the car. The declaration
 * is the road's, it is dated and it carries its reason, because a customer who disputes the month
 * disputes it by day.
 *
 * <p>A calendar is never changed in place. Declaring a day gives back a new calendar, so {@link
 * #STANDARD} is the six holidays and the weekends and nothing else, always.
 */
public final class TariffCalendar {

    /** The weekends and the six holidays, with nothing declared. */
    public static final TariffCalendar STANDARD = new TariffCalendar(Map.of());

    private final Map<LocalDate, String> declared;

    private TariffCalendar(Map<LocalDate, String> declared) {
        this.declared = Collections.unmodifiableMap(new TreeMap<>(declared));
    }

    /** This calendar with one more day declared closed, refusing a day declared twice. */
    public TariffCalendar declaring(LocalDate day, String reason) {
        if (Text.isBlank(reason)) {
            throw new CalendarError("a declared day carries its reason", Details.of("day", day.toString()));
        }
        if (declared.containsKey(day)) {
            throw new CalendarError("that day is already declared", Details.of("day", day.toString()));
        }
        Map<LocalDate, String> next = new TreeMap<>(declared);
        next.put(day, reason);
        return new TariffCalendar(next);
    }

    /** Every declared day and its reason, in date order. */
    public Map<LocalDate, String> declared() {
        return declared;
    }

    /** Why a day was declared closed, if it was. */
    public Optional<String> declaredOn(LocalDate day) {
        return Optional.ofNullable(declared.get(day));
    }

    /** A day demurrage may be charged for. */
    public boolean isChargeable(LocalDate day) {
        return !Days.isWeekend(day) && !Days.isHoliday(day) && !declared.containsKey(day);
    }

    /** How many chargeable days there are from one day to another, both ends counted. */
    public int chargeableDays(LocalDate first, LocalDate last) {
        if (last.isBefore(first)) {
            throw new CalendarError(
                    "that span runs backwards", Details.of("first", first.toString(), "last", last.toString()));
        }
        int count = 0;
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            if (isChargeable(day)) {
                count++;
            }
        }
        return count;
    }

    /** The first chargeable day strictly after a day. */
    public LocalDate nextChargeable(LocalDate day) {
        LocalDate found = day.plusDays(1);
        while (!isChargeable(found)) {
            found = found.plusDays(1);
        }
        return found;
    }

    /** Walk forward a number of chargeable days, which is how free time expires. */
    public LocalDate addChargeableDays(LocalDate day, int count) {
        if (count < 0) {
            throw new CalendarError("free time does not run backwards", Details.of("count", count));
        }
        LocalDate found = day;
        for (int step = 0; step < count; step++) {
            found = nextChargeable(found);
        }
        return found;
    }
}
