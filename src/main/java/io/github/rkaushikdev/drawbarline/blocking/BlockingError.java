package io.github.rkaushikdev.drawbarline.blocking;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.util.Map;

/** A block or a plan will not stand. */
public final class BlockingError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "blocking";

    public BlockingError(String message) {
        super(message);
    }

    public BlockingError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
