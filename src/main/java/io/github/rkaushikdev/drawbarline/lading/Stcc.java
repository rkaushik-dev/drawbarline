package io.github.rkaushikdev.drawbarline.lading;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.util.regex.Pattern;

/**
 * A seven digit standard transportation commodity code.
 *
 * <p>The code narrows from a two digit major group down to the particular product. The first two
 * digits are what a tariff usually keys on and the last five are what a claim argues about.
 * Hazardous material has its own major group, which is why a code beginning 49 changes how the car
 * is handled, placarded and placed.
 */
public record Stcc(String digits) {

    /** The major group hazardous material is filed under. */
    public static final String HAZARDOUS_GROUP = "49";

    private static final Pattern STCC = Pattern.compile("\\d{7}");
    private static final Pattern FAMILY = Pattern.compile("\\d{1,7}");

    public Stcc {
        if (digits == null || !STCC.matcher(digits).matches()) {
            throw new LadingError("a commodity code is seven digits", Details.of("digits", digits));
        }
    }

    /** Read a code with or without the dashes and spaces a tariff prints it with. */
    public static Stcc parse(String text) {
        return new Stcc(Text.strip(text).replace("-", "").replace(" ", ""));
    }

    public String majorGroup() {
        return digits.substring(0, 2);
    }

    public boolean hazardous() {
        return majorGroup().equals(HAZARDOUS_GROUP);
    }

    /** The first {@code level} digits, which is how a tariff matches a family. */
    public String atLevel(int level) {
        if (level < 1 || level > 7) {
            throw new LadingError("a commodity code has seven levels", Details.of("level", level));
        }
        return digits.substring(0, level);
    }

    /** Whether this code sits under a shorter code. */
    public boolean within(String family) {
        if (!FAMILY.matcher(family).matches()) {
            throw new LadingError("a family is one to seven digits", Details.of("family", family));
        }
        return digits.startsWith(family);
    }

    /** The groups a tariff prints: {@code 01 137 10}. */
    @Override
    public String toString() {
        return digits.substring(0, 2) + " " + digits.substring(2, 5) + " " + digits.substring(5);
    }
}
