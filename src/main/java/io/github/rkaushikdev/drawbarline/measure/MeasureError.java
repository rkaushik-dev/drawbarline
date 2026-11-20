package io.github.rkaushikdev.drawbarline.measure;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.util.Map;

/** A length cannot be read, or cannot exist. */
public final class MeasureError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "measure";

    public MeasureError(String message) {
        super(message);
    }

    public MeasureError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
