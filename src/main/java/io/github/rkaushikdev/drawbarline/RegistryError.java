package io.github.rkaushikdev.drawbarline;

import java.util.Map;

/** The fleet register was asked for something it cannot do. */
public class RegistryError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "registry";

    public RegistryError(String message) {
        super(message);
    }

    public RegistryError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
