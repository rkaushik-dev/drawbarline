package io.github.rkaushikdev.drawbarline.repairs;

import io.github.rkaushikdev.drawbarline.BillingError;
import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.util.Objects;
import java.util.Optional;

/**
 * One line of a repair card: what was wrong, what was done about it, how many of the part, and how
 * old the part that came off was. An inspector may say a party other than the why made code's
 * usual one answers for it, but only with a reason on file.
 *
 * @param overridden the party an inspector found answers for the line, when it is not the usual one
 * @param partYears the age of the part that came off, which depreciation is figured on
 * @param note the inspector's reason, required with an override
 */
public record Defect(
        JobCode job, WhyMade why, int quantity, Optional<Responsibility> overridden, int partYears, String note) {

    public Defect {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(why, "why");
        Objects.requireNonNull(overridden, "overridden");
        Objects.requireNonNull(note, "note");
        if (quantity < 1) {
            throw new BillingError("a repair line covers at least one part", Details.of("job", job.toString()));
        }
        if (partYears < 0) {
            throw new BillingError("a part has no negative age", Details.of("job", job.toString()));
        }
        if (overridden.isPresent() && Text.isBlank(note)) {
            throw new BillingError("an overridden responsibility carries a reason", Details.of("job", job.toString()));
        }
    }

    /** One new part, answered for by whoever the why made code says. */
    public Defect(JobCode job, WhyMade why) {
        this(job, why, 1, Optional.empty(), 0, "");
    }

    public Defect withQuantity(int quantity) {
        return new Defect(job, why, quantity, overridden, partYears, note);
    }

    public Defect withPartYears(int partYears) {
        return new Defect(job, why, quantity, overridden, partYears, note);
    }

    public Defect withNote(String note) {
        return new Defect(job, why, quantity, overridden, partYears, note);
    }

    /** The same line with an inspector's finding of who answers for it, keeping the note it has. */
    public Defect withOverridden(Responsibility who) {
        return new Defect(job, why, quantity, Optional.of(who), partYears, note);
    }

    /** The same line with an inspector's finding of who answers for it and the reason. */
    public Defect withOverridden(Responsibility who, String reason) {
        return new Defect(job, why, quantity, Optional.of(who), partYears, reason);
    }

    /** Who pays for the line: the inspector's finding, or the why made code's usual party. */
    public Responsibility answeredBy() {
        return Codes.responsibilityFor(why, overridden);
    }

    /** {@code 4312 why 01 x1 to owner}. */
    @Override
    public String toString() {
        return job + " why " + why + " x" + quantity + " to " + answeredBy().text();
    }
}
