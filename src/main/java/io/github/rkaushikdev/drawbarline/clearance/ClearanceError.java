package io.github.rkaushikdev.drawbarline.clearance;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.util.Map;

/** A dimension or a place cannot be checked. */
public final class ClearanceError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "clearance";

    public ClearanceError(String message) {
        super(message);
    }

    public ClearanceError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
