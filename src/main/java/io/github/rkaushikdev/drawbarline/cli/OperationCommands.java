package io.github.rkaushikdev.drawbarline.cli;

import io.github.rkaushikdev.drawbarline.agreements.InterchangeAgreement;
import io.github.rkaushikdev.drawbarline.clock.Clock;
import io.github.rkaushikdev.drawbarline.customers.CustomerRegister;
import io.github.rkaushikdev.drawbarline.distribution.CarOrder;
import io.github.rkaushikdev.drawbarline.distribution.Distribution;
import io.github.rkaushikdev.drawbarline.distribution.Empty;
import io.github.rkaushikdev.drawbarline.embargo.Embargo;
import io.github.rkaushikdev.drawbarline.events.EventLog;
import io.github.rkaushikdev.drawbarline.fleet.CarKind;
import io.github.rkaushikdev.drawbarline.interchange.Verdict;
import io.github.rkaushikdev.drawbarline.lading.Commodity;
import io.github.rkaushikdev.drawbarline.lading.Lading;
import io.github.rkaushikdev.drawbarline.lading.Stcc;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.persistence.UnitOfWork;
import io.github.rkaushikdev.drawbarline.reports.Align;
import io.github.rkaushikdev.drawbarline.reports.Column;
import io.github.rkaushikdev.drawbarline.reports.DistributionReport;
import io.github.rkaushikdev.drawbarline.reports.Render;
import io.github.rkaushikdev.drawbarline.reports.Table;
import io.github.rkaushikdev.drawbarline.service.Service;
import io.github.rkaushikdev.drawbarline.switching.Move;
import io.github.rkaushikdev.drawbarline.switching.Placement;
import io.github.rkaushikdev.drawbarline.switching.SwitchError;
import io.github.rkaushikdev.drawbarline.switching.SwitchList;
import io.github.rkaushikdev.drawbarline.switching.Switching;
import io.github.rkaushikdev.drawbarline.util.Literal;
import io.github.rkaushikdev.drawbarline.util.Numbers;
import io.github.rkaushikdev.drawbarline.util.Text;
import io.github.rkaushikdev.drawbarline.waybill.Waybill;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Operating commands: waybills, embargoes, the switch list, car orders, the distribution of
 * empties, interchange agreements and the delivery check.
 *
 * <p>The switch list is the one a crew actually reads off a printer at five in the morning, so it
 * prints as lines rather than as anything a machine would like, and it prints the moves it could not
 * make as loudly as the ones it could. A crew that finds out at the industry that a track was full
 * has already made the trip.
 *
 * <p>Orders are written car=industry for the same reason they are on the HTTP side. The yard clerk
 * writes the pairs down that way on the sheet the list is made up from.
 */
final class OperationCommands {

    private OperationCommands() {}

    private static boolean given(String text) {
        return text != null && !text.isEmpty();
    }

    /** A car to spot, written car=industry. */
    static Placement anOrder(String text) {
        int equals = text.indexOf('=');
        String industry = equals < 0 ? "" : text.substring(equals + 1);
        if (industry.isEmpty()) {
            throw new CommandError(Literal.of(text) + " is not an order, write it as car=industry");
        }
        return new Placement(CarMark.parse(text.substring(0, equals)), Text.upper(Text.strip(industry)));
    }

    /** Midnight UTC at the start of a day given as yyyy-mm-dd, or now when none is given. */
    private static Instant midnightOrNow(String at) {
        return given(at) ? Clock.midnightUtc(Readers.aDate(at)) : Clock.now();
    }

    /** The paper a car moves on. */
    @Command(
            name = "waybill",
            description = "The paper a car moves on.",
            subcommands = {Waybills.Issue.class, Waybills.Show.class})
    static final class Waybills {

        @ParentCommand
        private Main main;

        /** Issue a waybill. Leave the commodity off for an empty. */
        @Command(name = "issue", description = "Issue a waybill. Leave the commodity off for an empty.")
        static final class Issue implements Main.Action {

