package io.github.rkaushikdev.drawbarline.distribution;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.util.Map;

/** A car order cannot be taken or cannot be filled as asked. */
public final class DistributionError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "distribution";

    public DistributionError(String message) {
        super(message);
    }

    public DistributionError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
