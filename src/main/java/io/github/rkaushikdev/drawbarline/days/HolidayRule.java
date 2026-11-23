package io.github.rkaushikdev.drawbarline.days;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * How each holiday a tariff can name falls in a year, and whether the office keeps it on another
 * day when it lands on a weekend.
 *
 * <p>The six the standard tariff recognises are fixed in law. A tariff may publish more, and the
 * two it most often adds are the day after Thanksgiving and Christmas Eve, when nobody is at the
 * mill to unload anything and a customer billed for those days disputes every one of them.
 */
public enum HolidayRule {
    NEW_YEAR("new year", year -> LocalDate.of(year, 1, 1), true),
    MEMORIAL_DAY(
            "memorial day",
            year -> LocalDate.of(year, 5, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY)),
            false),
    INDEPENDENCE_DAY("independence day", year -> LocalDate.of(year, 7, 4), true),
    LABOR_DAY(
            "labor day",
            year -> LocalDate.of(year, 9, 1).with(TemporalAdjusters.dayOfWeekInMonth(1, DayOfWeek.MONDAY)),
            false),
    THANKSGIVING("thanksgiving", HolidayRule::thanksgiving, false),
    CHRISTMAS("christmas", year -> LocalDate.of(year, 12, 25), true),
    DAY_AFTER_THANKSGIVING("day after thanksgiving", year -> thanksgiving(year).plusDays(1), false),
    CHRISTMAS_EVE("christmas eve", year -> LocalDate.of(year, 12, 24), false);

    /** The six every tariff on this road recognises. */
    public static final Set<HolidayRule> STANDARD = Collections.unmodifiableSet(EnumSet.range(NEW_YEAR, CHRISTMAS));

    private final String text;
    private final IntFunction<LocalDate> falls;
    private final boolean observed;

    HolidayRule(String text, IntFunction<LocalDate> falls, boolean observed) {
        this.text = text;
        this.falls = falls;
        this.observed = observed;
    }

    private static LocalDate thanksgiving(int year) {
        return LocalDate.of(year, 11, 1).with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY));
    }

    /** The holiday's name: {@code labor day}. */
    public String text() {
        return text;
    }

    /**
     * The holiday in one year: the day it falls and the day it is kept, which for a fixed date
     * holiday is moved off a weekend by the federal rule and for the others is the day it falls.
     */
    public Holiday in(int year) {
        LocalDate day = falls.apply(year);
        return new Holiday(text, day, observed ? Days.observed(day) : day);
    }
}
