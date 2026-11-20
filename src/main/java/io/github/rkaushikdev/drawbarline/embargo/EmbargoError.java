package io.github.rkaushikdev.drawbarline.embargo;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.util.Map;

/** An embargo cannot be filed or cannot be lifted. */
public final class EmbargoError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "embargo";

    public EmbargoError(String message) {
        super(message);
    }

    public EmbargoError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
