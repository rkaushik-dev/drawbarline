package io.github.rkaushikdev.drawbarline.cli;

import io.github.rkaushikdev.drawbarline.audit.Entry;
import io.github.rkaushikdev.drawbarline.demurrage.Invoice;
import io.github.rkaushikdev.drawbarline.export.Export;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.persistence.UnitOfWork;
import io.github.rkaushikdev.drawbarline.service.Service;
import io.github.rkaushikdev.drawbarline.settlement.CloseResult;
import io.github.rkaushikdev.drawbarline.settlement.Ledger;
import io.github.rkaushikdev.drawbarline.settlement.Period;
import io.github.rkaushikdev.drawbarline.settlement.Reopening;
import io.github.rkaushikdev.drawbarline.settlement.Statement;
import io.github.rkaushikdev.drawbarline.settlement.StatementLine;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * The month close, the statements, the audit trail and the export.
 *
 * <p>The close is the one command in here that changes a lot at once, so it prints what it did
 * rather than saying nothing and exiting zero. A clerk running it at the start of the month wants to
 * see the car hire, the reclaims, the demurrage and the statements, and wants to see them before
 * anybody sends them out.
 *
 * <p>A dry run does everything except commit. That is not a separate code path, it is the same run
 * with the commit left off, which is the only kind of dry run worth having.
 */
final class CloseCommands {

    private CloseCommands() {}

    /** Work a month through and shut it. */
    @Command(name = "close", description = "Work a month through and shut it.")
    static final class CloseMonth implements Main.Action {

        @ParentCommand
        private Main main;

        @Parameters(paramLabel = "MONTH")
        private String month;

        @Option(
                names = "--issued",
                required = true,
                paramLabel = "TEXT",
                description = "The day the statements are cut, yyyy-mm-dd.")
        private String issued;

        @Option(names = "--dry-run", description = "Work the month out and change nothing.")
        private boolean dryRun;

        @Option(
                names = "--by",
                paramLabel = "TEXT",
                defaultValue = Service.UNSIGNED,
                description = "Whose initials go on it.")
        private String actor;

        @Override
        public Integer call() {
            Period period = Readers.aMonth(month);
            LocalDate on = Readers.aDate(issued);
            if (on.isBefore(period.lastDay())) {
                throw new CommandError("a month is closed after it ends");
            }
            try (UnitOfWork work = main.work()) {
                CloseResult result = Service.runClose(work, period, on, actor);
                main.out("month      " + result.period());
                main.out("car hire   " + result.carHire());
                main.out("reclaimed  " + result.reclaimed());
                main.out("demurrage  " + result.demurrage());
                main.out("postings   " + result.posted());
                for (Statement statement : result.statements()) {
                    main.out("statement  " + statement);
                }
                for (Invoice invoice : result.invoices()) {
                    main.out("invoice    " + invoice);
                }
                if (dryRun) {
                    main.out("dry run, nothing written");
                    return Main.OK;
                }
                work.commit();
            }
            main.out("closed " + period);
            return Main.OK;
        }
    }

    /** Print the statement for one road and one month. */
    @Command(name = "statement", description = "Print the statement for one road and one month.")
    static final class PrintStatement implements Main.Action {

        @ParentCommand
        private Main main;

        @Parameters(index = "0", paramLabel = "ROAD")
        private String road;

        @Parameters(index = "1", paramLabel = "MONTH")
        private String month;

        @Option(
                names = "--number",
                paramLabel = "INTEGER",
                defaultValue = "1",
                showDefaultValue = picocli.CommandLine.Help.Visibility.ALWAYS,
                description = "Statement number to print on it.")
        private int number;

        @Override
        public Integer call() {
            Period period = Readers.aMonth(month);
            ReportingMark mark = ReportingMark.parse(road);
            Ledger ledger;
            try (UnitOfWork work = main.work()) {
                ledger = work.ledger().ledger();
            }
            Statement found = Statement.cut(ledger, mark, period, number, period.lastDay());
            main.out("road      " + found.road());
            main.out("month     " + found.period());
            main.out("opening   " + found.opening());
            for (StatementLine line : found.lines()) {
                main.out("  " + line);
            }
            main.out("closing   " + found.closing());
            main.out("          " + found.direction());
            return Main.OK;
        }
    }

