package io.github.rkaushikdev.drawbarline.support;

import io.github.rkaushikdev.drawbarline.cli.Main;
import io.github.rkaushikdev.drawbarline.cli.Terminal;
import io.github.rkaushikdev.drawbarline.config.Settings;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
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

    /** The car hire bands the railroad runs on. */
    public static final String RATES =
            """
            type_code,from_age,to_age,effective,hourly,mileage
            L351,0,9,2024-07-01,4630,1210
            L351,10,99,2024-07-01,3180,1210
            """;

    /** The two shippers, one of them on an average agreement. */
    public static final String SHIPPERS =
            """
            code,name,average_agreement
            ESCPAP,Escanaba Paper,no
            PWRGRN,Powers Grain,yes
            """;

    /** Their track, switched on the working week. */
    public static final String INDUSTRIES =
            """
            code,name,station,shipper,spots,switched
            ESCPAP1,mill house track,ESC,ESCPAP,2,
            PWRGRN1,elevator,PWR,PWRGRN,20,
            """;

    /** The repair price master. */
    public static final String PRICES =
            """
            job,effective,labor_hundredths,material_cents
            4312,2024-07-01,250,128000
            5104,2024-07-01,125,84000
            """;

    /** The cars on the loaded railroad's register. */
    public static final List<String> CARS = List.of("CN 400100", "CN 400101", "CN 400102", "DBL 104207");

    /** The demurrage tariff, as it is published. */
    public static final List<String> TARIFF = List.of(
            "reference",
            "tariff",
            "4200",
            "--effective",
            "2024-07-01",
            "--free-loaded",
            "2",
            "--free-empty",
            "1",
            "--step",
            "2:10000",
            "--step",
            "0:20000");

    /** The command that files one of the railroad's hoppers under a mark. */
    public static List<String> carArgs(String mark) {
        return List.of(
                "fleet",
                "add",
                mark,
                "--designation",
                "LO",
                "--type-code",
                "L351",
                "--built",
                "2020-06-01",
                "--light-weight",
                "63500",
                "--gross-rail-load",
                "286000",
                "--length",
                "59-6");
    }

    /** A railroad with its filings loaded and a few cars on the register, filed the way a clerk files them. */
    public static String build(Path into) {
        String database = into.resolve("books.db").toString();
        List<List<String>> commands = new ArrayList<>();
        commands.add(List.of("init"));
        commands.add(List.of("reference", "rates", sheet(into, "rates.csv", RATES)));
        commands.add(List.of("reference", "shippers", sheet(into, "shippers.csv", SHIPPERS)));
        commands.add(List.of("reference", "industries", sheet(into, "industries.csv", INDUSTRIES)));
        commands.add(TARIFF);
        commands.add(List.of("repairs", "price", sheet(into, "prices.csv", PRICES)));
        commands.add(List.of("repairs", "depreciation", "43", "--percent", "4", "--floor", "20"));
        commands.add(List.of("repairs", "labor", "CN", "--effective", "2024-07-01", "--cents", "4200"));
        for (String mark : CARS) {
            commands.add(carArgs(mark));
        }
        for (List<String> command : commands) {
            Ran done = run(database, command.toArray(String[]::new));
            if (done.exitCode() != Main.OK) {
                throw new AssertionError("the railroad would not build at " + command + ": " + done.output());
            }
        }
        return database;
    }

    /** A sheet written into a directory, by its path. */
    public static String sheet(Path into, String name, String text) {
        Path sheet = into.resolve(name);
        try {
            Files.writeString(sheet, text, StandardCharsets.UTF_8);
        } catch (IOException trouble) {
            throw new UncheckedIOException(trouble);
        }
        return sheet.toString();
    }

    /** The loaded railroad, built once for the whole run. */
    private static synchronized Path loaded() {
        if (template == null) {
            try {
                Path into = Files.createTempDirectory("drawbarline-template");
                into.toFile().deleteOnExit();
                for (String name : List.of("rates.csv", "shippers.csv", "industries.csv", "prices.csv")) {
                    into.resolve(name).toFile().deleteOnExit();
                }
                Path database = Path.of(build(into));
                storeOf(database).toFile().deleteOnExit();
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
