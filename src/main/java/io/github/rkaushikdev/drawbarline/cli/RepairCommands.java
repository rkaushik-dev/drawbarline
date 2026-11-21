package io.github.rkaushikdev.drawbarline.cli;

import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.persistence.UnitOfWork;
import io.github.rkaushikdev.drawbarline.repairs.Defect;
import io.github.rkaushikdev.drawbarline.repairs.Depreciation;
import io.github.rkaushikdev.drawbarline.repairs.Ground;
import io.github.rkaushikdev.drawbarline.repairs.JobCode;
import io.github.rkaushikdev.drawbarline.repairs.LaborRate;
import io.github.rkaushikdev.drawbarline.repairs.Objection;
import io.github.rkaushikdev.drawbarline.repairs.Outcome;
import io.github.rkaushikdev.drawbarline.repairs.PriceLine;
import io.github.rkaushikdev.drawbarline.repairs.PricedCard;
import io.github.rkaushikdev.drawbarline.repairs.PricedLine;
import io.github.rkaushikdev.drawbarline.repairs.RepairCard;
import io.github.rkaushikdev.drawbarline.repairs.Resolution;
import io.github.rkaushikdev.drawbarline.repairs.WhyMade;
import io.github.rkaushikdev.drawbarline.util.Literal;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Repair cards and what they cost.
 *
 * <p>A card is filed with one option a line, written as job:why:quantity:years. It looks terse and
 * it is what the shop foreman already writes on the card, so it is what a clerk keying it can copy
 * without translating anything.
 *
 * <p>Pricing a card is a separate command from filing it, because the price master changes and a
 * card filed today may be priced tomorrow at a rate that was already in force. Filing and pricing
 * being two steps is how the office works, and pretending otherwise would mean keying cards again
 * after a filing.
 *
 * <p>The billing report is under the report group with every other report rather than here,
 * because a clerk looking for a report looks in one place.
 */
@Command(
        name = "repairs",
        description = "Repair cards and what they cost.",
        subcommands = {
            RepairCommands.Answer.class,
            RepairCommands.FileCard.class,
            RepairCommands.SetDepreciation.class,
            RepairCommands.TakeException.class,
            RepairCommands.FileLabor.class,
            RepairCommands.LoadPrices.class,
            RepairCommands.ShowCard.class
        })
final class RepairCommands {

    /** How a line of a card is written on the command line. */
    static final String DEFECT_FORM = "job:why:quantity:years";

    @ParentCommand
    private Main main;

    /** Read {@code 4312:01:2:5} as a line of a card; the quantity and the part's age may be left off. */
    static Defect aDefect(String text) {
        String[] parts = text.split(":", -1);
        if (parts.length < 2 || parts.length > 4) {
            throw new CommandError(Literal.of(text) + " is not a line, write it as " + DEFECT_FORM);
        }
        int quantity = parts.length > 2 ? Readers.whole(parts[2]) : 1;
        int years = parts.length > 3 ? Readers.whole(parts[3]) : 0;
        return new Defect(
                new JobCode(Text.strip(parts[0])),
                new WhyMade(Text.strip(parts[1])),
                quantity,
                Optional.empty(),
                years,
                "");
    }

    /** Load the price master from job,effective,labor_hundredths,material_cents. */
    @Command(name = "price", description = "Load the price master from job,effective,labor_hundredths,material_cents.")
    static final class LoadPrices implements Main.Action {

        @ParentCommand
        private RepairCommands repairs;

        @Parameters(paramLabel = "SHEET", converter = Readers.Sheet.class)
        private Path sheet;

