package io.github.rkaushikdev.drawbarline.fleet;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * A term during which somebody other than the owner collects for a car. A term with no end is
 * open; both end days are covered, because a lease runs to the end of its last day.
 */
public record Lease(ReportingMark lessee, LocalDate began, Optional<LocalDate> ended) {

    public Lease {
        Objects.requireNonNull(lessee, "lessee");
        Objects.requireNonNull(began, "began");
        Objects.requireNonNull(ended, "ended");
        if (ended.isPresent() && ended.get().isBefore(began)) {
            throw new OwnershipError(
                    "a lease cannot end before it begins",
                    Details.of(
                            "lessee", lessee.toString(),
                            "began", began.toString(),
                            "ended", ended.get().toString()));
        }
    }

    /** An open term. */
    public Lease(ReportingMark lessee, LocalDate began) {
        this(lessee, began, Optional.empty());
    }

    /** A term with its last day. */
    public Lease(ReportingMark lessee, LocalDate began, LocalDate ended) {
        this(lessee, began, Optional.of(ended));
    }

    public boolean openEnded() {
        return ended.isEmpty();
    }

    /** Whether the term runs on a day, both ends counted. */
    public boolean covers(LocalDate day) {
        if (day.isBefore(began)) {
            return false;
        }
        return ended.isEmpty() || !day.isAfter(ended.get());
    }

    /** Whether the two terms share a day. */
    public boolean overlaps(Lease other) {
        boolean left = ended.isEmpty() || !other.began.isAfter(ended.get());
        boolean right = other.ended.isEmpty() || !began.isAfter(other.ended.get());
        return left && right;
    }

    /** {@code CN from 2025-01-01 to 2025-06-30}, or {@code to open}. */
    @Override
    public String toString() {
        String until = ended.map(LocalDate::toString).orElse("open");
        return lessee + " from " + began + " to " + until;
    }
}
