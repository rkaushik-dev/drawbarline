package io.github.rkaushikdev.drawbarline.customers;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.util.Map;

/** A shipper or an industry is not on the register. */
public final class CustomerError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "customer";

    public CustomerError(String message) {
        super(message);
    }

    public CustomerError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
