package io.github.rkaushikdev.drawbarline.hazmat;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.lading.Commodity;
import io.github.rkaushikdev.drawbarline.lading.Stcc;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Placarding, and where a placarded car may stand in a train.
 *
 * <p>A placarded car stands no nearer than the sixth car from the engine when the train has the
 * cars to put five ahead of it, and when it has not, it still has at least one car between it and
 * the engine. It also keeps a car between it and an occupied car. These are train handling rules
 * rather than accounting ones, checked here because the consist is already built and it is the last
 * chance to catch them.
 *
 * <p>The commodity decides the placard and the placard decides the handling, and that chain runs
 * one way only: nothing here reads a placard off a car and infers what is in it, because a placard
 * left on an emptied car is the most common paperwork error on any railroad. An empty that last
 * carried a hazardous commodity keeps its placards until it is cleaned, so a residue movement is
 * handled as though it were loaded. That is the rule, not an oversight in it.
 */
public final class Hazmat {

    /** How many unplacarded cars have to stand between a placarded car and the engine or an occupied car. */
    public static final int BUFFER_CARS = 1;

    /** The nearest place to the engine a placarded car may stand when the train is long enough. */
    public static final int SIXTH_FROM_THE_ENGINE = 6;

    private Hazmat() {}

    /** The placard a hazardous commodity is moved under. */
    public static Placard placardFor(Commodity commodity, int unNumber, HazardClass hazard) {
        if (!commodity.hazardous()) {
            throw new HazmatError(
                    "that commodity is not hazardous and takes no placard",
                    Details.of("code", commodity.code().toString()));
        }
        return new Placard(hazard, unNumber, commodity.name());
    }

    /** Whether the commodity code sits in the hazardous major group. */
    public static boolean isHazardous(Commodity commodity) {
        return commodity.code().majorGroup().equals(Stcc.HAZARDOUS_GROUP);
    }

    /** Every reason the train may not be handled as it stands. */
    public static List<String> checkStanding(List<Standing> cars) {
        List<Standing> ordered = inOrder(cars);
        List<String> found = new ArrayList<>(offTheEngine(ordered));
        found.addAll(offOccupiedCars(ordered));
        return List.copyOf(found);
    }

    /** The train head end first, refusing positions that do not run from one without a gap. */
    private static List<Standing> inOrder(List<Standing> cars) {
        List<Standing> ordered = cars.stream()
                .sorted(Comparator.comparingInt(Standing::position))
                .toList();
        for (int at = 0; at < ordered.size(); at++) {
            if (ordered.get(at).position() != at + 1) {
                throw new HazmatError("the positions do not run from one without a gap");
            }
        }
        return ordered;
    }

    /**
     * How many cars have to stand between the engine and the first placarded car: five when the
     * train has five cars that need no buffer to put there, one when it has not.
     */
    public static int engineBuffer(List<Standing> cars) {
        long spare = cars.stream().filter(one -> buffered(one).isEmpty()).count();
        int deep = SIXTH_FROM_THE_ENGINE - 1;
        return spare >= deep ? deep : BUFFER_CARS;
    }

    private static List<String> offTheEngine(List<Standing> ordered) {
        int buffer = engineBuffer(ordered);
        List<String> found = new ArrayList<>();
        for (Standing one : ordered.subList(0, Math.min(buffer, ordered.size()))) {
            buffered(one)
                    .ifPresent(hazard -> found.add(
                            buffer == BUFFER_CARS
                                    ? "position " + one.position() + " is placarded " + hazard.text()
                                            + " and sits against the engine"
                                    : "position " + one.position() + " is placarded " + hazard.text()
                                            + " and the train has the cars to stand it sixth from the engine"));
        }
        return found;
    }

    private static List<String> offOccupiedCars(List<Standing> ordered) {
        List<String> found = new ArrayList<>();
        for (Standing one : ordered) {
            if (!one.occupied()) {
                continue;
            }
            for (Standing other : ordered) {
                if (Math.abs(other.position() - one.position()) <= BUFFER_CARS) {
                    buffered(other)
                            .ifPresent(hazard -> found.add("position " + other.position() + " is placarded "
                                    + hazard.text() + " and sits against the occupied car at " + one.position()));
                }
            }
        }
        return found;
    }

    /** Whether the train may be handled as it stands. */
    public static boolean handles(List<Standing> cars) {
        return checkStanding(cars).isEmpty();
    }

    /** The class of a placarded car that has to be kept off the engine, if it is one. */
    private static Optional<HazardClass> buffered(Standing one) {
        return one.placarded() ? one.hazard().filter(HazardClass::needsBuffer) : Optional.empty();
    }
}
