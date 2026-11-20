package io.github.rkaushikdev.drawbarline.blocking;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.mileage.Mileage;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** One cut of cars for one place or connection, and where it stands in the train. */
public record Block(String code, String name, int position, Set<String> covers) {

    public Block {
        Objects.requireNonNull(name, "name");
        if (!Text.isUpper(code) || !Text.isAlnum(code)) {
            throw new BlockingError("a block code is upper case", Details.of("code", code));
        }
        if (position < 1) {
            throw new BlockingError("blocks stand from position one", Details.of("code", code));
        }
        if (covers.isEmpty()) {
            throw new BlockingError("a block covers at least one place", Details.of("code", code));
        }
        covers = Collections.unmodifiableSortedSet(new TreeSet<>(covers));
        for (String station : covers) {
            Mileage.milepost(station);
        }
    }

    /** Whether a car for this destination goes in this block. */
    public boolean takes(String destination) {
        return covers.contains(destination);
    }

    /** {@code GRB green bay and beyond at 1}. */
    @Override
    public String toString() {
        return code + " " + name + " at " + position;
    }
}
