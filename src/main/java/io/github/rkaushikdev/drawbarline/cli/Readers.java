package io.github.rkaushikdev.drawbarline.cli;

import io.github.rkaushikdev.drawbarline.clock.Clock;
import io.github.rkaushikdev.drawbarline.network.Station;
import io.github.rkaushikdev.drawbarline.reference.Reference;
import io.github.rkaushikdev.drawbarline.settlement.Period;
import io.github.rkaushikdev.drawbarline.util.Literal;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/**
 * Reading the things a command line argument can be.
 *
 * <p>Four command modules grew their own date reader, and by the time there were four of them two
 * accepted a month where the others wanted a day and one let a bare parsing failure reach the user
 * as a stack trace. So they are here, once, and every command reads its arguments through them.
 *
 * <p>Everything in here refuses rather than guesses, and every refusal names the argument and shows
 * the form it wanted. A command line is the one interface where the user cannot see a schema, so
 * the error message is the schema.
 *
 * <p>A day is four digits of year, then a month and a day of one or two digits each; an hour and a
 * minute may be one digit too. Space around the text is ignored, and between the day and the time
 * of a reading any run of space will do.
 */
public final class Readers {

    public static final String DAY_FORM = "yyyy-mm-dd";
    public static final String MONTH_FORM = "yyyy-mm";
    public static final String READING_FORM = "yyyy-mm-dd hh:mm";
    public static final String CLOCK_FORM = "hh:mm";

    private static final String YEAR = "(?<year>[0-9]{4})";
    private static final String MONTH = "(?<month>1[0-2]|0[1-9]|[1-9])";
    private static final String DAY = "(?<day>3[01]|[12][0-9]|0[1-9]|[1-9]| [1-9])";
    private static final String HOUR = "(?<hour>2[0-3]|[01][0-9]|[0-9])";
    private static final String MINUTE = "(?<minute>[0-5][0-9]|[0-9])";
    private static final String SPACE =
            "[\\t-\\r\\x1c-\\x20\\x85\\xa0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000]+";

    private static final Pattern A_DAY = Pattern.compile(YEAR + "-" + MONTH + "-" + DAY);
    private static final Pattern A_MONTH = Pattern.compile(YEAR + "-" + MONTH);
    private static final Pattern A_READING =
            Pattern.compile(YEAR + "-" + MONTH + "-" + DAY + SPACE + HOUR + ":" + MINUTE);
    private static final Pattern A_TIME = Pattern.compile(HOUR + ":" + MINUTE);
    private static final Pattern A_WHOLE = Pattern.compile("[+-]?[0-9](?:_?[0-9])*");

    private Readers() {}

    /** A day, written the way every filing writes one. */
    public static LocalDate aDate(String text) {
        return day(A_DAY, text).orElseThrow(() -> refused(text, "a date", DAY_FORM));
    }

    /** An accounting month. */
    public static Period aMonth(String text) {
        Matcher found = A_MONTH.matcher(Text.strip(text));
        if (!found.matches()) {
            throw refused(text, "a month", MONTH_FORM);
        }
        int year = Integer.parseInt(found.group("year"));
        if (year < 1) {
            throw refused(text, "a month", MONTH_FORM);
        }
        return new Period(year, Integer.parseInt(found.group("month")));
    }

    /** A local clock reading at a station, read as a moment, taking the first pass of a repeated hour. */
    public static Instant aMoment(String stationCode, String text) {
        return aMoment(stationCode, text, false);
    }

    /**
     * A local clock reading at a station, read as a moment. {@code secondPass} takes the later of the
     * two hours the morning the clocks go back.
     */
    public static Instant aMoment(String stationCode, String text, boolean secondPass) {
        LocalDateTime local = aReading(text);
        Station station = Reference.drawbarLine().station(stationCode);
        return Clock.toUtc(station, local, secondPass);
    }

    /** A local clock reading with no station, as a delivery check is asked about. */
    public static LocalDateTime aReading(String text) {
        Matcher found = A_READING.matcher(Text.strip(text));
        Optional<LocalDate> on = found.matches() ? dayOf(found) : Optional.empty();
        if (on.isEmpty()) {
            throw refused(text, "a reading", READING_FORM);
        }
        return on.get().atTime(timeOf(found));
    }

    /** An hour on a clock, as an agreement states its manned hours. */
    public static LocalTime aTime(String text) {
        Matcher found = A_TIME.matcher(Text.strip(text));
        if (!found.matches()) {
            throw refused(text, "a time", CLOCK_FORM);
        }
        return timeOf(found);
    }

