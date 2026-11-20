package io.github.rkaushikdev.drawbarline.fleet;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.util.Map;

/** A weighing cannot be recorded as it stands. */
public final class StencilError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "stencil";

    public StencilError(String message) {
        super(message);
    }

    public StencilError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
