package io.github.rkaushikdev.drawbarline.fleet;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.util.Map;

/** An equipment record contradicts itself. */
public final class CarRecordError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "car_record";

    public CarRecordError(String message) {
        super(message);
    }

    public CarRecordError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