            @ParentCommand
            private Waybills waybills;

            @Parameters(index = "0", paramLabel = "NUMBER")
            private int number;

            @Parameters(index = "1", paramLabel = "CAR")
            private String car;

            @Option(names = "--dated", required = true, paramLabel = "TEXT", description = "The day it is issued.")
            private String dated;

            @Option(names = "--shipper", required = true, paramLabel = "TEXT", description = "Who is shipping.")
            private String shipper;

            @Option(names = "--consignee", required = true, paramLabel = "TEXT", description = "Who is receiving.")
            private String consignee;

            @Option(
                    names = "--route",
                    required = true,
                    paramLabel = "TEXT",
                    description = "Station codes, comma separated.")
            private String route;

            @Option(names = "--commodity", paramLabel = "TEXT", description = "Seven digit commodity code.")
            private String commodity;

            @Option(names = "--commodity-name", paramLabel = "TEXT", description = "What it is called.")
            private String commodityName;

            @Option(names = "--pounds", paramLabel = "INTEGER", description = "What the lading weighs.")
            private Integer pounds;

            @Override
            public Integer call() {
                boolean weighed = pounds != null && pounds != 0;
                Optional<Lading> lading = Optional.empty();
                if (given(commodity) || weighed) {
                    if (!(given(commodity) && weighed)) {
                        throw new CommandError("a lading needs a commodity and a weight");
                    }
                    String name = given(commodityName) ? commodityName : "unnamed";
                    lading = Optional.of(new Lading(new Commodity(Stcc.parse(commodity), name), new Weight(pounds)));
                }
                LocalDate day = Readers.aDate(dated);
                CarMark mark = CarMark.parse(car);
                List<String> stations = Stream.of(route.split(",", -1))
                        .filter(one -> !Text.strip(one).isEmpty())
                        .map(one -> Text.upper(Text.strip(one)))
                        .toList();
                Waybill bill = new Waybill(number, day, mark, shipper, consignee, stations, lading);
                try (UnitOfWork work = waybills.main.work()) {
                    work.waybills().issue(bill);
                    work.commit();
                }
                waybills.main.out("issued " + bill);
                return Main.OK;
            }
        }

        /** One waybill and the route it is authorised over. */
        @Command(name = "show", description = "One waybill and the route it is authorised over.")
        static final class Show implements Main.Action {

            @ParentCommand
            private Waybills waybills;

            @Parameters(paramLabel = "NUMBER")
            private int number;

            @Override
            public Integer call() {
                Waybill bill;
                try (UnitOfWork work = waybills.main.work()) {
                    bill = work.waybills().get(number);
                }
                Main main = waybills.main;
                main.out("waybill    " + bill.number());
                main.out("dated      " + bill.dated());
                main.out("car        " + bill.car());
                main.out("shipper    " + bill.shipper());
                main.out("consignee  " + bill.consignee());
                main.out("route      " + String.join(" ", bill.route()));
                main.out("miles      " + Numbers.general(bill.miles()));
                if (bill.lading().isPresent()) {
                    main.out("lading     " + bill.lading().get());
                    if (bill.hazardous()) {
                        main.out("           hazardous");
                    }
                } else {
                    main.out("lading     empty");
                }
                return Main.OK;
            }
        }
    }

    /** What is stopped, and where. */
    @Command(
            name = "embargo",
            description = "What is stopped, and where.",
            subcommands = {Embargoes.Issue.class, Embargoes.Lift.class, Embargoes.ListEmbargoes.class})
    static final class Embargoes {

        @ParentCommand
        private Main main;

        /** Issue an embargo. It has to restrict at least one thing. */
        @Command(name = "issue", description = "Issue an embargo. It has to restrict at least one thing.")
        static final class Issue implements Main.Action {

            @ParentCommand
            private Embargoes embargoes;

            @Parameters(paramLabel = "NUMBER")
            private int number;

