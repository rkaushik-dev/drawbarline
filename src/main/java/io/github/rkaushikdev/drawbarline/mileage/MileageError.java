package io.github.rkaushikdev.drawbarline.mileage;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.util.Map;

/** Two places cannot be measured between. */
public final class MileageError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "mileage";

    public MileageError(String message) {
        super(message);
    }

    public MileageError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
