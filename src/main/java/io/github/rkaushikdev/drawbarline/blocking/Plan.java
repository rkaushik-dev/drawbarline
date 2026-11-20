package io.github.rkaushikdev.drawbarline.blocking;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.mileage.Mileage;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The blocking for trains out of one station: which block each destination goes in, and the order
 * the blocks stand. It is a filing, not a computation; somebody in the operating department decides
 * that Green Bay blocks ahead of Marinette because the Marinette cut comes off first.
 */
public record Plan(String station, List<Block> blocks) {

    public Plan {
        Mileage.milepost(station);
        blocks = List.copyOf(blocks);
        if (blocks.isEmpty()) {
            throw new BlockingError("a plan has at least one block", Details.of("station", station));
        }
        Set<String> codes = new HashSet<>();
        Set<Integer> positions = new HashSet<>();
        for (Block one : blocks) {
            codes.add(one.code());
            positions.add(one.position());
        }
        if (codes.size() != blocks.size()) {
            throw new BlockingError("a block code appears twice", Details.of("station", station));
        }
        if (positions.size() != blocks.size()) {
            throw new BlockingError("two blocks stand in one place", Details.of("station", station));
        }
        Set<String> covered = new HashSet<>();
        for (Block one : blocks) {
            SortedSet<String> clash = new TreeSet<>(covered);
            clash.retainAll(one.covers());
            if (!clash.isEmpty()) {
                throw new BlockingError(
                        "two blocks cover the same place",
                        Details.of("station", station, "places", List.copyOf(clash)));
            }
            covered.addAll(one.covers());
        }
    }

    /** The blocks head end first. */
    public List<Block> standing() {
        return blocks.stream().sorted(Comparator.comparingInt(Block::position)).toList();
    }

    /** The block a destination goes in, if any block covers it. */
    public Optional<Block> blockFor(String destination) {
        return blocks.stream().filter(one -> one.takes(destination)).findFirst();
    }

    /** Every place some block covers. */
    public SortedSet<String> covers() {
        SortedSet<String> found = new TreeSet<>();
        for (Block one : blocks) {
            found.addAll(one.covers());
        }
        return Collections.unmodifiableSortedSet(found);
    }
}
