package io.github.rkaushikdev.drawbarline.waybill;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.util.Map;

/** A waybill contradicts itself or names a place we do not serve. */
public final class WaybillError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "waybill";

    public WaybillError(String message) {
        super(message);
    }

    public WaybillError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
