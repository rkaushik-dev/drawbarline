package io.github.rkaushikdev.drawbarline.api;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.DrawbarlineError;
import java.util.List;
import java.util.Map;

/**
 * A request that is not the shape its route takes: a field missing, a field nobody declared, a
 * value of the wrong kind, a query parameter that cannot be read.
 *
 * <p>This is a malformed request rather than a railroading rule that was broken, so it is answered
 * the way every malformed request is, with an unprocessable status and a {@code detail} list of
 * {@link Problem problems}, and never with the code, message and context a domain refusal carries.
 */
public final class RequestRejected extends DrawbarlineError {

    /** The stable code a report groups on. */
    public static final String CODE = "request";

    private final transient List<Problem> problems;

    public RequestRejected(List<Problem> problems) {
        super(
                "the request is not the shape this route takes",
                Details.of("problems", problems.stream().map(Problem::msg).toList()));
        this.problems = List.copyOf(problems);
    }

    /** A request with one thing wrong with it. */
    public RequestRejected(Problem problem) {
        this(List.of(problem));
    }

    /** A request refused as a whole, for a rule about the body rather than about one field. */
    public RequestRejected(String message) {
        this(message, Map.of());
    }

    /**
     * A request refused as a whole, with the values behind the refusal. Its one problem is the
     * sentence, at the body.
     */
    public RequestRejected(String message, Map<String, ?> context) {
        super(message, context);
        this.problems = List.of(new Problem(Problem.VALUE_ERROR, List.of("body"), message));
    }

    /** What was wrong, in the order it was found. */
    public List<Problem> problems() {
        return problems;
    }

    @Override
    public String code() {
        return CODE;
    }
}
