package io.github.rkaushikdev.drawbarline.clock;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The days a filing is in force: from the day it takes effect through its last day, both counted,
 * or with no last day at all while it stands.
 *
 * <p>Agreements, embargoes, leases and weight agreements are all filed this way, and every one of
 * them used to carry its own copy of the same two comparisons. The copies drifted: one counted the
 * last day and one did not, and nobody could say which was meant. A term is the one reading.
 *
 * @param effective the first day in force
 * @param expires the last day in force, absent while the filing stands
 */
public record Term(LocalDate effective, Optional<LocalDate> expires) {

    public Term {
        Objects.requireNonNull(effective, "effective");
        Objects.requireNonNull(expires, "expires");
        if (runsBackwards(effective, expires)) {
            throw new ClockError(
                    "a term cannot end before it begins",
                    Details.of(
                            "effective",
                            effective.toString(),
                            "expires",
                            expires.get().toString()));
        }
    }

    /** A term with no last day. */
    public static Term from(LocalDate effective) {
        return new Term(effective, Optional.empty());
    }

    /** A term through a known last day. */
    public static Term through(LocalDate effective, LocalDate expires) {
        return new Term(effective, Optional.of(expires));
    }

    /**
     * The term a filing states, refusing one that ends before it begins with the filing's own
     * error, so a clerk reads a refusal about the thing they filed rather than about the calendar.
     */
    public static Term checked(
            LocalDate effective, Optional<LocalDate> expires, Supplier<? extends DrawbarlineError> refusal) {
        Objects.requireNonNull(effective, "effective");
        Objects.requireNonNull(expires, "expires");
        if (runsBackwards(effective, expires)) {
            throw refusal.get();
        }
        return new Term(effective, expires);
    }

    private static boolean runsBackwards(LocalDate effective, Optional<LocalDate> expires) {
        return expires.isPresent() && expires.get().isBefore(effective);
    }

    /** Whether nothing has ended the term yet. */
    public boolean openEnded() {
        return expires.isEmpty();
    }

    /** Whether the term is in force on a day, both ends counted. */
    public boolean covers(LocalDate day) {
        if (day.isBefore(effective)) {
            return false;
        }
        return expires.isEmpty() || !day.isAfter(expires.get());
    }

    /** Whether the two terms share a day. */
    public boolean overlaps(Term other) {
        boolean left = expires.isEmpty() || !other.effective.isAfter(expires.get());
        boolean right = other.expires.isEmpty() || !effective.isAfter(other.expires.get());
        return left && right;
    }

    /** The same term with a last day, which is what lifting or ending a standing filing does. */
    public Term endingOn(LocalDate last) {
        return new Term(effective, Optional.of(last));
    }

    /** {@code 2025-01-01 to 2025-06-30}, or {@code 2025-01-01 to open}. */
    @Override
    public String toString() {
        return effective + " to " + expires.map(LocalDate::toString).orElse("open");
    }
}
