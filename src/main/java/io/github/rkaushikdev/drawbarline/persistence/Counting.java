package io.github.rkaushikdev.drawbarline.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Counting rows without reading them.
 *
 * <p>A repository answers how many by sending one aggregate and reading one number back, never by
 * selecting a column and counting what came across the wire. On the event table the difference is
 * tens of thousands of values to answer one question, and the next table is always the big one.
 *
 * <p>A count too large for an {@code int} reads as {@link Integer#MAX_VALUE}, the way a collection
 * reports its size.
 */
public final class Counting {

    private Counting() {}

    /** One condition on the rows of a table, built against the table's root. */
    @FunctionalInterface
    public interface Condition<T> {

        /** The condition, for the rows of {@code row}. */
        Predicate on(CriteriaBuilder where, Root<T> row);
    }

    /** How many rows the table behind an entity has. */
    public static int countOf(EntityManager session, Class<?> table) {
        CriteriaQuery<Long> query = session.getCriteriaBuilder().createQuery(Long.class);
        query.select(session.getCriteriaBuilder().count(query.from(table)));
        return counted(session, query);
    }

    /** How many rows match every condition, for the counts a report asks for. */
    @SafeVarargs
    public static <T> int countWhere(EntityManager session, Class<T> table, Condition<T>... conditions) {
        CriteriaBuilder where = session.getCriteriaBuilder();
        CriteriaQuery<Long> query = where.createQuery(Long.class);
        Root<T> row = query.from(table);
        Predicate[] all = new Predicate[conditions.length];
        for (int at = 0; at < conditions.length; at++) {
            all[at] = conditions[at].on(where, row);
        }
        query.select(where.count(row)).where(all);
        return counted(session, query);
    }

    private static int counted(EntityManager session, CriteriaQuery<Long> query) {
        Long found = session.createQuery(query).getSingleResult();
        return found == null ? 0 : (int) Math.min(found, Integer.MAX_VALUE);
    }
}
