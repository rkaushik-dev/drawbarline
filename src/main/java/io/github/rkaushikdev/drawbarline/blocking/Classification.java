package io.github.rkaushikdev.drawbarline.blocking;

import io.github.rkaushikdev.drawbarline.marks.CarMark;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every car put in its block, in the standing order of the blocks, and the cars nobody planned
 * for. A car whose destination is in no block is not quietly put on the end, because the end is
 * somebody else's block; it comes back as unblocked and somebody looks at it.
 */
public final class Classification {

    private final Map<String, List<CarMark>> blocks = new LinkedHashMap<>();
    private final List<CarMark> unblocked = new ArrayList<>();

    /** Open an empty block, which a plan does for every block it has. */
    void open(String code) {
        blocks.put(code, new ArrayList<>());
    }

    void put(String code, CarMark car) {
        blocks.get(code).add(car);
    }

    void leaveOut(CarMark car) {
        unblocked.add(car);
    }

    void sortWithinBlocks() {
        for (List<CarMark> cars : blocks.values()) {
            cars.sort((one, other) -> one.toString().compareTo(other.toString()));
        }
    }

    /** The cars in each block, the blocks in standing order. */
    public Map<String, List<CarMark>> blocks() {
        Map<String, List<CarMark>> found = new LinkedHashMap<>();
        blocks.forEach((code, cars) -> found.put(code, List.copyOf(cars)));
        return Collections.unmodifiableMap(found);
    }

    /** The cars no block covers, in the order they came. */
    public List<CarMark> unblocked() {
        return List.copyOf(unblocked);
    }

    /** How many cars went in a block. */
    public int blocked() {
        return blocks.values().stream().mapToInt(List::size).sum();
    }

    /** The cars in one block, or nothing when the plan has no such block. */
    public List<CarMark> carsIn(String code) {
        return List.copyOf(blocks.getOrDefault(code, List.of()));
    }

    /** {@code 2 cars in 3 blocks}, with the unblocked cars counted when there are any. */
    @Override
    public String toString() {
        if (unblocked.isEmpty()) {
            return blocked() + " cars in " + blocks.size() + " blocks";
        }
        return blocked() + " cars in " + blocks.size() + " blocks, " + unblocked.size() + " unblocked";
    }
}
