package io.github.rkaushikdev.drawbarline.persistence;

import io.github.rkaushikdev.drawbarline.ConfigurationError;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;

/**
 * One session, one transaction, and every repository riding on it.
 *
 * <p>A caller opens one, does whatever it came to do, and commits. If it does not commit, nothing
 * happened: closing without a commit rolls back, which is the safe reading of somebody forgetting
 * and has caught more than one half written close.
 *
 * <pre>{@code
 * try (UnitOfWork work = database.unitOfWork()) {
 *     int invoice = work.numbering().take(Numbering.INVOICES);
 *     ...
 *     work.commit();
 * }
 * }</pre>
 *
 * <p>Every repository is built over the same entity manager, so what one of them writes the next
 * one reads without a commit. A commit begins the next transaction straight away, so work can carry
 * on in the same unit, and loaded rows stay usable after it.
 *
 * <p>A repository is added as one accessor that builds it over {@link #session()}, the same way
 * {@link #numbering()} is: {@code public FleetRepository fleet() { return new
 * FleetRepository(session()); }}. Building it per call keeps the refusal of a closed unit in one
 * place.
 */
public final class UnitOfWork implements AutoCloseable {

    private final EntityManagerFactory factory;
    private EntityManager session;
    private boolean committed;

    /** A unit of work on a set of books, not yet open. */
    public UnitOfWork(Database database) {
        this(database.entityManagerFactory());
    }

    /** A unit of work over an entity manager factory, not yet open. */
    public UnitOfWork(EntityManagerFactory factory) {
        this.factory = factory;
    }

    /** Open the session and begin its transaction. A unit already open refuses. */
    public UnitOfWork open() {
        if (session != null) {
            throw new ConfigurationError("the unit of work is already open");
        }
        session = factory.createEntityManager();
        committed = false;
        session.getTransaction().begin();
        return this;
    }

    /** The entity manager every repository of this unit shares. */
    public EntityManager session() {
        if (session == null) {
            throw new ConfigurationError("the unit of work is not open");
        }
        return session;
    }

    /** The document number series. */
    public Numbering numbering() {
        return new Numbering(session());
    }

    /** Whether this unit has committed since it was opened. */
    public boolean committed() {
        return committed;
    }

    /** Make the work so far stick, and begin the next transaction. */
    public void commit() {
        EntityTransaction transaction = session().getTransaction();
        transaction.commit();
        committed = true;
        transaction.begin();
    }

    /** Undo the work since the last commit, forget what was loaded, and begin again. */
    public void rollback() {
        EntityTransaction transaction = session().getTransaction();
        if (transaction.isActive()) {
            transaction.rollback();
        }
        session.clear();
        transaction.begin();
    }

    /** Roll back whatever was not committed and close the session. Safe to call when not open. */
    @Override
    public void close() {
        if (session == null) {
            return;
        }
        EntityManager closing = session;
        session = null;
        try {
            EntityTransaction transaction = closing.getTransaction();
            if (transaction.isActive()) {
                transaction.rollback();
            }
        } finally {
            closing.close();
        }
    }
}
