package io.github.rkaushikdev.drawbarline.cli;

import io.github.rkaushikdev.drawbarline.carhire.Band;
import io.github.rkaushikdev.drawbarline.carhire.Rate;
import io.github.rkaushikdev.drawbarline.customers.Industry;
import io.github.rkaushikdev.drawbarline.customers.Shipper;
import io.github.rkaushikdev.drawbarline.demurrage.Step;
import io.github.rkaushikdev.drawbarline.demurrage.Tariff;
import io.github.rkaushikdev.drawbarline.fleet.CarTypeCode;
import io.github.rkaushikdev.drawbarline.persistence.UnitOfWork;
import io.github.rkaushikdev.drawbarline.util.Literal;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Loading the tables the road files rather than computes.
 *
 * <p>Rates, tariffs, shippers and industry track all arrive as filings, and none of them belong in
 * the source. They come in as small comma separated files that a clerk can read, and they are
 * loaded in one transaction each so a file with a bad line halfway down loads nothing.
 */
@Command(
        name = "reference",
        description = "The tables the road files.",
        subcommands = {
            ReferenceCommands.Rates.class,
            ReferenceCommands.TariffFiling.class,
            ReferenceCommands.Shippers.class,
            ReferenceCommands.Industries.class,
            ReferenceCommands.Show.class
        })
final class ReferenceCommands {

    /** What a track is switched on when the filing does not say otherwise. */
    static final Set<String> WORKING_WEEK = Set.copyOf(Industry.WEEKDAYS.subList(0, 5));

    /** What a shipper's average agreement column says when it says yes. */
    private static final Set<String> YES = Set.of("1", "yes", "true");

    @ParentCommand
    private Main main;

    private static String field(Map<String, String> row, String name) {
        return row.getOrDefault(name, "");
    }

    /** Load car hire bands from type_code,from_age,to_age,effective,hourly,mileage. */
    @Command(
            name = "rates",
            description = "Load car hire bands from type_code,from_age,to_age,effective,hourly,mileage.")
    static final class Rates implements Main.Action {

        @ParentCommand
        private ReferenceCommands reference;

        @Parameters(paramLabel = "SHEET", converter = Readers.Sheet.class)
        private Path sheet;

        @Override
        public Integer call() {
            int taken = 0;
            try (UnitOfWork work = reference.main.work()) {
                for (Map<String, String> row : Readers.rowsOf(sheet)) {
                    CarTypeCode code = CarTypeCode.parse(field(row, "type_code"));
                    int fromAge = Readers.whole(field(row, "from_age"));
                    int toAge = Readers.whole(field(row, "to_age"));
                    LocalDate effective = Readers.aDate(field(row, "effective"));
                    Rate rate = new Rate(Readers.whole(field(row, "hourly")), Readers.whole(field(row, "mileage")));
                    work.rates().file(new Band(code, fromAge, toAge, effective, rate));
                    taken++;
                }
                work.commit();
            }
            reference.main.out("filed " + taken + " bands");
            return Main.OK;
        }
    }

    /** Publish a demurrage tariff. */
    @Command(name = "tariff", description = "Publish a demurrage tariff.")
    static final class TariffFiling implements Main.Action {

        @ParentCommand
        private ReferenceCommands reference;

        @Parameters(paramLabel = "CODE")
        private String code;

        @Option(names = "--effective", required = true, paramLabel = "TEXT", description = "The day it takes effect.")
        private String effective;

        @Option(names = "--free-loaded", required = true, paramLabel = "INTEGER", description = "Free days on a load.")
        private int freeLoaded;

        @Option(names = "--free-empty", required = true, paramLabel = "INTEGER", description = "Free days on an empty.")
        private int freeEmpty;

        @Option(
                names = "--step",
                required = true,
                paramLabel = "TEXT",
                description = "A step as through:cents, with the last one 0:cents.")
        private List<String> steps;

        @Override
        public Integer call() {
            List<Step> read = new ArrayList<>();
            for (String one : steps) {
                int colon = one.indexOf(':');
                String cents = colon < 0 ? "" : one.substring(colon + 1);
                if (cents.isEmpty()) {
                    throw new CommandError(Literal.of(one) + " is not a step, write it as 2:10000");
                }
                read.add(new Step(Readers.whole(one.substring(0, colon)), Readers.whole(cents)));
            }
            Tariff tariff = new Tariff(code, Readers.aDate(effective), freeLoaded, freeEmpty, read);
            try (UnitOfWork work = reference.main.work()) {
                work.tariffs().publish(tariff);
                work.commit();
            }
            reference.main.out("published " + tariff);
            return Main.OK;
        }
    }

    /** Load shippers from code,name,average_agreement. */
    @Command(name = "shippers", description = "Load shippers from code,name,average_agreement.")
    static final class Shippers implements Main.Action {

        @ParentCommand
        private ReferenceCommands reference;

        @Parameters(paramLabel = "SHEET", converter = Readers.Sheet.class)
        private Path sheet;

        @Override
        public Integer call() {
            int taken = 0;
            try (UnitOfWork work = reference.main.work()) {
                for (Map<String, String> row : Readers.rowsOf(sheet)) {
                    work.customers()
                            .addShipper(new Shipper(
                                    Text.upper(Text.strip(field(row, "code"))),
                                    Text.strip(field(row, "name")),
                                    YES.contains(Text.lower(Text.strip(field(row, "average_agreement"))))));
                    taken++;
                }
                work.commit();
            }
            reference.main.out("filed " + taken + " shippers");
            return Main.OK;
        }
    }

    /** Load industry track from code,name,station,shipper,spots,switched. */
    @Command(name = "industries", description = "Load industry track from code,name,station,shipper,spots,switched.")
    static final class Industries implements Main.Action {

        @ParentCommand
        private ReferenceCommands reference;

        @Parameters(paramLabel = "SHEET", converter = Readers.Sheet.class)
        private Path sheet;

        @Override
        public Integer call() {
            int taken = 0;
            try (UnitOfWork work = reference.main.work()) {
                for (Map<String, String> row : Readers.rowsOf(sheet)) {
                    Shipper shipper = work.customers().shipper(Text.upper(Text.strip(field(row, "shipper"))));
                    Set<String> days = new LinkedHashSet<>();
                    for (String one : Text.strip(field(row, "switched")).split(";", -1)) {
                        if (!Text.strip(one).isEmpty()) {
                            days.add(Text.strip(one));
                        }
                    }
                    work.customers()
                            .addIndustry(new Industry(
                                    Text.upper(Text.strip(field(row, "code"))),
                                    Text.strip(field(row, "name")),
                                    Text.upper(Text.strip(field(row, "station"))),
                                    shipper,
                                    Readers.whole(field(row, "spots")),
                                    days.isEmpty() ? WORKING_WEEK : days));
                    taken++;
                }
                work.commit();
            }
            reference.main.out("filed " + taken + " industries");
            return Main.OK;
        }
    }

    /** What has been filed. */
    @Command(name = "show", description = "What has been filed.")
    static final class Show implements Main.Action {

        @ParentCommand
        private ReferenceCommands reference;

        @Override
        public Integer call() {
            int bands;
            int tariffs;
            int shippers;
            int industries;
            try (UnitOfWork work = reference.main.work()) {
                bands = work.rates().size();
                tariffs = work.tariffs().book().size();
                shippers = work.customers().shippers().size();
                industries = work.customers().size();
            }
            reference.main.out("rate bands  " + bands);
            reference.main.out("tariffs     " + tariffs);
            reference.main.out("shippers    " + shippers);
            reference.main.out("industries  " + industries);
            return Main.OK;
        }
    }
}
