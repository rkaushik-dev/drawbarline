package io.github.rkaushikdev.drawbarline.logs;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.LayoutBase;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * One log line: the local time to the second, the level padded to seven, the logger name and the
 * message. {@code 2025-02-10 06:00:01 WARNING drawbarline.settlement.close no tariff in force}.
 */
final class LineLayout extends LayoutBase<ILoggingEvent> {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT);

    @Override
    public String doLayout(ILoggingEvent event) {
        String time = TIME.format(Instant.ofEpochMilli(event.getTimeStamp()).atZone(ZoneId.systemDefault()));
        return String.format(
                Locale.ROOT,
                "%s %-7s %s %s%n",
                time,
                levelName(event.getLevel()),
                event.getLoggerName(),
                event.getFormattedMessage());
    }

    static String levelName(Level level) {
        return level.equals(Level.WARN) ? "WARNING" : level.toString();
    }
}
