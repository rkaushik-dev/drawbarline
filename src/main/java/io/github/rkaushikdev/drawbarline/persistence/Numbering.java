package io.github.rkaushikdev.drawbarline.persistence;

import io.github.rkaushikdev.drawbarline.ConfigurationError;
import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.persistence.models.CounterRow;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Handing out document numbers without leaving gaps.
 *
 * <p>The rule the office cares about is that invoice 5001 is followed by invoice 5002 and that
 * nobody can produce 5002 twice. Both halves come from the same place: a number is taken by an
 * update inside the caller's transaction, so a second caller waits, and a caller that rolls back
 * gives its number up.
 *
 * <p>A series that has never been used starts wherever the caller says it starts, once. Asking
 * again with a different start is refused rather than quietly ignored, because a second start is
 * somebody trying to renumber a series that has documents in it.
 */
public final class Numbering {

    public static final String INVOICES = "demurrage_invoice";
    public static final String STATEMENTS = "statement";
    public static final String REPAIR_CARDS = "repair_card";
    public static final String WAYBILLS = "waybill";

    /** Every series there is. */
    public static final List<String> SERIES = List.of(INVOICES, STATEMENTS, REPAIR_CARDS, WAYBILLS);

    private final EntityManager session;

    public Numbering(EntityManager session) {
        this.session = session;
    }

    private CounterRow row(String name) {
        return session.find(CounterRow.class, name);
    }

    /** Open a series. Doing it twice is refused, not ignored. */
    public int start(String name, int at) {
        if (!SERIES.contains(name)) {
            throw new ConfigurationError("no such document series", Details.of("series", name));
        }
        if (at < 1) {
            throw new ConfigurationError("a series starts at one or later", Details.of("at", at));
        }
        if (row(name) != null) {
            throw new ConfigurationError("that series is already open", Details.of("series", name));
        }
        session.persist(new CounterRow(name, at));
        session.flush();
        return at;
    }

    /** The number the series will hand out next, without taking it. */
    public int peek(String name) {
        CounterRow found = row(name);
        if (found == null) {
            throw new ConfigurationError("that series has not been opened", Details.of("series", name));
        }
        return found.getNextNumber();
    }

    /** One number, taken inside the caller's transaction. */
    public int take(String name) {
        return takeMany(name, 1).get(0);
    }

    /** A run of numbers, which is what cutting a month of statements needs. */
    public List<Integer> takeMany(String name, int count) {
        if (count < 1) {
            throw new ConfigurationError("ask for at least one number", Details.of("count", count));
        }
        CounterRow found = row(name);
        if (found == null) {
            throw new ConfigurationError("that series has not been opened", Details.of("series", name));
        }
        int first = found.getNextNumber();
        session.createQuery("update CounterRow c set c.nextNumber = c.nextNumber + :count where c.name = :name")
                .setParameter("count", count)
                .setParameter("name", name)
                .executeUpdate();
        session.refresh(found);
        return IntStream.range(first, first + count).boxed().toList();
    }

    /** Every series that has been opened, in name order. */
    public List<String> openSeries() {
        return session.createQuery("select c.name from CounterRow c order by c.name", String.class)
                .getResultList();
    }
}
