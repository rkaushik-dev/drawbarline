package io.github.rkaushikdev.drawbarline;

import java.util.Map;

/** The demurrage tariff cannot answer for this car. */
public final class TariffError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "tariff";

    public TariffError(String message) {
        super(message);
    }

    public TariffError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
