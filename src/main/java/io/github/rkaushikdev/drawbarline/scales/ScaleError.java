package io.github.rkaushikdev.drawbarline.scales;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.util.Map;

/** A scale, a test or an agreed weight will not stand. */
public final class ScaleError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "scale";

    public ScaleError(String message) {
        super(message);
    }

    public ScaleError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
