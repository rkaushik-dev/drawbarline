package io.github.rkaushikdev.drawbarline;

import java.util.Map;

/** An event cannot be placed on a car's stream. */
public final class EventError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "event";

    public EventError(String message) {
        super(message);
    }

    public EventError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
