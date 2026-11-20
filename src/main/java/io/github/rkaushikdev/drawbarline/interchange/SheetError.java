package io.github.rkaushikdev.drawbarline.interchange;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.util.Map;

/** An interchange sheet cannot be read or cannot be written. */
public final class SheetError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "sheet";

    public SheetError(String message) {
        super(message);
    }

    public SheetError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
