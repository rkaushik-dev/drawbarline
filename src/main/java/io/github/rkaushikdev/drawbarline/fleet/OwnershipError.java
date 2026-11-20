package io.github.rkaushikdev.drawbarline.fleet;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.util.Map;

/** A lease term cannot sit where it is being put. */
public final class OwnershipError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "ownership";

    public OwnershipError(String message) {
        super(message);
    }

    public OwnershipError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
