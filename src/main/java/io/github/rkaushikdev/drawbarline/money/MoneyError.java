package io.github.rkaushikdev.drawbarline.money;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.util.Map;

/** An amount cannot be read, or an allocation cannot be made. */
public final class MoneyError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "money";

    public MoneyError(String message) {
        super(message);
    }

    public MoneyError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
