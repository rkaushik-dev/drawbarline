package io.github.rkaushikdev.drawbarline.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One thing wrong with the shape of a request: what kind of problem, where it is, and the sentence
 * a caller is shown.
 *
 * @param type a short stable name for the kind of problem, such as {@code missing}
 * @param loc where it is, from {@code body}, {@code query} or {@code path} down: names for fields,
 *     whole numbers for positions in a list
 * @param msg the sentence a caller is shown
 */
public record Problem(String type, List<Object> loc, String msg) {

    /** A field nobody filled in. */
    public static final String MISSING = "missing";

    /** A field nobody declared. */
    public static final String EXTRA = "extra_forbidden";

    /** A rule about the body as a whole that it broke. */
    public static final String VALUE_ERROR = "value_error";

    public Problem {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(msg, "msg");
        loc = List.copyOf(loc);
    }

    /** The problem as it goes over the wire. */
    public Map<String, Object> asMap() {
        Map<String, Object> shown = new LinkedHashMap<>();
        shown.put("type", type);
        shown.put("loc", loc);
        shown.put("msg", msg);
        return shown;
    }
}
