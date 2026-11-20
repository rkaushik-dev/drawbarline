package io.github.rkaushikdev.drawbarline.lading;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.util.Map;

/** A commodity code or a lading will not read. */
public final class LadingError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "lading";

    public LadingError(String message) {
        super(message);
    }

    public LadingError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
