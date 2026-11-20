package io.github.rkaushikdev.drawbarline.audit;

import io.github.rkaushikdev.drawbarline.clock.Clock;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * Who did what, and when they did it.
 *
 * <p>Most of the application is already an audit trail, because the event log is never edited and
 * the ledger is never updated. What those leave out is the person. This is a second, thinner log
 * that records only the things a person decides: closing a month, reopening one, retiring a car,
 * lifting an embargo, allowing an exception. It records no reads, because a trail that records
 * reads is a trail nobody can find anything in.
 */
public final class AuditTrail {

    private static final Comparator<Entry> IN_ORDER = Comparator.comparing(Entry::at)
            .thenComparing(entry -> entry.action().name());

    private final List<Entry> entries = new ArrayList<>();

    /** Record a decision made now. */
    public Entry record(String actor, Action action, String subject) {
        return record(actor, action, subject, "", Clock.now());
    }

    /** Record a decision made now, with something said about it. */
    public Entry record(String actor, Action action, String subject, String detail) {
        return record(actor, action, subject, detail, Clock.now());
    }

    /** Record a decision made at a moment. */
    public Entry record(String actor, Action action, String subject, Instant at) {
        return record(actor, action, subject, "", at);
    }

    /** Record a decision made at a moment, with something said about it. */
    public Entry record(String actor, Action action, String subject, String detail, Instant at) {
        Entry entry = new Entry(at, actor, action, subject, detail);
        entries.add(entry);
        return entry;
    }

    /** Every decision, in the order they were made. */
    public List<Entry> entries() {
        return entries.stream().sorted(IN_ORDER).toList();
    }

    /** One person's decisions; the name is trimmed before it is compared. */
    public List<Entry> byActor(String actor) {
        String wanted = Text.strip(actor);
        return entries().stream().filter(entry -> entry.actor().equals(wanted)).toList();
    }

    public List<Entry> ofAction(Action action) {
        return entries().stream().filter(entry -> entry.action() == action).toList();
    }

    /** The decisions about one subject. */
    public List<Entry> about(String subject) {
        return entries().stream()
                .filter(entry -> entry.subject().equals(subject))
                .toList();
    }

    /** The decisions made at or after a moment. */
    public List<Entry> since(Instant moment) {
        return entries().stream().filter(entry -> !entry.at().isBefore(moment)).toList();
    }

    /** Everybody who decided anything, in order. */
    public List<String> actors() {
        TreeSet<String> found = new TreeSet<>();
        for (Entry entry : entries) {
            found.add(entry.actor());
        }
        return List.copyOf(found);
    }

    /** How many decisions are on the trail. */
    public int size() {
        return entries.size();
    }
}
