package io.github.rkaushikdev.drawbarline.persistence;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.embargo.Embargo;
import io.github.rkaushikdev.drawbarline.embargo.EmbargoError;
import io.github.rkaushikdev.drawbarline.embargo.EmbargoRegister;
import io.github.rkaushikdev.drawbarline.persistence.models.EmbargoRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;

/**
 * Every embargo the road has issued, live or lifted.
 *
 * <p>The whole register loads, because there are never many live embargoes and every movement has
 * to be checked against all of them. Lifting one goes through the domain register, so the rule that
 * a lift cannot come before the embargo took effect is enforced in one place.
 */
public final class EmbargoRepository {

    private final EntityManager session;

    public EmbargoRepository(EntityManager session) {
        this.session = session;
    }

    static Embargo embargoFromRow(EmbargoRow row) {
        return new Embargo(
                row.getNumber(),
                row.getEffective(),
                row.getReason(),
                row.getStation(),
                row.getCommodityFamily(),
                row.getRoad(),
                row.getExpires(),
                row.isPermitsAllowed());
    }

    static EmbargoRow rowFromEmbargo(Embargo embargo) {
        return new EmbargoRow(
                embargo.number(),
                embargo.effective(),
                embargo.reason(),
                embargo.station().orElse(null),
                embargo.commodityFamily().orElse(null),
                embargo.road().orElse(null),
                embargo.expires().orElse(null),
                embargo.permitsAllowed());
    }

    private EmbargoRow row(int number) {
        EmbargoRow found = session.find(EmbargoRow.class, number);
        if (found == null) {
            throw new EmbargoError("no such embargo", Details.of("number", number));
        }
        return found;
    }

    /** Issue an embargo. A number already used is refused. */
    public Embargo issue(Embargo embargo) {
        if (session.find(EmbargoRow.class, embargo.number()) != null) {
            throw new EmbargoError("that embargo is already issued", Details.of("number", embargo.number()));
        }
        session.persist(rowFromEmbargo(embargo));
        session.flush();
        return embargo;
    }

    public Embargo get(int number) {
        return embargoFromRow(row(number));
    }

    /** Lift an embargo after a day, which writes the day it expires onto its row. */
    public Embargo lift(int number, LocalDate on) {
        EmbargoRow found = row(number);
        EmbargoRegister register = new EmbargoRegister();
        register.issue(embargoFromRow(found));
        Embargo lifted = register.lift(number, on);
        found.setExpires(lifted.expires().orElse(null));
        session.flush();
        return lifted;
    }

    /** Every embargo, by number. */
    public List<Embargo> all() {
        return session
                .createQuery("select e from EmbargoRow e order by e.number", EmbargoRow.class)
                .getResultList()
                .stream()
                .map(EmbargoRepository::embargoFromRow)
                .toList();
    }

    /** The embargoes in force on a day, by number. */
    public List<Embargo> inForceOn(LocalDate day) {
        return all().stream().filter(one -> one.inForceOn(day)).toList();
    }

    /** Build the register in memory that movements are checked against. */
    public EmbargoRegister register() {
        EmbargoRegister found = new EmbargoRegister();
        all().forEach(found::issue);
        return found;
    }

    /** How many embargoes have been issued. */
    public int size() {
        return Counting.countOf(session, EmbargoRow.class);
    }
}
