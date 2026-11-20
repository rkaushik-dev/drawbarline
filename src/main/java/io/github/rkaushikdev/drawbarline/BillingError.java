package io.github.rkaushikdev.drawbarline;

import java.util.Map;

/** A repair bill will not assemble. */
public final class BillingError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "billing";

    public BillingError(String message) {
        super(message);
    }

    public BillingError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