    /** Who decided what, newest first. */
    @Command(name = "audit", description = "Who decided what, newest first.")
    static final class ShowAudit implements Main.Action {

        @ParentCommand
        private Main main;

        @Option(
                names = "--limit",
                paramLabel = "INTEGER",
                defaultValue = "20",
                showDefaultValue = picocli.CommandLine.Help.Visibility.ALWAYS,
                description = "How many to show.")
        private int limit;

        @Option(names = "--by", paramLabel = "TEXT", description = "Only one person.")
        private String actor;

        @Override
        public Integer call() {
            List<Entry> found;
            try (UnitOfWork work = main.work()) {
                found = actor == null || actor.isEmpty()
                        ? work.audit().entries(limit)
                        : work.audit().byActor(actor);
            }
            for (Entry entry : found) {
                main.out(entry.toString());
            }
            if (found.isEmpty()) {
                main.out("nothing on the trail yet");
            }
            return Main.OK;
        }
    }

    /** Which months are shut and which have been opened again. */
    @Command(name = "periods", description = "Which months are shut and which have been opened again.")
    static final class Periods implements Main.Action {

        @ParentCommand
        private Main main;

        @Override
        public Integer call() {
            List<Period> closed;
            List<Reopening> reopened;
            try (UnitOfWork work = main.work()) {
                closed = work.periods().closedPeriods();
                reopened = work.periods().reopenings();
            }
            for (Period period : closed) {
                main.out("closed    " + period);
            }
            for (Reopening one : reopened) {
                main.out("reopened  " + one.period() + " on " + one.on() + ", " + one.reason());
            }
            if (closed.isEmpty() && reopened.isEmpty()) {
                main.out("nothing closed yet");
            }
            return Main.OK;
        }
    }

    /** Open a closed month again, with the reason on file. */
    @Command(name = "reopen", description = "Open a closed month again, with the reason on file.")
    static final class Reopen implements Main.Action {

        @ParentCommand
        private Main main;

        @Parameters(paramLabel = "MONTH")
        private String month;

        @Option(
                names = "--on",
                required = true,
                paramLabel = "TEXT",
                description = "The day it was opened again, yyyy-mm-dd.")
        private String on;

        @Option(names = "--reason", required = true, paramLabel = "TEXT", description = "Why it had to be.")
        private String reason;

        @Option(
                names = "--by",
                paramLabel = "TEXT",
                defaultValue = Service.UNSIGNED,
                description = "Whose initials go on it.")
        private String actor;

        @Override
        public Integer call() {
            Period period = Readers.aMonth(month);
            try (UnitOfWork work = main.work()) {
                Service.reopenPeriod(work, period, Readers.aDate(on), reason, actor);
                work.commit();
            }
            main.out("reopened " + period);
            return Main.OK;
        }
    }

    /** Write a whole month as JSON, for the auditors. */
    @Command(name = "export", description = "Write a whole month as json, for the auditors.")
    static final class ExportMonth implements Main.Action {

        @ParentCommand
        private Main main;

        @Parameters(paramLabel = "MONTH")
        private String month;

        @Option(
                names = "--out",
                paramLabel = "FILE",
                converter = Readers.Target.class,
                description = "Where to write it. Standard output by default.")
        private Path out;

        @Override
        public Integer call() {
            Period period = Readers.aMonth(month);
            Map<String, Object> bundle;
            try (UnitOfWork work = main.work()) {
                bundle = Export.month(work, period);
            }
            String text = JsonText.of(bundle) + "\n";
            if (out == null) {
                main.write(text);
                return Main.OK;
            }
            try {
                Files.writeString(out, text, StandardCharsets.UTF_8);
            } catch (IOException unwritable) {
                throw new CommandError(Readers.name(out) + " cannot be written");
            }
            String counted = Export.counts(bundle).entrySet().stream()
                    .map(entry -> entry.getValue() + " " + entry.getKey())
                    .collect(Collectors.joining(", "));
            main.out("wrote " + period + " to " + Readers.name(out) + ": " + counted);
            return Main.OK;
        }
    }
}
