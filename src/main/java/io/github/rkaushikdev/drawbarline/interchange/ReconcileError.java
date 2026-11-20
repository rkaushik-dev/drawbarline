package io.github.rkaushikdev.drawbarline.interchange;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.util.Map;

/** A comparison of two interchange records cannot be made as asked. */
public final class ReconcileError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "reconcile";

    public ReconcileError(String message) {
        super(message);
    }

    public ReconcileError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
