package io.github.rkaushikdev.drawbarline;

import java.util.Map;

/** A car was named that the register has never heard of. */
public final class UnknownCar extends RegistryError {

    /** The stable code a report groups on. */
    public static final String CODE = "unknown_car";

    public UnknownCar(String message) {
        super(message);
    }

    public UnknownCar(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
