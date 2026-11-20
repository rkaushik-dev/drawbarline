package io.github.rkaushikdev.drawbarline.support;

import io.github.rkaushikdev.drawbarline.fleet.Car;
import io.github.rkaushikdev.drawbarline.fleet.CarTypeCode;
import io.github.rkaushikdev.drawbarline.fleet.FleetRegister;
import io.github.rkaushikdev.drawbarline.fleet.MechanicalDesignation;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.measure.Length;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.time.LocalDate;

/** The cars of a small working railroad, for the tests that need more than one register. */
public final class Cars {

    public static final ReportingMark HOME = new ReportingMark("DBL");
    public static final ReportingMark CN = new ReportingMark("CN");
    public static final ReportingMark ELS = new ReportingMark("ELS");
    public static final CarTypeCode HOPPER = new CarTypeCode("L351");

    /** The build date every car here carries unless a test says otherwise. */
    public static final LocalDate BUILT = LocalDate.of(2020, 6, 1);

    private Cars() {}

    /** A covered hopper of the usual size under the given marks. */
    public static Car aCar(String mark) {
        return aCar(mark, BUILT);
    }

    /** A covered hopper of the usual size, built on a given day. */
    public static Car aCar(String mark, LocalDate built) {
        return new Car(
                CarMark.parse(mark),
                new MechanicalDesignation("LO"),
                HOPPER,
                built,
                new Weight(63500),
                new Weight(286000),
                Length.of(59, 6));
    }

    /** A register holding one such car for each mark. */
    public static FleetRegister aFleet(String... marks) {
        FleetRegister register = new FleetRegister();
        for (String mark : marks) {
            register.add(aCar(mark));
        }
        return register;
    }
}
