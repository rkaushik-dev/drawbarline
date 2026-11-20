package io.github.rkaushikdev.drawbarline;

import java.util.Map;

/** A reporting mark or car number that cannot be what it claims to be. */
public final class MarkError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "mark";

    public MarkError(String message) {
        super(message);
    }

    public MarkError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
