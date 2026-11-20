package io.github.rkaushikdev.drawbarline;

import io.github.rkaushikdev.drawbarline.util.Literal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Base for every refusal the application makes on purpose.
 *
 * <p>A caller can catch this and be sure it caught something raised deliberately. Anything else
 * escaping from the application is a defect, not a message about the data.
 *
 * <p>Each error carries a short stable {@link #code() code} and a mapping of the values that
 * caused it. The code is what a report groups on, so it never changes once an error class is in
 * use. The context is what an operator reads.
 */
public class DrawbarlineError extends RuntimeException {

    /** The code of the base class, which no leaf shares. */
    public static final String CODE = "drawbarline";

    private final String message;
    private final Map<String, Object> context;

    public DrawbarlineError(String message) {
        this(message, Map.of());
    }

    public DrawbarlineError(String message, Map<String, ?> context) {
        super(message);
        this.message = Objects.requireNonNull(message, "message");
        this.context = Collections.unmodifiableMap(new LinkedHashMap<>(context));
    }

    /** The stable code a report groups on. Every subclass answers its own. */
    public String code() {
        return CODE;
    }

    /** The sentence on its own, without the context. */
    public String message() {
        return message;
    }

    /** The values that caused the refusal, in the order they were given. */
    public Map<String, Object> context() {
        return context;
    }

    /**
     * The sentence followed by the context in key order, each value written as a literal: {@code
     * no rate in effect (car_type='A402', on='2025-01-04')}.
     */
    @Override
    public String getMessage() {
        if (context.isEmpty()) {
            return message;
        }
        String shown = new TreeMap<>(context)
                .entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + Literal.of(entry.getValue()))
                        .collect(Collectors.joining(", "));
        return message + " (" + shown + ")";
    }

    /** The class, the sentence and the code: {@code UnknownCar('never heard of it', code='unknown_car')}. */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + Literal.of(message) + ", code=" + Literal.of(code()) + ")";
    }
}
