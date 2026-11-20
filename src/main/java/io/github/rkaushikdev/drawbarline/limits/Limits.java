package io.github.rkaushikdev.drawbarline.limits;

import io.github.rkaushikdev.drawbarline.ConfigurationError;
import io.github.rkaushikdev.drawbarline.Details;

/**
 * How long a field is allowed to be.
 *
 * <p>Every one of these numbers appears twice: once in the domain object that refuses a value too
 * long, and once in the column that stores it. They live here, once, and both sides use them, so
 * a note the domain accepts always fits its column. None of them is a rule about railroading; they
 * are what fits on the form.
 */
public final class Limits {

    /** What somebody writes on an event to explain a correction. */
    public static final int NOTE = 120;

    /** The initials at the bottom of a form. */
    public static final int ACTOR = 32;

    /** What a decision was about. */
    public static final int SUBJECT = 60;

    /** What was decided, in a sentence. */
    public static final int DETAIL = 200;

    /** A name as it appears on paperwork. */
    public static final int PARTY_NAME = 60;

    public static final int STATION_CODE = 4;
    public static final int SHIPPER_CODE = 8;
    public static final int INDUSTRY_CODE = 12;
    public static final int TARIFF_CODE = 8;
    public static final int BLOCK_CODE = 8;

    /** Free text that travels on a document. */
    public static final int REASON = 200;

    public static final int REFERENCE = 60;

    /** Codes that are not ours. */
    public static final int JOB_CODE = 4;

    public static final int WHY_MADE_CODE = 2;
    public static final int COMMODITY_CODE = 7;
    public static final int CAR_MARK = 11;
    public static final int REPORTING_MARK = 4;
    public static final int TYPE_CODE = 4;
    public static final int DESIGNATION = 4;

    private Limits() {}

    /** Whether the text fits a field of {@code cap} characters. */
    public static boolean within(String text, int cap) {
        return text.length() <= cap;
    }

    /** Hand the text back, or say which field it does not fit. */
    public static String fits(String text, int cap, String what) {
        if (!within(text, cap)) {
            throw new ConfigurationError(
                    "that does not fit the field", Details.of("field", what, "length", text.length(), "holds", cap));
        }
        return text;
    }
}
