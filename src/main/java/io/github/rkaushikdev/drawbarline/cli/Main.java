package io.github.rkaushikdev.drawbarline.cli;

import io.github.rkaushikdev.drawbarline.Drawbarline;
import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import io.github.rkaushikdev.drawbarline.api.DrawbarlineApplication;
import io.github.rkaushikdev.drawbarline.clock.Clock;
import io.github.rkaushikdev.drawbarline.config.Settings;
import io.github.rkaushikdev.drawbarline.health.Finding;
import io.github.rkaushikdev.drawbarline.health.Health;
import io.github.rkaushikdev.drawbarline.logs.Logs;
import io.github.rkaushikdev.drawbarline.persistence.Database;
import io.github.rkaushikdev.drawbarline.persistence.Numbering;
import io.github.rkaushikdev.drawbarline.persistence.UnitOfWork;
import io.github.rkaushikdev.drawbarline.settlement.Period;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.TypeConversionException;

/**
 * The command line.
 *
 * <p>One root command, one database option, and every command opening its own unit of work. Nothing
 * here holds a connection between commands, because the office runs these out of a scheduler one at
 * a time and a long lived connection to a file on a share is a way to lose a month.
 *
 * <p>Settings come from the environment and are overridden by the options, which is the order
 * anybody expects. The database option exists because it is the one setting a clerk changes between
 * two commands.
 *
 * <p>Every command that writes commits at the end and only at the end. A command that refuses
 * leaves the database exactly as it found it, and what the user sees is the refusal with its
 * context on standard error, not a stack trace. The exit code says which kind of refusal it was:
 * {@value #REFUSED} for one about the books, {@value #MISUSED} for arguments that were wrong or a
 * request the command line will not carry out as asked.
 *
 * <p>No command but {@code serve} starts the HTTP service, so a scheduled command starts quickly
 * and honours {@code --database} before anything connects.
 */
@Command(
        name = "drawbarline",
        description = "Car hire, demurrage and interchange settlement.",
        version = "drawbarline, version " + Drawbarline.VERSION,
        subcommands = {
            Main.Init.class,
            Main.ShowSettings.class,
            Main.Status.class,
            Main.Doctor.class,
            Main.Serve.class,
            CloseCommands.ExportMonth.class
        })
public final class Main {

    /** It worked. */
    public static final int OK = 0;

    /** A refusal about the books, or a check that found them wanting. */
    public static final int REFUSED = 1;

    /** The arguments were wrong, or the command will not do what was asked as it was asked. */
    public static final int MISUSED = 2;

    /** Where each document number series starts on a new set of books. */
    public static final Map<String, Integer> STARTING_NUMBERS = startingNumbers();

    private static final String HELP = "Show this message and exit.";

    private final Terminal terminal;

    @Option(
            names = "--database",
            paramLabel = "TEXT",
            description = "The file the books live in. Read from the environment otherwise.")
    private String database;

    @Option(
            names = "--log-level",
            paramLabel = "[debug|info|warning|error]",
            converter = Level.class,
            description = "How much to say. Read from the environment otherwise.")
    private String logLevel;

    @Option(names = "--version", versionHelp = true, description = "Show the version and exit.")
    private boolean version;

    private Settings settings;
    private Database opened;

    Main(Terminal terminal) {
        this.terminal = terminal;
    }

    /** The console entry point. */
    public static void main(String... args) {
        System.exit(run(Terminal.system(), args));
    }

    /** Run one command line and answer its exit code. */
    public static int run(Terminal terminal, String... args) {
        Main main = new Main(terminal);
        CommandLine root = commandLine(main);
        try {
            return main.dispatch(root, args);
        } finally {
            main.close();
            terminal.out().flush();
            terminal.err().flush();
        }
    }

    /**
     * The command tree. A repeated option takes the last value given, an argument that starts with
     * {@code @} is an argument like any other, and every command answers {@code --help}.
     */
    static CommandLine commandLine(Main main) {
        CommandLine root = new CommandLine(main);
        root.setExpandAtFiles(false);
        root.setOverwrittenOptionsAllowed(true);
        root.setOut(main.terminal.out());
        root.setErr(main.terminal.err());
        addHelp(root);
        return root;
    }