            @Option(
                    names = "--effective",
                    required = true,
                    paramLabel = "TEXT",
                    description = "The day it takes effect.")
            private String effective;

            @Option(names = "--reason", required = true, paramLabel = "TEXT", description = "Why.")
            private String reason;

            @Option(names = "--station", paramLabel = "TEXT", description = "Restrict one station.")
            private String station;

            @Option(names = "--commodity-family", paramLabel = "TEXT", description = "Restrict a commodity family.")
            private String commodityFamily;

            @Option(names = "--road", paramLabel = "TEXT", description = "Restrict one connection.")
            private String road;

            @Option(names = "--expires", paramLabel = "TEXT", description = "The day it ends, if it is known.")
            private String expires;

            @Option(names = "--permits", description = "Allow movement under a permit.")
            private boolean permits;

            @Override
            public Integer call() {
                Embargo.Builder built = Embargo.builder(number, Readers.aDate(effective), reason);
                if (given(station)) {
                    built.station(Text.upper(Text.strip(station)));
                }
                if (commodityFamily != null) {
                    built.commodityFamily(commodityFamily);
                }
                if (given(road)) {
                    built.road(ReportingMark.parse(road));
                }
                if (given(expires)) {
                    built.expires(Readers.aDate(expires));
                }
                Embargo found = built.permitsAllowed(permits).build();
                try (UnitOfWork work = embargoes.main.work()) {
                    work.embargoes().issue(found);
                    work.commit();
                }
                embargoes.main.out("issued " + found);
                return Main.OK;
            }
        }

        /** Lift an embargo early. */
        @Command(name = "lift", description = "Lift an embargo early.")
        static final class Lift implements Main.Action {

            @ParentCommand
            private Embargoes embargoes;

            @Parameters(paramLabel = "NUMBER")
            private int number;

            @Option(names = "--on", required = true, paramLabel = "TEXT", description = "The day it comes off.")
            private String on;

            @Override
            public Integer call() {
                Embargo found;
                try (UnitOfWork work = embargoes.main.work()) {
                    found = work.embargoes().lift(number, Readers.aDate(on));
                    work.commit();
                }
                embargoes.main.out("lifted " + found + " on " + Literal.of(found.expires()));
                return Main.OK;
            }
        }

        /** Every embargo, or only the live ones. */
        @Command(name = "list", description = "Every embargo, or only the live ones.")
        static final class ListEmbargoes implements Main.Action {

            @ParentCommand
            private Embargoes embargoes;

            @Option(names = "--on", paramLabel = "TEXT", description = "Only the ones live on a day.")
            private String on;

            @Override
            public Integer call() {
                List<Embargo> found;
                try (UnitOfWork work = embargoes.main.work()) {
                    found = given(on)
                            ? work.embargoes().inForceOn(Readers.aDate(on))
                            : work.embargoes().all();
                }
                Table table = new Table(List.of(
                        new Column("number", Align.RIGHT),
                        new Column("from"),
                        new Column("to"),
                        new Column("restricts"),
                        new Column("reason")));
                for (Embargo one : found) {
                    String restricts = Stream.of(
                                    one.station(),
                                    one.commodityFamily(),
                                    one.road().map(ReportingMark::toString))
                            .flatMap(Optional::stream)
                            .filter(part -> !part.isEmpty())
                            .collect(Collectors.joining(" "));
                    table.add(
                            String.valueOf(one.number()),
                            one.effective().toString(),
                            one.expires().map(LocalDate::toString).orElse("open"),
                            restricts,
                            one.reason());
                }
                table.total(found.size() + " embargoes", "", "", "", "");
                List<String> lines = new ArrayList<>(Render.heading("embargoes", given(on) ? on : "all"));
                lines.addAll(table.lines());
                lines.forEach(embargoes.main::out);
                return Main.OK;
            }
        }
    }

    /** The list for one station, pulls first so the placements see the room. */
    @Command(name = "switchlist", description = "The list for one station, pulls first so the placements see the room.")
    static final class SwitchListing implements Main.Action {

