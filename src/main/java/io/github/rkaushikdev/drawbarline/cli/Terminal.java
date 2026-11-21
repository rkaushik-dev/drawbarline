package io.github.rkaushikdev.drawbarline.cli;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Objects;

/**
 * What a command talks to: the environment it was started in, the keyboard and the two streams.
 *
 * <p>What a command produces goes to {@code out}. A refusal, and anything the package logs, goes to
 * {@code err}, so a report piped into a file or an export read by another program never has a
 * warning in the middle of it.
 *
 * @param environment the variables the process was started with, which is where the settings come
 *     from when no option says otherwise
 */
public record Terminal(Map<String, String> environment, BufferedReader in, PrintWriter out, PrintWriter err) {

    public Terminal {
        environment = Map.copyOf(environment);
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
    }

    /** The process's own environment, keyboard and streams. */
    public static Terminal system() {
        Charset charset = Charset.defaultCharset();
        return new Terminal(
                System.getenv(),
                new BufferedReader(new InputStreamReader(System.in, charset)),
                new PrintWriter(System.out, true, charset),
                new PrintWriter(System.err, true, charset));
    }

    /**
     * The error stream as a byte stream, for logging, which writes encoded lines. Each line reaches
     * {@code err} whole when the logger flushes it.
     */
    PrintStream errBytes() {
        return new PrintStream(new LineBytes(err), true, Charset.defaultCharset());
    }

    /** Bytes collected until a flush and then written to a writer as text. */
    private static final class LineBytes extends OutputStream {

        private final PrintWriter target;
        private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

        LineBytes(PrintWriter target) {
            this.target = target;
        }

        @Override
        public synchronized void write(int one) {
            pending.write(one);
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) {
            pending.write(bytes, offset, length);
        }

        @Override
        public synchronized void flush() {
            if (pending.size() > 0) {
                target.write(pending.toString(Charset.defaultCharset()));
                pending.reset();
            }
            target.flush();
        }

        @Override
        public void close() {
            flush();
        }
    }
}
