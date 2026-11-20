package io.github.rkaushikdev.drawbarline.weight;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.util.Map;

/** A weight cannot exist, or two of them do not agree. */
public final class WeightError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "weight";

    public WeightError(String message) {
        super(message);
    }

    public WeightError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