        @ParentCommand
        private Main main;

        @Parameters(paramLabel = "STATION")
        private String station;

        @Option(names = "--day", required = true, paramLabel = "TEXT", description = "The day the crew works it.")
        private String day;

        @Option(names = "--at", paramLabel = "TEXT", description = "A moment in utc, yyyy-mm-dd. Now by default.")
        private String at;

        @Option(names = "--order", paramLabel = "TEXT", description = "A car to spot, written as car=industry.")
        private List<String> orders = new ArrayList<>();

        @Override
        public Integer call() {
            String where = Text.upper(Text.strip(station));
            Instant moment = midnightOrNow(at);
            EventLog log;
            CustomerRegister customers;
            try (UnitOfWork work = main.work()) {
                log = work.events().log();
                customers = work.customers().register();
            }
            LocalDate worked = Readers.aDate(day);
            List<Placement> placements =
                    orders.stream().map(OperationCommands::anOrder).toList();
            SwitchList found;
            try {
                found = Switching.plan(log, customers, where, worked, moment, placements);
            } catch (SwitchError bad) {
                throw new CommandError(bad.getMessage());
            }
            main.out("SWITCH LIST " + where + " " + day);
            main.out("");
            for (Move move : found.moves()) {
                main.out("  " + move);
            }
            if (found.moves().isEmpty()) {
                main.out("  nothing to do");
            }
            main.out("");
            main.out("worked " + found.worked() + " of " + found.size());
            return Main.OK;
        }
    }

    /** Cars a shipper has asked for. */
    @Command(
            name = "order",
            description = "Cars a shipper has asked for.",
            subcommands = {Orders.Cancel.class, Orders.Place.class})
    static final class Orders {

        @ParentCommand
        private Main main;

        /** Take a car order. */
        @Command(name = "place", description = "Take a car order.")
        static final class Place implements Main.Action {

            @ParentCommand
            private Orders orders;

            @Parameters(index = "0", paramLabel = "NUMBER")
            private int number;

            @Parameters(index = "1", paramLabel = "SHIPPER")
            private String shipper;

            @Option(names = "--track", required = true, paramLabel = "TEXT", description = "The industry track code.")
            private String track;

            @Option(
                    names = "--kind",
                    required = true,
                    paramLabel = "TEXT",
                    description = "Car kind letter, such as L or X.")
            private String kind;

            @Option(names = "--cars", required = true, paramLabel = "INTEGER", description = "How many.")
            private int cars;

            @Option(
                    names = "--wanted",
                    required = true,
                    paramLabel = "TEXT",
                    description = "The day the cars are wanted.")
            private String wanted;

            @Option(names = "--on", paramLabel = "TEXT", description = "The day it is placed. Today by default.")
            private String on;

            @Override
            public Integer call() {
                CarOrder found = new CarOrder(
                        number,
                        Text.upper(Text.strip(shipper)),
                        Text.upper(Text.strip(track)),
                        CarKind.fromLetter(Text.upper(Text.strip(kind))),
                        cars,
                        Readers.aDate(wanted));
                LocalDate placed = given(on) ? Readers.aDate(on) : Clock.today();
                try (UnitOfWork work = orders.main.work()) {
                    work.orders().place(found, placed);
                    work.commit();
                }
                orders.main.out("took " + found);
                return Main.OK;
            }
        }

        /** Cancel a car order. */
        @Command(name = "cancel", description = "Cancel a car order.")
        static final class Cancel implements Main.Action {

            @ParentCommand
            private Orders orders;

            @Parameters(paramLabel = "NUMBER")
            private int number;

            @Option(names = "--on", required = true, paramLabel = "TEXT", description = "The day it is cancelled.")
            private String on;

            @Override
            public Integer call() {
                try (UnitOfWork work = orders.main.work()) {
                    work.orders().cancel(number, Readers.aDate(on));
                    work.commit();
                }
                orders.main.out("cancelled order " + number);
                return Main.OK;
            }
        }
    }

