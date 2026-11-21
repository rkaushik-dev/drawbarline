package io.github.rkaushikdev.drawbarline.cli;

import io.github.rkaushikdev.drawbarline.EventError;
import io.github.rkaushikdev.drawbarline.events.CarState;
import io.github.rkaushikdev.drawbarline.events.CarStream;
import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.events.EventKind;
import io.github.rkaushikdev.drawbarline.events.Replay;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.persistence.UnitOfWork;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * The car event log.
 *
 * <p>Recording an event from a command line means reading a local clock reading at a station,
 * because that is what the paperwork carries. The conversion to a moment happens here, once, and it
 * refuses the two mornings a year the reading is ambiguous rather than guessing which one the clerk
 * meant.
 *
 * <p>There is a bulk load as well, because the connections send a day of interchange as a file and
 * keying it by hand is not a plan.
 */
@Command(
        name = "events",
        description = "The car event log.",
        subcommands = {EventCommands.Record.class, EventCommands.Load.class, EventCommands.Show.class})
final class EventCommands {

    /**
     * The columns a sheet of events cannot do without. It may carry {@code road}, {@code industry},
     * {@code waybill} and {@code tenths} as well, and an empty one is an absent one.
     */
    private static final Set<String> REQUIRED = Set.of("car", "kind", "when", "station");

    @ParentCommand
    private Main main;

    /** One event out of one row of a sheet. */
    static Event build(Map<String, String> row) {
        String station = Text.upper(Text.strip(field(row, "station")));
        EventKind kind = EventKind.kindNamed(field(row, "kind"));
        CarMark car = CarMark.parse(field(row, "car"));
        Instant when = Readers.aMoment(station, Text.strip(field(row, "when")));
        String road = field(row, "road");
        String industry = field(row, "industry");
        String waybill = field(row, "waybill");
        String tenths = field(row, "tenths");
        Event.Builder event = Event.builder(kind, car, when, station);
        if (!road.isEmpty()) {
            event.road(ReportingMark.parse(road));
        }
        if (!industry.isEmpty() && !Text.strip(industry).isEmpty()) {
            event.industry(Text.upper(Text.strip(industry)));
        }
        if (!waybill.isEmpty()) {
            event.waybill(Readers.whole(waybill));
        }
        if (!tenths.isEmpty()) {
            event.tenths(Readers.whole(tenths));
        }
        return event.build();
    }

    private static String field(Map<String, String> row, String name) {
        return row.getOrDefault(name, "");
    }

    /** Record one event against one car. */
    @Command(name = "record", description = "Record one event against one car.")
    static final class Record implements Main.Action {

        @ParentCommand
        private EventCommands events;

        @Parameters(index = "0", paramLabel = "CAR")
        private String car;

        @Parameters(index = "1", paramLabel = "KIND")
        private String kind;

        @Option(names = "--at", required = true, paramLabel = "TEXT", description = "Station code.")
        private String station;

        @Option(
                names = "--when",
                required = true,
                paramLabel = "TEXT",
                description = "Local clock reading, yyyy-mm-dd hh:mm.")
        private String when;

        @Option(names = "--road", paramLabel = "TEXT", description = "The road on the other side of an interchange.")
        private String road;

        @Option(names = "--industry", paramLabel = "TEXT", description = "The track, for a placement or a pull.")
        private String industry;

        @Option(names = "--waybill", paramLabel = "INTEGER", description = "Waybill number.")
        private Integer waybill;

        @Option(names = "--tenths", paramLabel = "INTEGER", description = "Tenths of a mile, for a movement.")
        private Integer tenths;

        @Option(names = "--second-pass", description = "The later of the two ambiguous hours.")
        private boolean secondPass;

        @Override
        public Integer call() {
            String code = Text.upper(Text.strip(station));
            Event.Builder built = Event.builder(
                    EventKind.kindNamed(kind), CarMark.parse(car), Readers.aMoment(code, when, secondPass), code);
            if (road != null && !road.isEmpty()) {
                built.road(ReportingMark.parse(road));
            }
            if (industry != null && !industry.isEmpty()) {
                built.industry(Text.upper(Text.strip(industry)));
            }
            Event event = built.waybill(waybill).tenths(tenths).build();
            try (UnitOfWork work = events.main.work()) {
                work.events().record(event);
                work.commit();
            }
            events.main.out("recorded " + event);
            return Main.OK;
        }
    }

    /** Load a day of interchange out of a comma separated sheet. */
    @Command(name = "load", description = "Load a day of interchange out of a comma separated sheet.")
    static final class Load implements Main.Action {

        @ParentCommand
        private EventCommands events;

        @Parameters(paramLabel = "SHEET", converter = Readers.Sheet.class)
        private Path sheet;

        @Override
        public Integer call() {
            List<Map<String, String>> rows = Readers.rowsOf(sheet);
            Readers.requireColumns(rows, REQUIRED, Readers.name(sheet));
            int taken = 0;
            try (UnitOfWork work = events.main.work()) {
                int number = 2;
                for (Map<String, String> row : rows) {
                    try {
                        work.events().record(build(row));
                    } catch (EventError bad) {
                        throw new CommandError("line " + number + ": " + bad.getMessage());
                    }
                    taken++;
                    number++;
                }
                work.commit();
            }
            events.main.out("took " + taken + " events off " + Readers.name(sheet));
            return Main.OK;
        }
    }

    /** One car's stream and where it leaves the car. */
    @Command(name = "show", description = "One car's stream and where it leaves the car.")
    static final class Show implements Main.Action {

        @ParentCommand
        private EventCommands events;

        @Parameters(paramLabel = "CAR")
        private String car;

        @Override
        public Integer call() {
            CarMark mark = CarMark.parse(car);
            CarStream stream;
            CarState state;
            try (UnitOfWork work = events.main.work()) {
                stream = work.events().stream(mark);
                state = Replay.replay(stream);
            }
            for (Event event : stream) {
                events.main.out(event.toString());
            }
            events.main.out("-> " + state);
            return Main.OK;
        }
    }
}
