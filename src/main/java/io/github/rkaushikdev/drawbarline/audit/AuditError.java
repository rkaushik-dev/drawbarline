package io.github.rkaushikdev.drawbarline.audit;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.util.Map;

/** An entry cannot be written as it stands. */
public final class AuditError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "audit";

    public AuditError(String message) {
        super(message);
    }

    public AuditError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
