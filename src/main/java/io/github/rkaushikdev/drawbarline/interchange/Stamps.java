package io.github.rkaushikdev.drawbarline.interchange;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reading the date and time stamps a sheet carries: {@code yyyymmdd} and {@code yyyymmddhhmm}.
 *
 * <p>The year is four digits. The month, the day, the hour and the minute each take two digits
 * where two make sense and one where only one does, the first reading that works winning, so a
 * stamp with a field left short still reads the way a clerk would read it. The whole stamp has to
 * be used, and the date has to exist.
 */
final class Stamps {

    private static final String YEAR = "(\\d\\d\\d\\d)";
    private static final String MONTH = "(1[0-2]|0[1-9]|[1-9])";
    private static final String DAY = "(3[01]|[12]\\d|0[1-9]|[1-9]| [1-9])";
    private static final String HOUR = "(2[0-3]|[0-1]\\d|\\d)";
    private static final String MINUTE = "([0-5]\\d|\\d)";

    private static final Pattern DAY_STAMP = Pattern.compile(YEAR + MONTH + DAY);
    private static final Pattern MOMENT_STAMP = Pattern.compile(YEAR + MONTH + DAY + HOUR + MINUTE);

    private Stamps() {}

    /** A day stamp, or nothing when the text is not one. */
    static Optional<LocalDate> day(String text) {
        return read(DAY_STAMP, text).flatMap(found -> date(found));
    }

    /** A day and time stamp, or nothing when the text is not one. */
    static Optional<LocalDateTime> moment(String text) {
        return read(MOMENT_STAMP, text)
                .flatMap(found -> date(found).map(day -> day.atTime(number(found, 4), number(found, 5))));
    }

    private static Optional<Matcher> read(Pattern pattern, String text) {
        Matcher found = pattern.matcher(text);
        if (!found.lookingAt() || found.end() != text.length()) {
            return Optional.empty();
        }
        return Optional.of(found);
    }

    private static Optional<LocalDate> date(Matcher found) {
        int year = number(found, 1);
        int month = number(found, 2);
        int day = number(found, 3);
        if (year < 1 || day > YearMonth.of(year, month).lengthOfMonth()) {
            return Optional.empty();
        }
        return Optional.of(LocalDate.of(year, month, day));
    }

    private static int number(Matcher found, int group) {
        return Integer.parseInt(found.group(group).strip());
    }
}
