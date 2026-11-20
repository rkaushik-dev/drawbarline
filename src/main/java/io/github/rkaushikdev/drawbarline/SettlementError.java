package io.github.rkaushikdev.drawbarline;

import java.util.Map;

/** The accounting period refuses the change. */
public final class SettlementError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "settlement";

    public SettlementError(String message) {
        super(message);
    }

    public SettlementError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
