package io.github.rkaushikdev.drawbarline.fleet;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.util.Map;

/** A car kind, designation or type code will not read. */
public final class EquipmentError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "equipment";

    public EquipmentError(String message) {
        super(message);
    }

    public EquipmentError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
