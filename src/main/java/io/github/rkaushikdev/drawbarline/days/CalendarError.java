package io.github.rkaushikdev.drawbarline.days;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.util.Map;

/** A day cannot be placed on the calendar. */
public final class CalendarError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "calendar";

    public CalendarError(String message) {
        super(message);
    }

    public CalendarError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
