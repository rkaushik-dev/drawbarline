package io.github.rkaushikdev.drawbarline.days;

import io.github.rkaushikdev.drawbarline.Details;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The calendar the tariff counts on.
 *
 * <p>Car hire runs on hours and does not care what day it is. Demurrage runs on days and cares a
 * great deal, because a Saturday, a Sunday and six holidays a year are not chargeable, and free
 * time is quoted in chargeable days.
 *
 * <p>The holidays are computed rather than tabled, so nobody has to remember to extend a table
 * every December: each is a {@link HolidayRule}. The observance rule for the fixed date ones is the
 * ordinary federal one: a holiday landing on a Saturday is kept the Friday before, and one landing
 * on a Sunday the Monday after. The readings here are the standard tariff's; the holidays another
 * tariff adds, and days the road declares closed, are on a {@link TariffCalendar}.
 */
public final class Days {

    private Days() {}

    /** Where a fixed date holiday actually falls for the office. */
    public static LocalDate observed(LocalDate day) {
        return switch (day.getDayOfWeek()) {
            case SATURDAY -> day.minusDays(1);
            case SUNDAY -> day.plusDays(1);
            default -> day;
        };
    }

    /** The six days the standard tariff recognises, in the order they are kept. */
    public static List<Holiday> holidays(int year) {
        return holidays(year, HolidayRule.STANDARD);
    }

    /** The holidays a tariff recognises in a year, in the order they are kept. */
    public static List<Holiday> holidays(int year, Set<HolidayRule> recognised) {
        if (year < 1900 || year > 2200) {
            throw new CalendarError("that year is outside anything we settle", Details.of("year", year));
        }
        return recognised.stream()
                .map(rule -> rule.in(year))
                .sorted(Comparator.comparing(Holiday::kept))
                .toList();
    }

    /** The holiday kept on a day, if any. */
    public static Optional<Holiday> holidayOn(LocalDate day) {
        return holidayOn(day, HolidayRule.STANDARD);
    }

    /** The holiday a tariff recognises kept on a day, if any. */
    public static Optional<Holiday> holidayOn(LocalDate day, Set<HolidayRule> recognised) {
        return holidays(day.getYear(), recognised).stream()
                .filter(one -> one.kept().equals(day))
                .findFirst();
    }

    public static boolean isHoliday(LocalDate day) {
        return holidayOn(day).isPresent();
    }

    public static boolean isWeekend(LocalDate day) {
        return day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    /** A day demurrage may be charged for on the standard calendar. */
    public static boolean isChargeable(LocalDate day) {
        return TariffCalendar.STANDARD.isChargeable(day);
    }

    /** How many chargeable days there are from one day to another, both ends counted. */
    public static int chargeableDays(LocalDate first, LocalDate last) {
        return TariffCalendar.STANDARD.chargeableDays(first, last);
    }

    /** The first chargeable day strictly after a day. */
    public static LocalDate nextChargeable(LocalDate day) {
        return TariffCalendar.STANDARD.nextChargeable(day);
    }

    /** Walk forward a number of chargeable days, which is how free time expires. */
    public static LocalDate addChargeableDays(LocalDate day, int count) {
        return TariffCalendar.STANDARD.addChargeableDays(day, count);
    }
}
