package io.github.rkaushikdev.drawbarline;

import java.util.Map;

/** Something the operator set up is wrong or missing. */
public final class ConfigurationError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "configuration";

    public ConfigurationError(String message) {
        super(message);
    }

    public ConfigurationError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
