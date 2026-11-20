package io.github.rkaushikdev.drawbarline.fleet;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The owner of one car and every term somebody else has held it under.
 *
 * <p>The mark on the side says who owns the car. It does not say who collects for it: a road that
 * leases a hundred covered hoppers to a grain shipper for three years sees the hours run to the
 * shipper for those three years. So ownership is a small history rather than a field. Terms may not
 * overlap, because on any given day exactly one party is owed, and an open term can only be the
 * last, otherwise a term filed later would silently disappear inside it.
 */
public final class Ownership {

    private final ReportingMark owner;
    private final List<Lease> terms = new ArrayList<>();

    public Ownership(ReportingMark owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    public ReportingMark owner() {
        return owner;
    }

    /** File an open term. */
    public Lease lease(ReportingMark lessee, LocalDate began) {
        return lease(new Lease(lessee, began));
    }

    /** File a term with its last day. */
    public Lease lease(ReportingMark lessee, LocalDate began, LocalDate ended) {
        return lease(new Lease(lessee, began, ended));
    }

    /** File a term, refusing one the owner holds or one that overlaps a term already filed. */
    public Lease lease(Lease term) {
        if (term.lessee().equals(owner)) {
            throw new OwnershipError(
                    "an owner does not lease from itself",
                    Details.of("lessee", term.lessee().toString()));
        }
        for (Lease standing : terms) {
            if (standing.overlaps(term)) {
                throw new OwnershipError(
                        "that term overlaps one already filed",
                        Details.of("filed", standing.toString(), "offered", term.toString()));
            }
        }
        terms.add(term);
        terms.sort(Comparator.comparing(Lease::began));
        return term;
    }

    /** Close the open term, which is how a lease is cancelled early. */
    public Lease endOpenTerm(LocalDate on) {
        for (int index = 0; index < terms.size(); index++) {
            Lease term = terms.get(index);
            if (term.openEnded()) {
                Lease closed = new Lease(term.lessee(), term.began(), on);
                terms.set(index, closed);
                return closed;
            }
        }
        throw new OwnershipError("no term is open", Details.of("on", on.toString()));
    }

    /** The term running on a day, if any. */
    public Optional<Lease> termOn(LocalDate day) {
        return terms.stream().filter(term -> term.covers(day)).findFirst();
    }

    /** Who is owed for the car on a day. */
    public ReportingMark holderOn(LocalDate day) {
        return termOn(day).map(Lease::lessee).orElse(owner);
    }

    public boolean leasedOn(LocalDate day) {
        return termOn(day).isPresent();
    }

    /** Every term, in the order they run. */
    public List<Lease> terms() {
        return List.copyOf(terms);
    }
}
