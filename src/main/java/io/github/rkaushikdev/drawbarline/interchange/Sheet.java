package io.github.rkaushikdev.drawbarline.interchange;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import io.github.rkaushikdev.drawbarline.clock.Clock;
import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.events.EventKind;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.network.Network;
import io.github.rkaushikdev.drawbarline.reference.Reference;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The interchange sheet, as it actually arrives, and what came out of reading one.
 *
 * <p>The connections send a fixed width file once a day: eighty columns, one record a line, a
 * header naming the road and the junction, then the car records, then a trailer with a count. The
 * count is the only integrity check the format has and it is worth more than it looks: a file
 * truncated by a transfer is the single most common thing that goes wrong.
 *
 * <p>Reading does not stop at the first bad line. A file of two hundred records with one bad date
 * in the middle has a hundred and ninety nine good records in it, and a clerk needs to know which
 * line to fix rather than being told the file is bad. So {@link #parse} gives back what it read and
 * what it could not, and the caller decides whether to take the good half.
 *
 * <p>Clock readings on the sheet are local time at the junction's station. A reading in the hour
 * the clocks repeat in autumn is taken as the first pass, and one in the hour they skip in spring is
 * a problem with that line.
 *
 * <p>Kind codes are two letters and they are not ours. They are what the roads agreed on, they do
 * not spell the words they stand for, and {@link #KIND_CODES} is the only place they are understood.
 */
public final class Sheet {

    public static final int LINE_WIDTH = 80;
    public static final String HEADER = "HD";
    public static final String RECORD = "IC";
    public static final String TRAILER = "TR";

    /** Every kind code the roads publish, and the kind of event it stands for. */
    public static final Map<String, EventKind> KIND_CODES = kindCodes();

    /** The code each kind of event is sent as. */
    public static final Map<EventKind, String> CODE_FOR = codeFor();

    private static final Network LINE = Reference.drawbarLine();
    private static final DateTimeFormatter READING = DateTimeFormatter.ofPattern("uuuuMMddHHmm", Locale.ROOT);
    private static final Pattern WHOLE_NUMBER = Pattern.compile("[+-]?\\d{1,9}");

    private Optional<Header> header = Optional.empty();
    private final List<Event> events = new ArrayList<>();
    private final List<Problem> problems = new ArrayList<>();
    private Optional<Integer> declared = Optional.empty();

    Sheet() {}

    private static Map<String, EventKind> kindCodes() {
        Map<String, EventKind> found = new LinkedHashMap<>();
        found.put("RC", EventKind.RECEIVED);
        found.put("DL", EventKind.DELIVERED);
        found.put("MV", EventKind.MOVED);
        found.put("NT", EventKind.NOTIFIED);
        found.put("PL", EventKind.PLACED);
        found.put("CP", EventKind.CONSTRUCTIVE);
        found.put("RL", EventKind.RELEASED);
        found.put("PU", EventKind.PULLED);
        found.put("BO", EventKind.BAD_ORDERED);
        found.put("RP", EventKind.REPAIRED);
        found.put("HL", EventKind.HELD);
        found.put("FR", EventKind.FREED);
        found.put("WG", EventKind.WEIGHED);
        return Collections.unmodifiableMap(found);
    }

    private static Map<EventKind, String> codeFor() {
        Map<EventKind, String> found = new EnumMap<>(EventKind.class);
        KIND_CODES.forEach((code, kind) -> found.put(kind, code));
        return Collections.unmodifiableMap(found);
    }

    /** The header line, when one read. */
    public Optional<Header> header() {
        return header;
    }

    /** Every car record that read, in file order. */
    public List<Event> events() {
        return Collections.unmodifiableList(events);
    }

    /** Every line that would not read, in file order. */
    public List<Problem> problems() {
        return Collections.unmodifiableList(problems);
    }

    /** The count the trailer declares, when there was a trailer. */
    public Optional<Integer> declared() {
        return declared;
    }

    /** Whether every line read and the trailer agrees with what was read. */
    public boolean isClean() {
        return problems.isEmpty() && isCounted();
    }

    /** Whether the trailer agrees with what was read. */
    public boolean isCounted() {
        return declared.isPresent() && declared.get() == events.size();
    }

    /** Read a whole file, keeping the good records and naming the bad lines. */
    public static Sheet parse(String text) {
        Sheet sheet = new Sheet();
        String station = "";
        ReportingMark road = null;
        int number = 0;
        for (String line : lines(text)) {
            number++;
            if (Text.isBlank(line)) {
                continue;
            }
            String kind = line.substring(0, Math.min(2, line.length()));
            try {
                if (kind.equals(HEADER)) {
                    road = ReportingMark.parse(field(line, 2, 4));
                    station = field(line, 6, 4);
                    LINE.station(station);
                    sheet.header = Optional.of(new Header(
                            road, station, day(field(line, 10, 8)), wholeNumber(field(line, 18, 4), "sequence")));
                } else if (kind.equals(RECORD)) {
                    if (road == null) {
                        throw new SheetError("a record before the header");
                    }
                    sheet.events.add(parseRecord(line, station, road));
                } else if (kind.equals(TRAILER)) {
                    sheet.declared = Optional.of(wholeNumber(field(line, 2, 6), "count"));
                } else {
                    throw new SheetError("no such record type", Details.of("kind", kind));
                }
            } catch (DrawbarlineError bad) {
                sheet.problems.add(new Problem(number, line, bad.getMessage()));
            }
        }
        return sheet;
    }

    /** One car record, read at a junction from a road. Refuses rather than reporting, because the caller catches. */
    public static Event parseRecord(String line, String station, ReportingMark road) {
        String code = field(line, 2, 2);
        EventKind kind = KIND_CODES.get(code);
        if (kind == null) {
            throw new SheetError("no such kind code", Details.of("code", code));
        }
        String initial = field(line, 4, 4);
        String number = field(line, 8, 6);
        String stamp = field(line, 14, 8);
        String clock = field(line, 22, 4);
        String waybill = field(line, 26, 6);
        String tenths = field(line, 32, 5);
        String industry = field(line, 37, 12);

        LocalDateTime local = Stamps.moment(stamp + clock)
                .orElseThrow(() -> new SheetError("that is not a date and time", Details.of("stamp", stamp + clock)));

        CarMark car = CarMark.parse(initial + " " + number);
        Instant when = Clock.toUtc(LINE.station(station), local);
        // The format has no way to say a field is absent, so zero is how the roads write one. A
        // movement of no distance and a waybill numbered zero are both impossible, which is what
        // makes that safe to read.
        int waybillNumber = wholeNumber(waybill, "waybill");
        int distance = wholeNumber(tenths, "tenths");
        return Event.builder(kind, car, when, station)
                .road(kind.needsRoad() ? road : null)
                .industry(industry.isEmpty() ? null : industry)
                .waybill(waybillNumber == 0 ? null : waybillNumber)
                .tenths(distance == 0 ? null : distance)
                .build();
    }

    /** One car record, as we send it, with the clock read at the event's station. */
    public static String renderRecord(Event event) {
        LocalDateTime local =
                Clock.toLocal(LINE.station(event.station()), event.when()).toLocalDateTime();
        String line = String.format(
                Locale.ROOT,
                "%s%s%-4s%06d%s%06d%05d%-12s",
                RECORD,
                CODE_FOR.get(event.kind()),
                event.car().mark(),
                event.car().number(),
                READING.format(local),
                event.waybill().orElse(0),
                event.tenths().orElse(0),
                event.industry().orElse(""));
        return padded(line);
    }

    /** A whole file, header and trailer included, every line ending in a line feed. */
    public static String render(Header header, List<Event> events) {
        List<String> lines = new ArrayList<>();
        lines.add(header.render());
        for (Event one : events) {
            lines.add(renderRecord(one));
        }
        lines.add(padded(String.format(Locale.ROOT, "%s%06d", TRAILER, events.size())));
        return String.join("\n", lines) + "\n";
    }

    /** A line padded with spaces out to the line width; a longer one is left as it is. */
    static String padded(String line) {
        if (line.length() >= LINE_WIDTH) {
            return line;
        }
        return line + " ".repeat(LINE_WIDTH - line.length());
    }

    private static String field(String line, int start, int width) {
        int from = Math.min(start, line.length());
        int to = Math.min(start + width, line.length());
        return Text.strip(line.substring(from, to));
    }

    private static LocalDate day(String text) {
        return Stamps.day(text).orElseThrow(() -> new SheetError("that is not a date", Details.of("day", text)));
    }

    /** A whole number field, where a field left blank reads as zero. */
    private static int wholeNumber(String text, String what) {
        if (text.isEmpty()) {
            return 0;
        }
        if (!WHOLE_NUMBER.matcher(text).matches()) {
            throw new SheetError("that is not a number", Details.of("field", what, "given", text));
        }
        return Integer.parseInt(text);
    }

    /**
     * The lines of a file. A line ends at a line feed, a carriage return, the pair of them, or any of
     * the other line and record separators, and the last line needs no ending.
     */
    static List<String> lines(String text) {
        List<String> found = new ArrayList<>();
        int start = 0;
        int at = 0;
        while (at < text.length()) {
            char one = text.charAt(at);
            if (isLineEnd(one)) {
                found.add(text.substring(start, at));
                if (one == '\r' && at + 1 < text.length() && text.charAt(at + 1) == '\n') {
                    at++;
                }
                start = at + 1;
            }
            at++;
        }
        if (start < text.length()) {
            found.add(text.substring(start));
        }
        return found;
    }

    private static boolean isLineEnd(char one) {
        return switch (one) {
            case '\n', '\r', '\u000b', '\u000c', '\u001c', '\u001d', '\u001e', '\u0085', ' ', ' ' -> true;
            default -> false;
        };
    }
}
