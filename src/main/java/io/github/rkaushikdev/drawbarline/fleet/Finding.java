package io.github.rkaushikdev.drawbarline.fleet;

import java.util.Objects;

/** One reason to turn a car back, with the detail that lets somebody argue about it. */
public record Finding(Refusal reason, String detail) {

    public Finding {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(detail, "detail");
    }

    /** A reason that needs no detail. */
    public Finding(Refusal reason) {
        this(reason, "");
    }

    /** The reason, with the detail in brackets when there is one. */
    @Override
    public String toString() {
        if (detail.isEmpty()) {
            return reason.text();
        }
        return reason.text() + " (" + detail + ")";
    }
}