    private static void addHelp(CommandLine command) {
        command.getCommandSpec()
                .addOption(OptionSpec.builder("--help")
                        .usageHelp(true)
                        .description(HELP)
                        .build());
        for (CommandLine sub : command.getSubcommands().values()) {
            addHelp(sub);
        }
    }

    private int dispatch(CommandLine root, String... args) {
        ParseResult parsed;
        try {
            parsed = root.parseArgs(args);
        } catch (ParameterException misused) {
            return misused(misused.getMessage());
        }
        ParseResult leaf = parsed;
        for (ParseResult level = parsed; level != null; level = level.subcommand()) {
            if (level.isUsageHelpRequested()) {
                level.commandSpec().commandLine().usage(terminal.out());
                return OK;
            }
            if (level.isVersionHelpRequested()) {
                level.commandSpec().commandLine().printVersionHelp(terminal.out());
                return OK;
            }
            leaf = level;
        }
        if (!(leaf.commandSpec().userObject() instanceof Action action)) {
            leaf.commandSpec().commandLine().usage(terminal.out());
            return OK;
        }
        try {
            begin();
            return action.call();
        } catch (CommandError misused) {
            return misused(misused.getMessage());
        } catch (DrawbarlineError refused) {
            say(terminal.err(), "error: " + refused.getMessage());
            return REFUSED;
        }
    }

    private int misused(String message) {
        say(terminal.err(), "error: " + message);
        return MISUSED;
    }

    /** Read the settings, lay the options over them and set the logging up, before any command runs. */
    private void begin() {
        Settings read = Settings.fromEnvironment(terminal.environment());
        if (database != null && !database.isEmpty()) {
            read = read.withDatabase(database);
        }
        if (logLevel != null && !logLevel.isEmpty()) {
            read = read.withLogLevel(logLevel);
        }
        Logs.configure(read, terminal.errBytes());
        settings = read;
    }

    /** What this run was started with, the options laid over the environment. */
    Settings settings() {
        return settings;
    }

    /** The books the settings name, opened the first time a command asks for them. */
    Database database() {
        if (opened == null) {
            opened = Database.open(settings);
        }
        return opened;
    }

    /** A unit of work on the books. It rolls back unless the command commits. */
    UnitOfWork work() {
        return database().unitOfWork();
    }

    /** Write one line of what the command produced. */
    void out(String line) {
        say(terminal.out(), line);
    }

    /** Write one line on standard error. */
    void err(String line) {
        say(terminal.err(), line);
    }

    /** Write text exactly as it is, with no line feed added. */
    void write(String text) {
        terminal.out().print(text);
        terminal.out().flush();
    }

    Terminal terminal() {
        return terminal;
    }

    private static void say(PrintWriter stream, String line) {
        stream.print(line);
        stream.print('\n');
        stream.flush();
    }

    private void close() {
        if (opened != null) {
            opened.close();
        }
    }

    private static Map<String, Integer> startingNumbers() {
        Map<String, Integer> numbers = new LinkedHashMap<>();
        numbers.put(Numbering.INVOICES, 5001);
        numbers.put(Numbering.STATEMENTS, 9001);
        numbers.put(Numbering.REPAIR_CARDS, 88001);
        numbers.put(Numbering.WAYBILLS, 40001);
        return Collections.unmodifiableMap(numbers);
    }

    /** One command's work: what it does once the arguments have been read, answering the exit code. */
    interface Action extends Callable<Integer> {

        @Override
        Integer call();
    }

    /** A log level, which has to be one the settings publish. */
    static final class Level implements ITypeConverter<String> {

        @Override
        public String convert(String value) {
            if (!Settings.LEVELS.contains(value)) {
                throw new TypeConversionException("'" + value + "' is not one of " + choices(Settings.LEVELS) + ".");
            }
            return value;
        }
    }

    /** Choices as an operator reads them: {@code 'debug', 'info', 'warning', 'error'}. */
    static String choices(List<String> names) {
        return names.stream().map(one -> "'" + one + "'").collect(Collectors.joining(", "));
    }

