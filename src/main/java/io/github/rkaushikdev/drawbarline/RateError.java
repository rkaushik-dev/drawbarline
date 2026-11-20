package io.github.rkaushikdev.drawbarline;

import java.util.Map;

/** No rate, or an impossible one, for the car and date asked about. */
public final class RateError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "rate";

    public RateError(String message) {
        super(message);
    }

    public RateError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
