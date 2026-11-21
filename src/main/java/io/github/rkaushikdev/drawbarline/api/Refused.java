package io.github.rkaushikdev.drawbarline.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonMappingException;

/**
 * A value in a request body that is the wrong kind of thing, refused with the kind of problem and
 * the sentence a caller is shown. Jackson adds where in the body it was found as the refusal
 * travels out.
 */
final class Refused extends JsonMappingException {

    private static final long serialVersionUID = 1L;

    private final String type;

    Refused(JsonParser in, String type, String why) {
        super(in, why);
        this.type = type;
    }

    /** The kind of problem, such as {@code datetime_parsing}. */
    String type() {
        return type;
    }

    /** The sentence on its own, without the location Jackson appends. */
    String why() {
        return getOriginalMessage();
    }
}
