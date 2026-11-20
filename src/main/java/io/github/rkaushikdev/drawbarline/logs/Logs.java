package io.github.rkaushikdev.drawbarline.logs;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import io.github.rkaushikdev.drawbarline.ConfigurationError;
import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.config.Settings;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import org.slf4j.LoggerFactory;

/**
 * Logging, configured once and never as a side effect of loading a class.
 *
 * <p>Nothing touches the package logger except {@link #configure}, and only the command line and
 * the service call that. Each line carries the logger name, because the useful question when
 * something goes wrong at a close is which part of the close, and the name answers it without
 * anybody adding a prefix to every message.
 */
public final class Logs {

    /** The name every logger in the application sits under. */
    public static final String ROOT = "drawbarline";

    private Logs() {}

    /** The level a setting names. */
    public static Level levelOf(String name) {
        return switch (name) {
            case "debug" -> Level.DEBUG;
            case "info" -> Level.INFO;
            case "warning" -> Level.WARN;
            case "error" -> Level.ERROR;
            default ->
                throw new ConfigurationError("no such log level", Details.of("level", name, "known", Settings.LEVELS));
        };
    }

    /** Set the package logger up to write to standard error. Safe to call twice, which a test will. */
    public static Logger configure(Settings settings) {
        return configure(settings, System.err);
    }

    /** Set the package logger up to write to a stream. The stream is never closed by logging. */
    public static Logger configure(Settings settings, PrintStream stream) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger found = context.getLogger(ROOT);
        found.setLevel(levelOf(settings.logLevel()));
        found.detachAndStopAllAppenders();

        LineLayout layout = new LineLayout();
        layout.setContext(context);
        layout.start();
        LayoutWrappingEncoder<ILoggingEvent> encoder = new LayoutWrappingEncoder<>();
        encoder.setContext(context);
        encoder.setLayout(layout);
        encoder.start();
        OutputStreamAppender<ILoggingEvent> appender = new OutputStreamAppender<>();
        appender.setContext(context);
        appender.setName("drawbarline-stream");
        appender.setEncoder(encoder);
        // Stopping an appender closes its stream, and reconfiguring must never close stderr.
        appender.setOutputStream(new Unclosed(stream));
        appender.start();
        found.addAppender(appender);
        found.setAdditive(false);
        return found;
    }

    /** The logger for one part of the application, under the package root. */
    public static org.slf4j.Logger logger(String name) {
        if (!name.equals(ROOT) && !name.startsWith(ROOT + ".")) {
            throw new ConfigurationError("that logger is outside the package", Details.of("name", name));
        }
        return LoggerFactory.getLogger(name);
    }

    /** A stream whose close only flushes, so the stream it wraps outlives the appender. */
    private static final class Unclosed extends FilterOutputStream {

        Unclosed(OutputStream out) {
            super(out);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            out.write(bytes, offset, length);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
