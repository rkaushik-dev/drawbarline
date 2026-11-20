package io.github.rkaushikdev.drawbarline.network;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.util.Map;

/** A place or a road is not on the register. */
public final class NetworkError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "network";

    public NetworkError(String message) {
        super(message);
    }

    public NetworkError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