    /** Build the schema and open the document number series. */
    @Command(name = "init", description = "Build the schema and open the document number series.")
    static final class Init implements Action {

        @ParentCommand
        private Main main;

        @Override
        public Integer call() {
            Settings settings = main.settings();
            if (!settings.isUrl() && Files.exists(settings.storeFile())) {
                throw new CommandError(settings.database() + " is already there");
            }
            main.database().migrate();
            try (UnitOfWork work = main.work()) {
                for (String name : Numbering.SERIES) {
                    work.numbering().start(name, STARTING_NUMBERS.get(name));
                }
                work.commit();
            }
            main.out("built " + settings.database());
            return OK;
        }
    }

    /** What this process thinks it is, and where each setting can be set. */
    @Command(name = "settings", description = "What this process thinks it is, and where each setting can be set.")
    static final class ShowSettings implements Action {

        @ParentCommand
        private Main main;

        @Override
        public Integer call() {
            Settings settings = main.settings();
            main.out("database   " + settings.database());
            main.out("url        " + settings.url());
            main.out("home road  " + settings.homeRoad());
            main.out("log level  " + settings.logLevel());
            main.out("echo sql   " + (settings.echoSql() ? "True" : "False"));
            main.out("");
            for (String name : Settings.names()) {
                main.out("  " + name);
            }
            return OK;
        }
    }

    /** How much is on the books. */
    @Command(name = "status", description = "How much is on the books.")
    static final class Status implements Action {

        @ParentCommand
        private Main main;

        @Override
        public Integer call() {
            int cars;
            int events;
            int postings;
            List<Period> closed;
            try (UnitOfWork work = main.work()) {
                cars = work.fleet().size();
                events = work.events().size();
                postings = work.ledger().size();
                closed = work.periods().closedPeriods();
            }
            main.out("cars      " + cars);
            main.out("events    " + events);
            main.out("postings  " + postings);
            main.out("closed    " + closed.size() + " months");
            if (!closed.isEmpty()) {
                main.out("latest    " + closed.get(closed.size() - 1));
            }
            return OK;
        }
    }

    /** Whether these books are ready to be worked. */
    @Command(name = "doctor", description = "Whether these books are ready to be worked.")
    static final class Doctor implements Action {

        @ParentCommand
        private Main main;

        @Option(names = "--on", paramLabel = "TEXT", description = "The day to check against. Today by default.")
        private String on;

        @Override
        public Integer call() {
            LocalDate day = on == null || on.isEmpty() ? Clock.today() : Readers.aDate(on);
            List<Finding> findings = Health.examine(main.database(), day);
            for (Finding finding : findings) {
                main.out(finding.toString());
            }
            main.out("");
            if (Health.ready(findings)) {
                main.out("ready to work");
                return OK;
            }
            main.err("not ready, see the lines marked bad");
            return REFUSED;
        }
    }

    /**
     * Run the HTTP service over the books the settings name until it is stopped, by a signal or an
     * interrupt. The one command that starts the service, which opens the books itself and closes
     * them when it stops.
     */
    @Command(name = "serve", description = "Run the HTTP service until it is stopped.")
    static final class Serve implements Action {

        @ParentCommand
        private Main main;

        @Option(
                names = "--host",
                paramLabel = "TEXT",
                defaultValue = "127.0.0.1",
                description = "The address to listen on.")
        private String host;

        @Option(
                names = "--port",
                paramLabel = "INTEGER",
                defaultValue = "8000",
                description = "The port to listen on. 0 takes any free one.")
        private int port;

        @Override
        public Integer call() {
            Settings settings = main.settings();
            CountDownLatch closed = new CountDownLatch(1);
            try (ConfigurableApplicationContext service =
                    DrawbarlineApplication.run(settings, "--server.port=" + port, "--server.address=" + host)) {
                service.addApplicationListener((ApplicationListener<ContextClosedEvent>) stopped -> closed.countDown());
                int listening =
                        ((WebServerApplicationContext) service).getWebServer().getPort();
                main.out("serving " + settings.database() + " on http://" + host + ":" + listening);
                if (service.isActive()) {
                    closed.await();
                }
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
            }
            return OK;
        }
    }
}
