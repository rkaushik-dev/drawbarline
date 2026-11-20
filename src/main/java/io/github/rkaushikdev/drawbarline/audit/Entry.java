package io.github.rkaushikdev.drawbarline.audit;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.limits.Limits;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * One decision, one person, one moment.
 *
 * <p>The actor is the initials on the bottom of the form, trimmed and checked against nothing:
 * there is no user table on this railroad, and the value of the name is that somebody can be
 * asked.
 *
 * @param at when the decision was made
 * @param actor who made it
 * @param action what kind of decision it was
 * @param subject what it was about: a month, a car, an embargo
 * @param detail anything worth saying about it, possibly nothing
 */
public record Entry(Instant at, String actor, Action action, String subject, String detail) {

    private static final DateTimeFormatter SHOWN =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT).withZone(ZoneOffset.UTC);

    public Entry {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(detail, "detail");
        String cleaned = Text.strip(actor);
        if (cleaned.isEmpty()) {
            throw new AuditError("an entry names who did it", Details.of("action", action.name()));
        }
        if (cleaned.length() > Limits.ACTOR) {
            throw new AuditError("that is a signature, not initials", Details.of("actor", actor));
        }
        if (detail.length() > Limits.DETAIL) {
            throw new AuditError("that detail is too long to carry", Details.of("length", detail.length()));
        }
        actor = cleaned;
        if (Text.isBlank(subject)) {
            throw new AuditError("an entry names what it is about", Details.of("action", action.name()));
        }
    }

    /** An entry with nothing more to say than what it was about. */
    public Entry(Instant at, String actor, Action action, String subject) {
        this(at, actor, action, subject, "");
    }

    /** {@code 2025-02-10 14:30Z rjs closed a period 2025-01 (cn sent a corrected sheet)}. */
    @Override
    public String toString() {
        String tail = detail.isEmpty() ? "" : " (" + detail + ")";
        return SHOWN.format(at) + "Z " + actor + " " + action.text() + " " + subject + tail;
    }
}
