package io.github.rkaushikdev.drawbarline.cli;

import io.github.rkaushikdev.drawbarline.clock.Clock;
import io.github.rkaushikdev.drawbarline.persistence.UnitOfWork;
import io.github.rkaushikdev.drawbarline.reporting.Reporting;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * What the office reads.
 *
 * <p>Every one of these opens the books, runs the report the reporting layer already knows how to
 * produce, and prints the lines. Nothing is computed here and nothing is formatted here, which is
 * why a report printed from the command line and the same report served over HTTP cannot disagree.
 *
 * <p>They all read and none of them commit, so running a report against the live books during a
 * close is safe.
 */
@Command(
        name = "report",
        description = "What the office reads.",
        subcommands = {
            ReportCommands.Age.class,
            ReportCommands.Annual.class,
            ReportCommands.Balance.class,
            ReportCommands.CarHire.class,
            ReportCommands.Daily.class,
            ReportCommands.Demurrage.class,
            ReportCommands.Forecast.class,
            ReportCommands.Interchange.class,
            ReportCommands.OnHand.class,
            ReportCommands.ClosePackage.class,
            ReportCommands.Repairs.class,
            ReportCommands.Shop.class,
            ReportCommands.Utilisation.class
        })
final class ReportCommands {

    @ParentCommand
    private Main main;

    /** Run one report in a unit of work that writes nothing, and print its lines. */
    private void show(Function<UnitOfWork, List<String>> report) {
        List<String> lines;
        try (UnitOfWork work = main.work()) {
            lines = report.apply(work);
        }
        for (String line : lines) {
            main.out(line);
        }
    }

    private static Optional<String> given(String text) {
        return Optional.ofNullable(text);
    }

    /** Car hire liability, by road or car by car. */
    @Command(name = "carhire", description = "Car hire liability, by road or car by car.")
    static final class CarHire implements Main.Action {

        @ParentCommand
        private ReportCommands report;

        @Parameters(paramLabel = "MONTH")
        private String month;

        @Option(names = "--road", paramLabel = "TEXT", description = "Print the car detail for one road.")
        private String road;

        @Override
        public Integer call() {
            report.show(work -> Reporting.carHire(work, Readers.aMonth(month), road == null ? "" : road));
            return Main.OK;
        }
    }

    /** Turn time against standing time, car by car. */
    @Command(name = "utilisation", description = "Turn time against standing time, car by car.")
    static final class Utilisation implements Main.Action {

        @ParentCommand
        private ReportCommands report;

        @Parameters(paramLabel = "MONTH")
        private String month;

        @Override
        public Integer call() {
            report.show(work -> Reporting.utilisation(work, Readers.aMonth(month)));
            return Main.OK;
        }
    }

    /** Detention, car by car or customer by customer. */
    @Command(name = "demurrage", description = "Detention, car by car or customer by customer.")
    static final class Demurrage implements Main.Action {

        @ParentCommand
        private ReportCommands report;

        @Parameters(paramLabel = "MONTH")
        private String month;

        @Option(names = "--by-shipper", description = "Summarise by customer.")
        private boolean byShipper;

        @Override
        public Integer call() {
            report.show(work -> Reporting.demurrage(work, Readers.aMonth(month), byShipper));
            return Main.OK;
        }
    }

    /** What crossed each junction, both ways. */
    @Command(name = "interchange", description = "What crossed each junction, both ways.")
    static final class Interchange implements Main.Action {

        @ParentCommand
        private ReportCommands report;

        @Parameters(paramLabel = "MONTH")
        private String month;

        @Override
        public Integer call() {
            report.show(work -> Reporting.interchange(work, Readers.aMonth(month)));
            return Main.OK;
        }
    }

    /** Every car we have taken and not handed back. */
    @Command(name = "onhand", description = "Every car we have taken and not handed back.")
    static final class OnHand implements Main.Action {

        @ParentCommand
        private ReportCommands report;

        @Option(names = "--at", paramLabel = "TEXT", description = "A moment in utc, yyyy-mm-dd. Now by default.")
        private String at;

        @Override
        public Integer call() {
            report.show(work -> Reporting.onHand(work, Readers.aMomentUtc(given(at))));
            return Main.OK;
        }
    }

    /** How old the fleet is and what leaves interchange service soon. */
    @Command(name = "age", description = "How old the fleet is and what leaves interchange service soon.")
    static final class Age implements Main.Action {

        @ParentCommand
        private ReportCommands report;