    /** Match the empties on hand against the open orders. */
    @Command(name = "distribute", description = "Match the empties on hand against the open orders.")
    static final class Distribute implements Main.Action {

        @ParentCommand
        private Main main;

        @Option(names = "--on", required = true, paramLabel = "TEXT", description = "The day the cars are wanted.")
        private String on;

        @Option(
                names = "--at",
                paramLabel = "TEXT",
                description = "A moment in utc, yyyy-mm-dd. The same day by default.")
        private String at;

        @Override
        public Integer call() {
            LocalDate day = Readers.aDate(on);
            Instant moment = Readers.aMomentUtc(Optional.of(given(at) ? at : on));
            List<CarOrder> orders;
            Map<String, String> stations;
            List<Empty> empties;
            try (UnitOfWork work = main.work()) {
                orders = work.orders().openOn(day);
                stations = work.orders().stations();
                empties = Distribution.emptiesOnHand(
                        work.fleet().register(), work.events().log(), moment);
            }
            Distribution found = Distribution.distribute(orders, empties, stations);
            DistributionReport.distributionReport(found, orders, day.toString()).forEach(main::out);
            return Main.OK;
        }
    }

    /** Every car standing empty on our rail. */
    @Command(name = "empties", description = "Every car standing empty on our rail.")
    static final class Empties implements Main.Action {

        @ParentCommand
        private Main main;

        @Option(names = "--at", paramLabel = "TEXT", description = "A moment in utc, yyyy-mm-dd. Now by default.")
        private String at;

        @Override
        public Integer call() {
            Instant moment = midnightOrNow(at);
            List<Empty> found;
            try (UnitOfWork work = main.work()) {
                found = Distribution.emptiesOnHand(
                        work.fleet().register(), work.events().log(), moment);
            }
            String day = LocalDate.ofInstant(moment, ZoneOffset.UTC).toString();
            DistributionReport.emptiesReport(found, day).forEach(main::out);
            return Main.OK;
        }
    }

    /** What each connection takes, and when. */
    @Command(
            name = "agreement",
            description = "What each connection takes, and when.",
            subcommands = {Agreements.ListAgreements.class, Agreements.Sign.class})
    static final class Agreements {

        @ParentCommand
        private Main main;

        /** File an interchange agreement. */
        @Command(name = "sign", description = "File an interchange agreement.")
        static final class Sign implements Main.Action {

            @ParentCommand
            private Agreements agreements;

            @Parameters(index = "0", paramLabel = "ROAD")
            private String road;

            @Parameters(index = "1", paramLabel = "JUNCTION")
            private String junction;

            @Option(
                    names = "--effective",
                    required = true,
                    paramLabel = "TEXT",
                    description = "The day it takes effect.")
            private String effective;

            @Option(
                    names = "--kinds",
                    required = true,
                    paramLabel = "TEXT",
                    description = "Car kind letters, such as LX.")
            private String kinds;

            @Option(names = "--cap", required = true, paramLabel = "INTEGER", description = "Cars a day.")
            private int cap;

            @Option(
                    names = "--opens",
                    required = true,
                    paramLabel = "TEXT",
                    description = "When the junction is manned, hh:mm.")
            private String opens;

            @Option(names = "--closes", required = true, paramLabel = "TEXT", description = "When it shuts, hh:mm.")
            private String closes;

            @Option(names = "--hazardous", description = "They take placarded cars.")
            private boolean hazardous;

            @Option(names = "--expires", paramLabel = "TEXT", description = "The day it ends, if it is known.")
            private String expires;

            @Option(
                    names = "--by",
                    paramLabel = "TEXT",
                    defaultValue = Service.UNSIGNED,
                    description = "Whose initials go on it.")
            private String actor;