    /**
     * A day given as yyyy-mm-dd read as midnight UTC, or now when there is none.
     *
     * <p>Four commands wanted this and each wrote it out. Two of them forgot the zone and produced a
     * reading with none, which every clock in the package refuses, so the command failed on an
     * argument it had accepted.
     */
    public static Instant aMomentUtc(Optional<String> text) {
        if (text.isEmpty()) {
            return Clock.now();
        }
        return Clock.midnightUtc(aDate(text.get()));
    }

    /**
     * A whole number, with an optional sign, digits that may be grouped by single underscores, and
     * space around it.
     */
    public static int whole(String text) {
        String cleaned = Text.strip(text);
        if (A_WHOLE.matcher(cleaned).matches()) {
            try {
                return Integer.parseInt(cleaned.replace("_", ""));
            } catch (NumberFormatException tooLarge) {
                throw new CommandError(Literal.of(text) + " is too large a number");
            }
        }
        throw new CommandError(Literal.of(text) + " is not a whole number");
    }

    /**
     * Every row of a comma separated sheet, keyed on its heading, in the order the heading names
     * them.
     *
     * <p>A field may be quoted, and a quoted field may hold commas, line breaks and doubled quotes.
     * A blank line is not a row. A row shorter than the heading reads the missing fields as empty,
     * and fields past the end of the heading are not read.
     */
    public static List<Map<String, String>> rowsOf(Path path) {
        String text;
        try {
            text = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new CommandError(name(path) + " cannot be read as text");
        }
        List<List<String>> records = Sheets.records(text);
        if (records.isEmpty()) {
            return List.of();
        }
        List<String> heading = records.get(0);
        List<Map<String, String>> rows = new ArrayList<>();
        for (List<String> record : records.subList(1, records.size())) {
            if (record.isEmpty()) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int at = 0; at < heading.size(); at++) {
                row.put(heading.get(at), at < record.size() ? record.get(at) : "");
            }
            rows.add(Collections.unmodifiableMap(row));
        }
        return List.copyOf(rows);
    }

    /** Refuse a sheet that does not carry the columns a loader needs. */
    public static void requireColumns(List<Map<String, String>> rows, Set<String> wanted, String name) {
        Set<String> missing = new TreeSet<>(wanted);
        if (!rows.isEmpty()) {
            missing.removeAll(rows.get(0).keySet());
        }
        if (!missing.isEmpty()) {
            throw new CommandError(name + " is missing " + Literal.of(List.copyOf(missing)));
        }
    }

    /** The name of a file without the directories it is in. */
    static String name(Path path) {
        Path file = path.getFileName();
        return file == null ? path.toString() : file.toString();
    }

    private static Optional<LocalDate> day(Pattern pattern, String text) {
        Matcher found = pattern.matcher(Text.strip(text));
        return found.matches() ? dayOf(found) : Optional.empty();
    }

    private static Optional<LocalDate> dayOf(Matcher found) {
        int year = Integer.parseInt(found.group("year"));
        int month = Integer.parseInt(found.group("month"));
        int day = Integer.parseInt(Text.strip(found.group("day")));
        if (year < 1) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.of(year, month, day));
        } catch (DateTimeException noSuchDay) {
            return Optional.empty();
        }
    }

    private static LocalTime timeOf(Matcher found) {
        return LocalTime.of(Integer.parseInt(found.group("hour")), Integer.parseInt(found.group("minute")));
    }

    private static CommandError refused(String text, String what, String form) {
        return new CommandError(Literal.of(text) + " is not " + what + ", write it as " + form);
    }

    /** A sheet named on the command line, which has to be a file that is there and can be read. */
    public static final class Sheet implements ITypeConverter<Path> {

        @Override
        public Path convert(String value) {
            Path path = Path.of(value);
            if (!Files.exists(path)) {
                throw new TypeConversionException("File " + Literal.of(value) + " does not exist.");
            }
            if (Files.isDirectory(path)) {
                throw new TypeConversionException("File '" + value + "' is a directory.");
            }
            if (!Files.isReadable(path)) {
                throw new TypeConversionException("File " + Literal.of(value) + " is not readable.");
            }
            return path;
        }
    }

    /** A file a command writes, which may not be there yet but cannot be a directory. */
    public static final class Target implements ITypeConverter<Path> {

        @Override
        public Path convert(String value) {
            Path path = Path.of(value);
            if (Files.isDirectory(path)) {
                throw new TypeConversionException("File '" + value + "' is a directory.");
            }
            return path;
        }
    }
}
