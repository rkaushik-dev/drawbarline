package io.github.rkaushikdev.drawbarline.interchange;

import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/** The first line of an interchange sheet: who sent it, from which junction, for what day. */
public record Header(ReportingMark road, String junction, LocalDate day, int sequence) {

    static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("uuuuMMdd", Locale.ROOT);

    public Header {
        Objects.requireNonNull(road, "road");
        Objects.requireNonNull(junction, "junction");
        Objects.requireNonNull(day, "day");
    }

    /** {@code HDCN  ESC 202501060041}, padded to the line width. */
    public String render() {
        String line = String.format(
                Locale.ROOT, "%s%-4s%-4s%s%04d", Sheet.HEADER, road, junction, STAMP.format(day), sequence);
        return Sheet.padded(line);
    }
}