        @Option(names = "--on", paramLabel = "TEXT", description = "A date, yyyy-mm-dd. Today by default.")
        private String on;

        @Override
        public Integer call() {
            LocalDate day = on == null || on.isEmpty() ? Clock.today() : Readers.aDate(on);
            report.show(work -> Reporting.fleetAge(work, day));
            return Main.OK;
        }
    }

    /** Repair billing for a month, or what is still under exception. */
    @Command(name = "repairs", description = "Repair billing for a month, or what is still under exception.")
    static final class Repairs implements Main.Action {

        @ParentCommand
        private ReportCommands report;

        @Parameters(paramLabel = "MONTH")
        private String month;

        @Option(names = "--exceptions", description = "What is open rather than what was billed.")
        private boolean exceptions;

        @Override
        public Integer call() {
            report.show(work -> Reporting.repairs(work, Readers.aMonth(month), exceptions));
            return Main.OK;
        }
    }

    /** Which cars are bad ordered and how long they have been down. */
    @Command(name = "shop", description = "Which cars are bad ordered and how long they have been down.")
    static final class Shop implements Main.Action {

        @ParentCommand
        private ReportCommands report;

        @Option(names = "--at", paramLabel = "TEXT", description = "A moment in utc, yyyy-mm-dd. Now by default.")
        private String at;

        @Override
        public Integer call() {
            report.show(work -> Reporting.shop(work, Readers.aMomentUtc(given(at))));
            return Main.OK;
        }
    }

    /** The morning sheet for one day. */
    @Command(name = "daily", description = "The morning sheet for one day.")
    static final class Daily implements Main.Action {

        @ParentCommand
        private ReportCommands report;

        @Option(names = "--on", required = true, paramLabel = "TEXT", description = "The day, yyyy-mm-dd.")
        private String on;

        @Option(names = "--at", paramLabel = "TEXT", description = "A moment in utc. The same day by default.")
        private String at;

        @Override
        public Integer call() {
            LocalDate day = Readers.aDate(on);
            Instant moment = Readers.aMomentUtc(Optional.of(at == null || at.isEmpty() ? on : at));
            report.show(work -> Reporting.daily(work, day, moment));
            return Main.OK;
        }
    }

    /** What the cars standing here cost a day if nothing moves. */
    @Command(name = "forecast", description = "What the cars standing here cost a day if nothing moves.")
    static final class Forecast implements Main.Action {

        @ParentCommand
        private ReportCommands report;

        @Option(names = "--at", paramLabel = "TEXT", description = "A moment in utc, yyyy-mm-dd. Now by default.")
        private String at;

        @Option(
                names = "--days",
                paramLabel = "INTEGER",
                defaultValue = "7",
                showDefaultValue = Visibility.ALWAYS,
                description = "How far to run it out.")
        private int days;

        @Override
        public Integer call() {
            report.show(work -> Reporting.forecast(work, Readers.aMomentUtc(given(at)), days));
            return Main.OK;
        }
    }

    /** The trial balance, which proves the other reports. */
    @Command(name = "balance", description = "The trial balance, which proves the other reports.")
    static final class Balance implements Main.Action {

        @ParentCommand
        private ReportCommands report;

        @Parameters(paramLabel = "MONTH")
        private String month;

        @Option(names = "--by-kind", description = "Split the month by kind of charge.")
        private boolean byKind;

        @Override
        public Integer call() {
            report.show(work -> Reporting.trialBalance(work, Readers.aMonth(month), byKind));
            return Main.OK;
        }
    }

    /** The year on one page. */
    @Command(name = "annual", description = "The year on one page.")
    static final class Annual implements Main.Action {

        @ParentCommand
        private ReportCommands report;

        @Parameters(paramLabel = "YEAR")
        private int year;

        @Option(names = "--road", paramLabel = "TEXT", description = "One road rather than the whole pool.")
        private String road;

        @Override
        public Integer call() {
            report.show(work -> Reporting.annual(work, year, road == null ? "" : road));
            return Main.OK;
        }
    }

    /** Everything that goes out after a close, in one document. */
    @Command(name = "package", description = "Everything that goes out after a close, in one document.")
    static final class ClosePackage implements Main.Action {

        @ParentCommand
        private ReportCommands report;

        @Parameters(paramLabel = "MONTH")
        private String month;

        @Override
        public Integer call() {
            report.show(work -> Reporting.closePackage(work, Readers.aMonth(month)));
            return Main.OK;
        }
    }
}
