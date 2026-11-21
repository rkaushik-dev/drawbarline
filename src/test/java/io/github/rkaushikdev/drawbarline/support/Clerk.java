package io.github.rkaushikdev.drawbarline.support;

import io.github.rkaushikdev.drawbarline.cli.Main;
import io.github.rkaushikdev.drawbarline.cli.Terminal;
import io.github.rkaushikdev.drawbarline.config.Settings;
import io.github.rkaushikdev.drawbarline.persistence.Database;
import io.github.rkaushikdev.drawbarline.persistence.UnitOfWork;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Running the command line the way a clerk does, one command against one set of books, with what
 * it printed and how it ended kept for the test to read.
 *
 * <p>Building the loaded railroad is a dozen commands and most of the time a command line suite
 * would spend, so it is built once and copied. The books are a file, and copying a file is a great
 * deal cheaper than doing again what made it. Each test still gets its own copy and can write to it
 * freely, which is the property that matters.
 */
public final class Clerk {

    private static Path template;

    private Clerk() {}

    /**
     * How one command line ended.
     *
     * @param out what it wrote to standard output
     * @param err what it wrote to standard error
     * @param output both, in the order they were written, as a person at the terminal saw them
     */
    public record Ran(int exitCode, String out, String err, String output) {

        /** What it wrote to standard output, line by line. */
        public List<String> lines() {
            List<String> found = new ArrayList<>(List.of(out.split("\n", -1)));
            if (found.get(found.size() - 1).isEmpty()) {
                found.remove(found.size() - 1);
            }
            return List.copyOf(found);
        }
    }

    /** One command against one set of books. */
    public static Ran run(String database, String... args) {
        return run(Map.of(), database, args);
    }

    /** One command against one set of books, started in an environment. */
    public static Ran run(Map<String, String> environment, String database, String... args) {
        List<String> line = new ArrayList<>(List.of("--database", database));
        line.addAll(List.of(args));
        return plain(environment, line.toArray(String[]::new));
    }

    /** One command line exactly as given, started in an environment. */
    public static Ran plain(Map<String, String> environment, String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        StringWriter both = new StringWriter();
        Terminal terminal = new Terminal(
                environment,
                new BufferedReader(new StringReader("")),
                new PrintWriter(new Both(out, both)),
                new PrintWriter(new Both(err, both)));
        int code = Main.run(terminal, args);
        return new Ran(code, out.toString(), err.toString(), both.toString());
    }

    /** A database with the schema and the counters and nothing else, built the way a clerk builds one. */
    public static String empty(Path into) {
        String database = into.resolve("empty.db").toString();
        Ran built = run(database, "init");
        if (built.exitCode() != Main.OK) {
            throw new AssertionError("the books would not build: " + built.output());
        }
        return database;
    }

    /** A private copy of the loaded railroad, for one test to write to. */
    public static String books(Path into) {
        Path target = into.resolve("books.db");
        try {
            Files.copy(storeOf(loaded()), storeOf(target));
        } catch (IOException trouble) {
            throw new UncheckedIOException(trouble);
        }
        return target.toString();
    }

    /** The file the books named {@code database} are stored in. */
    public static Path storeOf(Path database) {
        return Path.of(database + Settings.STORE_SUFFIX);
    }

    /** The loaded railroad, built once for the whole run. */
    private static synchronized Path loaded() {
        if (template == null) {
            try {
                Path into = Files.createTempDirectory("drawbarline-template");
                into.toFile().deleteOnExit();
                Path database = into.resolve("books.db");
                storeOf(database).toFile().deleteOnExit();
                empty(into);
                Files.move(storeOf(into.resolve("empty.db")), storeOf(database));
                try (Database books = Database.open(new Settings().withDatabase(database.toString()));
                        UnitOfWork work = books.unitOfWork()) {
                    Office.file(work);
                    work.commit();
                }
                template = database;
            } catch (IOException trouble) {
                throw new UncheckedIOException(trouble);
            }
        }
        return template;
    }

    /** A writer that writes everything to two others. */
    private static final class Both extends Writer {

        private final Writer one;
        private final Writer other;

        Both(Writer one, Writer other) {
            this.one = one;
            this.other = other;
        }

        @Override
        public void write(char[] text, int offset, int length) throws IOException {
            synchronized (other) {
                one.write(text, offset, length);
                other.write(text, offset, length);
            }
        }

        @Override
        public void flush() throws IOException {
            one.flush();
            other.flush();
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
