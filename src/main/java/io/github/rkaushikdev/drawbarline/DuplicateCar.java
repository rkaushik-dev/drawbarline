package io.github.rkaushikdev.drawbarline;

import java.util.Map;

/** A car was registered twice under the same marks. */
public final class DuplicateCar extends RegistryError {

    /** The stable code a report groups on. */
    public static final String CODE = "duplicate_car";

    public DuplicateCar(String message) {
        super(message);
    }

    public DuplicateCar(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
