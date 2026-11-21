package io.github.rkaushikdev.drawbarline.api;

import io.github.rkaushikdev.drawbarline.ConfigurationError;
import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.audit.Action;
import io.github.rkaushikdev.drawbarline.audit.Entry;
import io.github.rkaushikdev.drawbarline.util.Text;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The audit routes.
 *
 * <p>Reading only. There is no route that writes an audit entry, because an entry written on its
 * own says a decision was made without the decision having been made. Entries are written by the
 * service layer as a consequence of the thing they record, and that is the only way one can
 * appear.
 */
@RestController
@RequestMapping("/audit")
@Tag(name = "audit")
final class AuditController {

    /** The most entries one request reads back. */
    static final int MOST = 500;

    private final Work work;

    AuditController(Work work) {
        this.work = work;
    }

    /** One decision as it reads back. */
    record EntryOut(Instant at, String actor, String action, String subject, String detail) {

        static EntryOut of(Entry entry) {
            return new EntryOut(
                    entry.at(), entry.actor(), Text.lower(entry.action().name()), entry.subject(), entry.detail());
        }
    }

    /** Decisions, newest first, filtered by whichever of the three is given. */
    @GetMapping
    List<EntryOut> entries(
            @RequestParam(name = "actor", required = false) String actor,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "subject", required = false) String subject,
            @RequestParam(name = "limit", defaultValue = "50")
                    @Min(value = 1, message = "Input should be greater than or equal to 1")
                    @Max(value = MOST, message = "Input should be less than or equal to " + MOST)
                    int limit) {
        return work.read(books -> {
            List<Entry> found;
            if (actor != null && !actor.isEmpty()) {
                found = books.audit().byActor(actor);
            } else if (action != null && !action.isEmpty()) {
                found = books.audit().ofAction(actionNamed(action));
            } else if (subject != null && !subject.isEmpty()) {
                found = books.audit().about(subject);
            } else {
                found = books.audit().entries(limit);
            }
            return found.stream().limit(limit).map(EntryOut::of).toList();
        });
    }

    /** Every decision this application records. */
    @GetMapping("/actions")
    List<String> actions() {
        return Arrays.stream(Action.values()).map(one -> Text.lower(one.name())).toList();
    }

    private static Action actionNamed(String text) {
        String wanted = Text.upper(Text.strip(text));
        for (Action one : Action.values()) {
            if (one.name().equals(wanted)) {
                return one;
            }
        }
        String known = Arrays.stream(Action.values())
                .map(one -> Text.lower(one.name()))
                .collect(Collectors.joining(", "));
        throw new ConfigurationError("no such action", Details.of("given", text, "known", known));
    }
}
