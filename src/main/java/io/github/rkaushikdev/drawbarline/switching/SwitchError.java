package io.github.rkaushikdev.drawbarline.switching;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.util.Map;

/** A switch list cannot be built as asked. */
public final class SwitchError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "switching";

    public SwitchError(String message) {
        super(message);
    }

    public SwitchError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
