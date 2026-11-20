package io.github.rkaushikdev.drawbarline.clock;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.util.Map;

/** A moment cannot be placed on the clock. */
public final class ClockError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "clock";

    public ClockError(String message) {
        super(message);
    }

    public ClockError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