        @Override
        public Integer call() {
            int taken = 0;
            try (UnitOfWork work = repairs.main.work()) {
                for (Map<String, String> row : Readers.rowsOf(sheet)) {
                    JobCode job = new JobCode(Text.strip(row.getOrDefault("job", "")));
                    LocalDate effective = Readers.aDate(row.getOrDefault("effective", ""));
                    int labor = Readers.whole(row.getOrDefault("labor_hundredths", ""));
                    int material = Readers.whole(row.getOrDefault("material_cents", ""));
                    work.repairPricing().addPrice(new PriceLine(job, effective, labor, material));
                    taken++;
                }
                work.commit();
            }
            repairs.main.out("priced " + taken + " jobs");
            return Main.OK;
        }
    }

    /** File how fast one family of parts is used up. */
    @Command(name = "depreciation", description = "File how fast one family of parts is used up.")
    static final class SetDepreciation implements Main.Action {

        @ParentCommand
        private RepairCommands repairs;

        @Parameters(paramLabel = "FAMILY")
        private String family;

        @Option(names = "--percent", required = true, paramLabel = "INTEGER", description = "Percent used up a year.")
        private int percent;

        @Option(names = "--floor", required = true, paramLabel = "INTEGER", description = "The share always payable.")
        private int floor;

        @Override
        public Integer call() {
            try (UnitOfWork work = repairs.main.work()) {
                work.repairPricing().setDepreciation(new Depreciation(Text.strip(family), percent, floor));
                work.commit();
            }
            repairs.main.out("family " + family + " loses " + percent + " a year down to " + floor);
            return Main.OK;
        }
    }

    /** File what one road charges an hour. */
    @Command(name = "labor", description = "File what one road charges an hour.")
    static final class FileLabor implements Main.Action {

        @ParentCommand
        private RepairCommands repairs;

        @Parameters(paramLabel = "ROAD")
        private String road;

        @Option(
                names = "--effective",
                required = true,
                paramLabel = "TEXT",
                description = "The day the rate takes effect.")
        private String effective;

        @Option(names = "--cents", required = true, paramLabel = "INTEGER", description = "Cents an hour.")
        private int cents;

        @Override
        public Integer call() {
            try (UnitOfWork work = repairs.main.work()) {
                work.repairPricing()
                        .fileLabor(new LaborRate(ReportingMark.parse(road), Readers.aDate(effective), cents));
                work.commit();
            }
            repairs.main.out(Text.upper(road) + " at " + new Money(cents) + " an hour from " + effective);
            return Main.OK;
        }
    }

    /** File a repair card. */
    @Command(name = "card", description = "File a repair card.")
    static final class FileCard implements Main.Action {

        @ParentCommand
        private RepairCommands repairs;

        @Parameters(index = "0", paramLabel = "NUMBER")
        private int number;

        @Parameters(index = "1", paramLabel = "CAR")
        private String car;

        @Option(names = "--by", required = true, paramLabel = "TEXT", description = "The road that did the work.")
        private String road;

        @Option(names = "--on", required = true, paramLabel = "TEXT", description = "The day it was done.")
        private String on;

        @Option(names = "--at", required = true, paramLabel = "TEXT", description = "Where it was done.")
        private String station;

        @Option(
                names = "--defect",
                required = true,
                paramLabel = "TEXT",
                description = "A line, as " + DEFECT_FORM + ".")
        private List<String> defects;

        @Override
        public Integer call() {
            CarMark mark = CarMark.parse(car);
            ReportingMark performedBy = ReportingMark.parse(road);
            LocalDate day = Readers.aDate(on);
            String where = Text.upper(Text.strip(station));
            List<Defect> lines = new ArrayList<>();
            for (String one : defects) {
                lines.add(aDefect(one));
            }
            RepairCard card = new RepairCard(number, mark, performedBy, day, where, lines);
            try (UnitOfWork work = repairs.main.work()) {
                work.cards().file(card);
                work.commit();
            }
            repairs.main.out("filed " + card);
            return Main.OK;
        }
    }

    /** A card with the money on it. */
    @Command(name = "show", description = "A card with the money on it.")
    static final class ShowCard implements Main.Action {

        @ParentCommand
        private RepairCommands repairs;