            @Override
            public Integer call() {
                ReportingMark mark = ReportingMark.parse(road);
                String where = Text.upper(Text.strip(junction));
                LocalDate from = Readers.aDate(effective);
                Set<CarKind> taken = EnumSet.noneOf(CarKind.class);
                Text.upper(Text.strip(kinds))
                        .codePoints()
                        .forEach(letter -> taken.add(CarKind.fromLetter(Character.toString(letter))));
                InterchangeAgreement found = new InterchangeAgreement(
                        mark,
                        where,
                        from,
                        taken,
                        cap,
                        Readers.aTime(opens),
                        Readers.aTime(closes),
                        hazardous,
                        given(expires) ? Optional.of(Readers.aDate(expires)) : Optional.empty());
                try (UnitOfWork work = agreements.main.work()) {
                    Service.signAgreement(work, found, actor);
                    work.commit();
                }
                agreements.main.out("signed " + found);
                return Main.OK;
            }
        }

        /** Every agreement, or only the live ones. */
        @Command(name = "list", description = "Every agreement, or only the live ones.")
        static final class ListAgreements implements Main.Action {

            @ParentCommand
            private Agreements agreements;

            @Option(names = "--on", paramLabel = "TEXT", description = "Only the ones in force on a day.")
            private String on;

            @Override
            public Integer call() {
                List<InterchangeAgreement> found;
                try (UnitOfWork work = agreements.main.work()) {
                    found = given(on)
                            ? work.agreements().inForceOn(Readers.aDate(on))
                            : work.agreements().all();
                }
                Table table = new Table(List.of(
                        new Column("junction"),
                        new Column("road"),
                        new Column("kinds"),
                        new Column("cap", Align.RIGHT),
                        new Column("manned"),
                        new Column("hazmat")));
                for (InterchangeAgreement one : found) {
                    table.add(
                            one.junction(),
                            one.road().toString(),
                            one.kinds().stream().map(CarKind::letter).sorted().collect(Collectors.joining()),
                            String.valueOf(one.dailyCap()),
                            clock(one.opens()) + " to " + clock(one.closes()),
                            one.hazardous() ? "yes" : "no");
                }
                table.total(found.size() + " agreements", "", "", "", "", "");
                List<String> lines = new ArrayList<>(Render.heading("interchange agreements", given(on) ? on : "all"));
                lines.addAll(table.lines());
                lines.forEach(agreements.main::out);
                return Main.OK;
            }

            private static String clock(LocalTime at) {
                return String.format(Locale.ROOT, "%02d:%02d", at.getHour(), at.getMinute());
            }
        }
    }

    /** Ask whether a car can be handed across right now. */
    @Command(name = "deliver", description = "Ask whether a car can be handed across right now.")
    static final class Deliver implements Main.Action {

        @ParentCommand
        private Main main;

        @Parameters(paramLabel = "CAR")
        private String car;

        @Option(names = "--to", required = true, paramLabel = "TEXT", description = "The connection.")
        private String road;

        @Option(names = "--at", required = true, paramLabel = "TEXT", description = "The junction.")
        private String junction;

        @Option(
                names = "--when",
                required = true,
                paramLabel = "TEXT",
                description = "A local reading, yyyy-mm-dd hh:mm.")
        private String when;

        @Option(names = "--commodity", paramLabel = "TEXT", description = "Seven digit commodity code.")
        private String commodity;

        @Override
        public Integer call() {
            String where = Text.upper(Text.strip(junction));
            Optional<Commodity> lading = given(commodity)
                    ? Optional.of(new Commodity(Stcc.parse(commodity), "as billed"))
                    : Optional.empty();
            LocalDateTime local = Readers.aReading(when);
            Verdict found;
            try (UnitOfWork work = main.work()) {
                found = Service.deliveryCheck(
                        work, CarMark.parse(car), where, ReportingMark.parse(road), local, lading);
            }
            main.out(found.toString());
            for (String one : found.findings()) {
                main.out("  " + one);
            }
            return found.goes() ? Main.OK : Main.REFUSED;
        }
    }
}
