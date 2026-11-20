package io.github.rkaushikdev.drawbarline.marks;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.MarkError;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.util.regex.Pattern;

/**
 * Two to four letters stencilled on a car, saying who answers for it.
 *
 * <p>A mark ending in X belongs to somebody who is not a railroad. That single letter decides
 * whether car hire is owed to a road or rent is owed to a lessor, so it has its own predicate.
 */
public record ReportingMark(String letters) implements Comparable<ReportingMark> {

    private static final Pattern MARK = Pattern.compile("[A-Z]{2,4}");

    public ReportingMark {
        if (letters == null) {
            throw new MarkError("a reporting mark is letters", Details.of("letters", null));
        }
        if (!MARK.matcher(letters).matches()) {
            throw new MarkError("a reporting mark is two to four capitals", Details.of("letters", letters));
        }
    }

    /** Read a mark, taking lower case and surrounding space. */
    public static ReportingMark parse(String text) {
        return new ReportingMark(Text.upper(Text.strip(text)));
    }

    /** True when the owner is not a railroad, which the trailing X says. */
    public boolean isPrivate() {
        return letters.endsWith("X");
    }

    /** The mark with any private suffix taken off. */
    public String road() {
        return isPrivate() ? letters.substring(0, letters.length() - 1) : letters;
    }

    @Override
    public int compareTo(ReportingMark other) {
        return letters.compareTo(other.letters);
    }

    @Override
    public String toString() {
        return letters;
    }
}
