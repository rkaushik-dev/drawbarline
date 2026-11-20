package io.github.rkaushikdev.drawbarline.hazmat;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.util.Map;

/** A placard or a placement will not stand. */
public final class HazmatError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "hazmat";

    public HazmatError(String message) {
        super(message);
    }

    public HazmatError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
