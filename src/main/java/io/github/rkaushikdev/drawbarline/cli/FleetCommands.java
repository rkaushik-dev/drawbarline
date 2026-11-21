package io.github.rkaushikdev.drawbarline.cli;

import io.github.rkaushikdev.drawbarline.fleet.Car;
import io.github.rkaushikdev.drawbarline.fleet.CarTypeCode;
import io.github.rkaushikdev.drawbarline.fleet.Lease;
import io.github.rkaushikdev.drawbarline.fleet.MechanicalDesignation;
import io.github.rkaushikdev.drawbarline.fleet.Ownership;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.measure.Length;
import io.github.rkaushikdev.drawbarline.persistence.UnitOfWork;
import io.github.rkaushikdev.drawbarline.util.Text;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * The equipment register.
 *
 * <p>Reading a car off a command line means parsing the same strings a clerk writes on a wheel
 * report, so the arguments are marks and stencil numbers rather than identifiers somebody would
 * have to look up. Anything that cannot be read comes back as a refusal naming the argument.
 */
@Command(
        name = "fleet",
        description = "The equipment register.",
        subcommands = {
            FleetCommands.Add.class,
            FleetCommands.ListCars.class,
            FleetCommands.Retire.class,
            FleetCommands.Show.class
        })
final class FleetCommands {

    @ParentCommand
    private Main main;

    /** File an equipment record. */
    @Command(name = "add", description = "File an equipment record.")
    static final class Add implements Main.Action {

        @ParentCommand
        private FleetCommands fleet;

        @Parameters(paramLabel = "MARK")
        private String mark;

        @Option(
                names = "--designation",
                required = true,
                paramLabel = "TEXT",
                description = "Mechanical designation, such as LO.")
        private String designation;

        @Option(
                names = "--type-code",
                required = true,
                paramLabel = "TEXT",
                description = "AAR car type code, such as L351.")
        private String typeCode;

        @Option(names = "--built", required = true, paramLabel = "TEXT", description = "Build date, yyyy-mm-dd.")
        private String built;

        @Option(
                names = "--light-weight",
                required = true,
                paramLabel = "INTEGER",
                description = "Light weight in pounds.")
        private int lightWeight;

        @Option(
                names = "--gross-rail-load",
                required = true,
                paramLabel = "INTEGER",
                description = "Gross rail load in pounds.")
        private int grossRailLoad;

        @Option(
                names = "--length",
                required = true,
                paramLabel = "TEXT",
                description = "Length over pulling faces, such as 59-6.")
        private String length;

        @Option(names = "--rebuilt", paramLabel = "TEXT", description = "Rebuild date, yyyy-mm-dd.")
        private String rebuilt;

        @Override
        public Integer call() {
            CarMark car = CarMark.parse(mark);
            MechanicalDesignation letters = MechanicalDesignation.parse(designation);
            CarTypeCode code = CarTypeCode.parse(typeCode);
            LocalDate builtOn = Readers.aDate(built);
            Weight light = new Weight(lightWeight);
            Weight gross = new Weight(grossRailLoad);
            Length over = Length.parse(length);
            Optional<LocalDate> rebuiltOn =
                    rebuilt == null || rebuilt.isEmpty() ? Optional.empty() : Optional.of(Readers.aDate(rebuilt));
            Car filed = new Car(car, letters, code, builtOn, light, gross, over, rebuiltOn);
            try (UnitOfWork work = fleet.main.work()) {
                work.fleet().add(filed);
                work.commit();
            }
            fleet.main.out("filed " + filed);
            return Main.OK;
        }
    }

    /** Every car on the register. */
    @Command(name = "list", description = "Every car on the register.")
    static final class ListCars implements Main.Action {

        @ParentCommand
        private FleetCommands fleet;

        @Option(names = "--owner", paramLabel = "TEXT", description = "Only cars under one reporting mark.")
        private String owner;

        @Override
        public Integer call() {
            List<Car> cars;
            try (UnitOfWork work = fleet.main.work()) {
                cars = work.fleet().all();
            }
            if (owner != null && !owner.isEmpty()) {
                String wanted = Text.upper(owner);
                cars = cars.stream()
                        .filter(car -> car.owner().toString().equals(wanted))
                        .toList();
            }
            for (Car car : cars) {
                fleet.main.out(String.format(
                        Locale.ROOT,
                        "%s  %-4s %s  %s",
                        car.mark(),
                        car.designation(),
                        car.typeCode(),
                        car.loadLimit()));
            }
            fleet.main.out(cars.size() + " cars");
            return Main.OK;
        }
    }

    /** Everything on one car. */
    @Command(name = "show", description = "Everything on one car.")
    static final class Show implements Main.Action {

        @ParentCommand
        private FleetCommands fleet;

        @Parameters(paramLabel = "MARK")
        private String mark;

        @Override
        public Integer call() {
            CarMark wanted = CarMark.parse(mark);
            Car car;
            Ownership ownership;
            Optional<LocalDate> retired;
            try (UnitOfWork work = fleet.main.work()) {
                car = work.fleet().get(wanted);
                ownership = work.fleet().ownership(wanted);
                retired = work.fleet().retiredOn(wanted);
            }
            Main main = fleet.main;
            main.out("car          " + car.mark());
            main.out("stencil      " + car.mark().stencilled());
            main.out("kind         " + car.designation() + " " + car.typeCode() + " "
                    + car.kind().description());
            main.out("built        " + car.built());
            car.rebuilt().ifPresent(on -> main.out("rebuilt      " + on));
            main.out("light weight " + car.lightWeight());
            main.out("load limit   " + car.loadLimit());
            main.out("expires      " + car.interchangeExpires());
            for (Lease term : ownership.terms()) {
                main.out("lease        " + term);
            }
            retired.ifPresent(on -> main.out("retired      " + on));
            return Main.OK;
        }
    }

    /** Take a car off the roster. */
    @Command(name = "retire", description = "Take a car off the roster.")
    static final class Retire implements Main.Action {

        @ParentCommand
        private FleetCommands fleet;

        @Parameters(paramLabel = "MARK")
        private String mark;

        @Option(names = "--on", required = true, paramLabel = "TEXT", description = "The day it came off, yyyy-mm-dd.")
        private String on;

        @Option(names = "--reason", defaultValue = "", paramLabel = "TEXT", description = "Why.")
        private String reason;

        @Override
        public Integer call() {
            try (UnitOfWork work = fleet.main.work()) {
                work.fleet().retire(CarMark.parse(mark), Readers.aDate(on), reason);
                work.commit();
            }
            fleet.main.out("retired " + mark + " on " + on);
            return Main.OK;
        }
    }
}
