package io.github.rkaushikdev.drawbarline.train;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.util.Map;

/** A train cannot be made up as asked. */
public final class ConsistError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "consist";

    public ConsistError(String message) {
        super(message);
    }

    public ConsistError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
