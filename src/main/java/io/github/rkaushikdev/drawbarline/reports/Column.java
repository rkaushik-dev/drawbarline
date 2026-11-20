package io.github.rkaushikdev.drawbarline.reports;

import java.util.List;
import java.util.Objects;

/**
 * One column of a report: its heading, which side its values sit on, and the narrowest it may be.
 * A column is widened to its widest value and never truncated, because a car number with its last
 * digit cut off is worse than a line that wraps.
 */
public record Column(String heading, Align align, int width) {

    public Column {
        Objects.requireNonNull(heading, "heading");
        Objects.requireNonNull(align, "align");
    }

    /** A column of text. */
    public Column(String heading) {
        this(heading, Align.LEFT, 0);
    }

    /** A column with its values on one side, as wide as it needs to be. */
    public Column(String heading, Align align) {
        this(heading, align, 0);
    }

    /** A column of text at least {@code width} wide. */
    public Column(String heading, int width) {
        this(heading, Align.LEFT, width);
    }

    /** The width this column needs to hold its heading and every value. */
    public int sized(List<String> values) {
        int found = Math.max(width, heading.length());
        for (String value : values) {
            found = Math.max(found, value.length());
        }
        return found;
    }

    /** A value padded out to {@code width} on the column's side. */
    public String lay(String value, int width) {
        if (value.length() >= width) {
            return value;
        }
        String padding = " ".repeat(width - value.length());
        return align == Align.RIGHT ? padding + value : value + padding;
    }
}
