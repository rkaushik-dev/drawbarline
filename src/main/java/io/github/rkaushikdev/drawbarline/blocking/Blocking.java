package io.github.rkaushikdev.drawbarline.blocking;

import io.github.rkaushikdev.drawbarline.marks.CarMark;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Which block a car goes in, and what order the blocks stand.
 *
 * <p>A train out of Escanaba is blocks, each the cars for one place or one connection, standing in
 * the order they will be set off. Getting the order wrong does not lose the cars, it loses an hour
 * at every stop while the crew digs one out of the middle. The plan is data; this only applies it.
 */
public final class Blocking {

    private Blocking() {}

    /** Put each car in its block, keeping the standing order of the blocks and car order within each. */
    public static Classification classify(List<Bound> cars, Plan plan) {
        Classification found = new Classification();
        for (Block one : plan.standing()) {
            found.open(one.code());
        }
        for (Bound car : cars) {
            Optional<Block> block = plan.blockFor(car.destination());
            if (block.isPresent()) {
                found.put(block.get().code(), car.car());
            } else {
                found.leaveOut(car.car());
            }
        }
        found.sortWithinBlocks();
        return found;
    }

    /** The whole train head end first, block by block, the unblocked cars left out. */
    public static List<CarMark> orderOf(Classification found, Plan plan) {
        List<CarMark> ordered = new ArrayList<>();
        for (Block one : plan.standing()) {
            ordered.addAll(found.carsIn(one.code()));
        }
        return List.copyOf(ordered);
    }
}
