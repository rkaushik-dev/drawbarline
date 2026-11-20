package io.github.rkaushikdev.drawbarline.fleet;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The weight stencil and when the car is due back on the scale.
 *
 * <p>A load limit is the gross rail load less the stencilled light weight, and a car picks up
 * weight over its life: heavier brake gear, a quarter ton of frozen product in the corners of a
 * hopper. So a car goes back on the scale every ten years. Tank cars are exempt, because their light
 * weight is a certified figure that changes only when the tank does, and so is maintenance of way
 * equipment. A car whose reweigh is overdue still moves, but the load limit computed off its
 * stencil is not one the road would defend in a claim.
 */
public final class Stencil {

    public static final int DEFAULT_REWEIGH_YEARS = 10;

    /** The kinds of car that are never called back to the scale. */
    public static final Set<CarKind> EXEMPT_KINDS = Set.of(CarKind.TANK, CarKind.MAINTENANCE);

    /** Where a builder's certified weight is said to have been taken. */
    public static final String NEW = "NEW";

    private Stencil() {}

    public static boolean exempt(Car car) {
        return EXEMPT_KINDS.contains(car.kind());
    }

    /** When the car is next due on the scale, or nothing when it is exempt. */
    public static Optional<LocalDate> dueBy(Car car, Weighing weighing) {
        if (exempt(car)) {
            return Optional.empty();
        }
        return Optional.of(weighing.on().plusYears(DEFAULT_REWEIGH_YEARS));
    }

    public static boolean overdueOn(Car car, Weighing weighing, LocalDate day) {
        return dueBy(car, weighing).map(day::isAfter).orElse(false);
    }

    /** The load limit the stencil claims, which uses the weighed light weight. */
    public static Weight loadLimitFrom(Car car, Weighing weighing) {
        if (weighing.pounds().compareTo(car.grossRailLoad()) >= 0) {
            throw new StencilError(
                    "the scale read at or over the gross rail load",
                    Details.of(
                            "car",
                            car.mark().toString(),
                            "weighed",
                            weighing.pounds().pounds()));
        }
        return car.grossRailLoad().minus(weighing.pounds());
    }

    /** The stencil as it is painted, one line per line on the car. */
    public static List<String> block(Car car, Weighing weighing) {
        return List.of(
                car.mark().toString(),
                car.designation() + " " + car.typeCode(),
                "CAPY " + car.grossRailLoad().pounds(),
                "LD LMT " + loadLimitFrom(car, weighing).pounds(),
                "LT WT " + weighing.pounds().pounds(),
                weighing.asBuilt() ? weighing.toString() : "RWT " + weighing);
    }
}
