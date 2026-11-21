package io.github.rkaushikdev.drawbarline.persistence;

import io.github.rkaushikdev.drawbarline.audit.Action;
import io.github.rkaushikdev.drawbarline.audit.AuditTrail;
import io.github.rkaushikdev.drawbarline.audit.Entry;
import io.github.rkaushikdev.drawbarline.clock.Clock;
import io.github.rkaushikdev.drawbarline.persistence.models.AuditRow;
import io.github.rkaushikdev.drawbarline.util.Text;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.util.List;

/**
 * Every decision, written down.
 *
 * <p>Append only, in the strongest sense available: there is no update and no delete here at all.
 * An entry written in error is answered with another entry, the same way a posting is.
 */
public final class AuditRepository {

    private static final String NEWEST_FIRST = "select a from AuditRow a order by a.at desc, a.id desc";

    private final EntityManager session;

    public AuditRepository(EntityManager session) {
        this.session = session;
    }

    static Entry entryFromRow(AuditRow row) {
        return new Entry(
                row.getAt(),
                row.getActor(),
                Stored.named(Action.class, row.getAction()),
                row.getSubject(),
                row.getDetail());
    }

    static AuditRow rowFromEntry(Entry entry) {
        return new AuditRow(entry.at(), entry.actor(), entry.action().name(), entry.subject(), entry.detail());
    }

    /** Write down a decision made now. */
    public Entry record(String actor, Action action, String subject) {
        return record(actor, action, subject, "", Clock.now());
    }

    /** Write down a decision made now, with something said about it. */
    public Entry record(String actor, Action action, String subject, String detail) {
        return record(actor, action, subject, detail, Clock.now());
    }

    /** Write down a decision made at a moment. */
    public Entry record(String actor, Action action, String subject, Instant at) {
        return record(actor, action, subject, "", at);
    }

    /** Write down a decision made at a moment, with something said about it. */
    public Entry record(String actor, Action action, String subject, String detail, Instant at) {
        Entry entry = new Entry(at, actor, action, subject, detail);
        session.persist(rowFromEntry(entry));
        session.flush();
        return entry;
    }

    /** Every entry, the newest first. */
    public List<Entry> entries() {
        return entries(session.createQuery(NEWEST_FIRST, AuditRow.class));
    }

    /** The newest entries, as many as the limit allows. */
    public List<Entry> entries(int limit) {
        return entries(session.createQuery(NEWEST_FIRST, AuditRow.class).setMaxResults(limit));
    }

    /** One person's decisions, in the order they were made; the name is trimmed before it is compared. */
    public List<Entry> byActor(String actor) {
        return entries(
                session.createQuery("select a from AuditRow a where a.actor = :actor order by a.at", AuditRow.class)
                        .setParameter("actor", Text.strip(actor)));
    }

    /** The decisions about one subject, in the order they were made. */
    public List<Entry> about(String subject) {
        return entries(
                session.createQuery("select a from AuditRow a where a.subject = :subject order by a.at", AuditRow.class)
                        .setParameter("subject", subject));
    }

    /** The decisions of one kind, in the order they were made. */
    public List<Entry> ofAction(Action action) {
        return entries(
                session.createQuery("select a from AuditRow a where a.action = :action order by a.at", AuditRow.class)
                        .setParameter("action", action.name()));
    }

    /** Build the trail in memory from every entry on file. */
    public AuditTrail trail() {
        AuditTrail found = new AuditTrail();
        for (Entry entry : entries()) {
            found.record(entry.actor(), entry.action(), entry.subject(), entry.detail(), entry.at());
        }
        return found;
    }

    /** How many entries are on file. */
    public int size() {
        return Counting.countOf(session, AuditRow.class);
    }

    private static List<Entry> entries(TypedQuery<AuditRow> query) {
        return query.getResultList().stream().map(AuditRepository::entryFromRow).toList();
    }
}