        @Parameters(paramLabel = "NUMBER")
        private int number;

        @Override
        public Integer call() {
            RepairCard card;
            PricedCard priced;
            try (UnitOfWork work = repairs.main.work()) {
                card = work.cards().get(number);
                priced = PricedCard.price(
                        card,
                        work.repairPricing().master(),
                        work.repairPricing().laborRates());
            }
            Main main = repairs.main;
            main.out(card.toString());
            List<PricedLine> lines = priced.lines();
            for (int at = 0; at < lines.size(); at++) {
                PricedLine line = lines.get(at);
                main.out("  " + at + "  " + line.defect().job() + " why "
                        + line.defect().why() + " "
                        + line.labor() + " labor " + line.material() + " material "
                        + "to " + line.answeredBy().text());
            }
            main.out("total      " + priced.total());
            main.out("to owner   " + priced.billedToOwner());
            main.out("their cost " + priced.borneByTheRoad());
            return Main.OK;
        }
    }

    /** Take exception to one line of a card. */
    @Command(name = "except", description = "Take exception to one line of a card.")
    static final class TakeException implements Main.Action {

        @ParentCommand
        private RepairCommands repairs;

        @Parameters(index = "0", paramLabel = "NUMBER")
        private int number;

        @Parameters(index = "1", paramLabel = "LINE")
        private int line;

        @Option(names = "--ground", required = true, paramLabel = "TEXT", description = "Why the line is wrong.")
        private String ground;

        @Option(names = "--cents", required = true, paramLabel = "INTEGER", description = "How much is disputed.")
        private int cents;

        @Option(names = "--on", required = true, paramLabel = "TEXT", description = "The day it is filed.")
        private String on;

        @Override
        public Integer call() {
            Ground why = groundNamed(ground);
            Objection objection = new Objection(number, line, why, new Money(cents), Readers.aDate(on));
            try (UnitOfWork work = repairs.main.work()) {
                work.cards().takeException(objection);
                work.commit();
            }
            repairs.main.out("filed " + objection);
            return Main.OK;
        }

        private static Ground groundNamed(String name) {
            String wanted = Text.upper(Text.strip(name));
            return Arrays.stream(Ground.values())
                    .filter(one -> one.name().equals(wanted))
                    .findFirst()
                    .orElseThrow(() -> new CommandError("no such ground, try one of "
                            + Arrays.stream(Ground.values())
                                    .map(one -> Text.lower(one.name()))
                                    .collect(Collectors.joining(", "))));
        }
    }

    /** Answer an exception by allowing some or none of it. */
    @Command(name = "answer", description = "Answer an exception by allowing some or none of it.")
    static final class Answer implements Main.Action {

        @ParentCommand
        private RepairCommands repairs;

        @Parameters(index = "0", paramLabel = "NUMBER")
        private int number;

        @Parameters(index = "1", paramLabel = "LINE")
        private int line;

        @Option(names = "--allow", required = true, paramLabel = "INTEGER", description = "Cents allowed.")
        private int allow;

        @Override
        public Integer call() {
            Outcome outcome;
            try (UnitOfWork work = repairs.main.work()) {
                Objection objection = work.cards().openObjections().stream()
                        .filter(one -> one.cardNumber() == number && one.line() == line)
                        .findFirst()
                        .orElseThrow(() -> new CommandError("no open exception on card " + number + " line " + line));
                Money allowed = new Money(allow);
                if (allowed.isZero()) {
                    outcome = Outcome.DECLINED;
                } else if (allowed.equals(objection.disputed())) {
                    outcome = Outcome.ALLOWED;
                } else {
                    outcome = Outcome.PART_ALLOWED;
                }
                work.cards().answer(objection, new Resolution(objection, outcome, allowed));
                work.commit();
            }
            repairs.main.out("card " + number + " line " + line + " " + outcome.value());
            return Main.OK;
        }
    }
}
