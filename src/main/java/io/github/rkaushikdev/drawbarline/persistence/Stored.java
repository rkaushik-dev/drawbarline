package io.github.rkaushikdev.drawbarline.persistence;

import io.github.rkaushikdev.drawbarline.ConfigurationError;
import io.github.rkaushikdev.drawbarline.Details;

/**
 * The two ways a value and its column can disagree, refused in one place.
 *
 * <p>The domain counts cents and tenths in 64 bits while several columns hold 32, so a value too
 * large for its column is refused rather than cut short. And a name stored for a constant has to
 * name one on the way back: a hand edited row that names nothing is refused at the read, where the
 * row can still be found, rather than turning into a wrong answer further on.
 */
final class Stored {

    private Stored() {}

    /** The value as its whole number column holds it. */
    static int whole(long value, String column) {
        if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
            throw new StoreError(
                    "that number is too large for its column", Details.of("column", column, "value", value));
        }
        return (int) value;
    }

    /** The constant a stored name names. */
    static <E extends Enum<E>> E named(Class<E> kind, String name) {
        for (E one : kind.getEnumConstants()) {
            if (one.name().equals(name)) {
                return one;
            }
        }
        throw new ConfigurationError(
                "the books hold a name nobody publishes", Details.of("kind", kind.getSimpleName(), "name", name));
    }
}
