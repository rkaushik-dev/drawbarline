package io.github.rkaushikdev.drawbarline.agreements;

import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.util.Map;

/** An agreement cannot stand as written. */
public final class AgreementError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "agreement";

    public AgreementError(String message) {
        super(message);
    }

    public AgreementError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }
}
