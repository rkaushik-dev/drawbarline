package io.github.rkaushikdev.drawbarline.cli;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import io.github.rkaushikdev.drawbarline.clock.Clock;
import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.events.EventLog;
import io.github.rkaushikdev.drawbarline.interchange.Header;
import io.github.rkaushikdev.drawbarline.interchange.Problem;
import io.github.rkaushikdev.drawbarline.interchange.Reconcile;
import io.github.rkaushikdev.drawbarline.interchange.Reconciliation;
import io.github.rkaushikdev.drawbarline.interchange.Sheet;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.network.Station;
import io.github.rkaushikdev.drawbarline.persistence.UnitOfWork;
import io.github.rkaushikdev.drawbarline.reference.Reference;
import io.github.rkaushikdev.drawbarline.reports.ReconcileReport;
import io.github.rkaushikdev.drawbarline.util.Literal;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * The files the connections send and the ones we send back.
 *
 * <p>Receiving is the one command in here that anybody uses in anger, and it is built around the
 * fact that the files are not always good. It reads the whole file, reports every line it could not
 * read, and then refuses to record anything unless the caller says to take the good half. A file
 * with three bad lines out of two hundred is a file somebody has to look at, not one to load
 * quietly.
 *
 * <p>Sending is the other direction and it is dull, which is how it should be.
 */
@Command(
        name = "interchange",
        description = "The files the connections send and the ones we send back.",
        subcommands = {
            InterchangeCommands.Receive.class,
            InterchangeCommands.ReconcileSheet.class,
            InterchangeCommands.Send.class
        })
final class InterchangeCommands {

    @ParentCommand
    private Main main;

    private static Sheet read(Path file) {
        try {
            return Sheet.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException unreadable) {
            throw new CommandError(Readers.name(file) + " cannot be read as text");
        }
    }

    /** Read a sheet and record what is on it. */
    @Command(name = "receive", description = "Read a sheet and record what is on it.")
    static final class Receive implements Main.Action {

        @ParentCommand
        private InterchangeCommands interchange;

        @Parameters(paramLabel = "FILE", converter = Readers.Sheet.class)
        private Path file;

        @Option(names = "--partial", description = "Take the records that read even though some did not.")
        private boolean partial;

        @Override
        public Integer call() {
            Main main = interchange.main;
            String name = Readers.name(file);
            Sheet sheet = read(file);
            for (Problem problem : sheet.problems()) {
                main.err("  " + problem);
            }
            Header header = sheet.header().orElseThrow(() -> new CommandError(name + " has no header"));
            if (!sheet.isCounted()) {
                main.err("  trailer says " + Literal.of(sheet.declared()) + " and "
                        + sheet.events().size() + " read");
            }
            if ((!sheet.problems().isEmpty() || !sheet.isCounted()) && !partial) {
                throw new CommandError(name + " did not read cleanly, nothing taken");
            }
            try (UnitOfWork work = main.work()) {
                for (Event event : sheet.events()) {
                    try {
                        work.events().record(event);
                    } catch (DrawbarlineError bad) {
                        throw new CommandError(event.car() + ": " + bad.getMessage());
                    }
                }
                work.commit();
            }
            main.out("took " + sheet.events().size() + " records off " + name + " from " + header.road() + " at "
                    + header.junction());
            return Main.OK;
        }
    }

    /** Write the day's interchange with one road as a sheet. */
    @Command(name = "send", description = "Write the day's interchange with one road as a sheet.")
    static final class Send implements Main.Action {

        @ParentCommand
        private InterchangeCommands interchange;

        @Option(names = "--road", required = true, paramLabel = "TEXT", description = "The road the file goes to.")
        private String road;

        @Option(
                names = "--junction",
                required = true,
                paramLabel = "TEXT",
                description = "The station code we hand over at.")
        private String junction;

        @Option(names = "--day", required = true, paramLabel = "TEXT", description = "The day it covers, yyyy-mm-dd.")
        private String day;

        @Option(
                names = "--sequence",
                paramLabel = "INTEGER",
                defaultValue = "1",
                showDefaultValue = Visibility.ALWAYS,
                description = "File sequence number.")
        private int sequence;

        @Option(
                names = "--out",
                paramLabel = "FILE",
                converter = Readers.Target.class,
                description = "Where to write it. Standard output by default.")
        private Path out;

        @Override
        public Integer call() {
            Main main = interchange.main;
            ReportingMark mark = ReportingMark.parse(road);
            String station = Text.upper(Text.strip(junction));
            LocalDate on = Readers.aDate(day);
            Station place = Reference.drawbarLine().station(station);
            EventLog log;
            try (UnitOfWork work = main.work()) {
                log = work.events().log();
            }
            List<Event> events = log.atStation(station).stream()
                    .filter(one -> one.road().equals(Optional.of(mark)))
                    .filter(one ->
                            Clock.toLocal(place, one.when()).toLocalDate().equals(on))
                    .toList();
            String text = Sheet.render(new Header(mark, station, on, sequence), events);
            if (out == null) {
                main.write(text);
                return Main.OK;
            }
            try {
                Files.writeString(out, text, StandardCharsets.UTF_8);
            } catch (IOException unwritable) {
                throw new CommandError(Readers.name(out) + " cannot be written");
            }
            main.out("wrote " + events.size() + " records to " + Readers.name(out));
            return Main.OK;
        }
    }

    /** Set a sheet they sent us against what we already have. */
    @Command(name = "reconcile", description = "Set a sheet they sent us against what we already have.")
    static final class ReconcileSheet implements Main.Action {

        @ParentCommand
        private InterchangeCommands interchange;

        @Parameters(paramLabel = "FILE", converter = Readers.Sheet.class)
        private Path file;

        @Option(
                names = "--tolerance",
                paramLabel = "INTEGER",
                defaultValue = "30",
                showDefaultValue = Visibility.ALWAYS,
                description = "Minutes two clocks may differ by before it is a difference.")
        private int tolerance;

        @Override
        public Integer call() {
            Main main = interchange.main;
            String name = Readers.name(file);
            Sheet sheet = read(file);
            Header header = sheet.header().orElseThrow(() -> new CommandError(name + " has no header"));
            for (Problem problem : sheet.problems()) {
                main.err("  " + problem);
            }
            List<Event> theirs = sheet.events();
            if (theirs.isEmpty()) {
                throw new CommandError(name + " has no records to compare");
            }
            Set<LocalDate> days = theirs.stream().map(ReconcileSheet::dayOf).collect(Collectors.toSet());
            EventLog log;
            try (UnitOfWork work = main.work()) {
                log = work.events().log();
            }
            List<Event> ours = log.atStation(header.junction()).stream()
                    .filter(one -> one.road().equals(Optional.of(header.road())))
                    .filter(one -> days.contains(dayOf(one)))
                    .toList();
            Reconciliation found = Reconcile.compare(ours, theirs, Duration.ofMinutes(tolerance));
            for (String line : ReconcileReport.reconciliationReport(found, header.road(), name)) {
                main.out(line);
            }
            return found.isClean() ? Main.OK : Main.REFUSED;
        }

        /** The day an event happened on in UTC, which is the day both roads' records are filed under. */
        private static LocalDate dayOf(Event event) {
            return LocalDate.ofInstant(event.when(), ZoneOffset.UTC);
        }
    }
}
