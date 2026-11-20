package io.github.rkaushikdev.drawbarline.fleet;

import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Whether a car may be taken in interchange.
 *
 * <p>The inspector at the junction has a short list of reasons to turn a car back, and every one of
 * them costs somebody a day: refuse a good car and the connection reroutes its traffic, accept a bad
 * one and we own the defect from that moment. This is the paper half of the inspection. It reads
 * the register, the calendar and the waybill, and does not pretend to see the car; anything the
 * inspector finds with his own eyes arrives as a defect code and is added to what is found here.
 */
public final class Acceptance {

    private Acceptance() {}

    /** Every reason to turn an empty car with no defects reported back. */
    public static List<Finding> inspect(FleetRegister register, CarMark mark, LocalDate day) {
        return inspect(register, mark, day, Optional.empty(), List.of());
    }

    /** Every reason to turn the car back, in the order an inspector reads them. */
    public static List<Finding> inspect(
            FleetRegister register, CarMark mark, LocalDate day, Optional<Weight> lading, List<String> defects) {
        if (!register.has(mark)) {
            return List.of(new Finding(Refusal.NOT_REGISTERED, mark.toString()));
        }
        Car car = register.get(mark);
        List<Finding> findings = new ArrayList<>();
        if (day.isBefore(car.built())) {
            findings.add(new Finding(Refusal.NOT_YET_BUILT, car.built().toString()));
        } else if (!register.inServiceOn(mark, day)) {
            String retired = register.retiredOn(mark).map(LocalDate::toString).orElse("");
            findings.add(new Finding(Refusal.RETIRED, retired));
        }
        if (!day.isBefore(car.interchangeExpires())) {
            findings.add(new Finding(Refusal.AGE, car.interchangeExpires().toString()));
        }
        if (lading.isPresent()) {
            Weight past = Weight.overload(lading.get(), car.loadLimit());
            if (past.pounds() != 0) {
                findings.add(new Finding(Refusal.OVERLOADED, "over by " + past));
            }
        }
        for (String code : defects) {
            findings.add(new Finding(Refusal.DEFECT, code));
        }
        return List.copyOf(findings);
    }

    /** Whether an empty car with no defects reported goes across. */
    public static boolean acceptable(FleetRegister register, CarMark mark, LocalDate day) {
        return inspect(register, mark, day).isEmpty();
    }

    /** Whether the car goes across, which is nothing more than an empty finding list. */
    public static boolean acceptable(
            FleetRegister register, CarMark mark, LocalDate day, Optional<Weight> lading, List<String> defects) {
        return inspect(register, mark, day, lading, defects).isEmpty();
    }
}
