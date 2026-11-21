package io.github.rkaushikdev.drawbarline.persistence;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.sql.SQLException;
import java.util.Map;

/** The database would not do what was asked of it: it is unreachable, or a statement failed. */
public final class StoreError extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "store";

    public StoreError(String message) {
        super(message);
    }

    public StoreError(String message, Map<String, ?> context) {
        super(message, context);
    }

    @Override
    public String code() {
        return CODE;
    }

    /** A refusal naming the database and what the driver said, with the driver's error as the cause. */
    static StoreError of(String message, String url, SQLException trouble) {
        StoreError found = new StoreError(message, Details.of("url", url, "reason", trouble.getMessage()));
        found.initCause(trouble);
        return found;
    }
}
