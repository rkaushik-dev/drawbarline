package io.github.rkaushikdev.drawbarline.measure;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A length in whole inches.
 *
 * <p>Car dimensions come off a stencil and out of the equipment register in feet and inches,
 * never in decimal feet, and clearance is decided on the inch. Holding whole inches means no
 * length is ever compared with a tolerance, and feet and inches only exist at the edges where
 * somebody reads or writes one.
 *
 * <p>Nothing here is negative. A negative length is always a subtraction done in the wrong order,
 * and it is cheaper to find that at the subtraction than three screens later.
 */
public record Length(int inches) implements Comparable<Length> {

    public static final int INCHES_PER_FOOT = 12;

    /** No length at all. */
    public static final Length ZERO = new Length(0);

    private static final List<Pattern> FORMS = List.of(
            Pattern.compile("(?<feet>\\d+)\\s*'\\s*(?:(?<inches>\\d{1,2})\\s*\")?"),
            Pattern.compile("(?<feet>\\d+)\\s*ft\\.?\\s*(?:(?<inches>\\d{1,2})\\s*in\\.?)?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?<feet>\\d+)\\s*-\\s*(?<inches>\\d{1,2})"),
            // Bare inches carry no feet, so the feet group is there and always empty.
            Pattern.compile("(?<feet>)(?<inches>\\d+)\\s*\""));

    public Length {
        if (inches < 0) {
            throw new MeasureError("a length is not negative", Details.of("inches", inches));
        }
    }

    /** Whole feet. */
    public static Length of(int feet) {
        return of(feet, 0);
    }

    /** Feet and inches. The inches part may exceed eleven. */
    public static Length of(int feet, int inches) {
        return new Length(feet * INCHES_PER_FOOT + inches);
    }

    /** Read the forms a register and a stencil use: {@code 89'4"}, {@code 89 ft 4 in}, {@code 89-4}, {@code 9"}. */
    public static Length parse(String text) {
        String cleaned = Text.strip(text);
        for (Pattern form : FORMS) {
            Matcher found = form.matcher(cleaned);
            if (!found.matches()) {
                continue;
            }
            int feet = group(found, "feet");
            int inches = group(found, "inches");
            if (feet != 0 && inches >= INCHES_PER_FOOT) {
                throw new MeasureError(
                        "a written length carries under twelve inches", Details.of("text", text, "inches", inches));
            }
            return of(feet, inches);
        }
        throw new MeasureError("cannot read that as a length", Details.of("text", text));
    }

    private static int group(Matcher found, String name) {
        String value = found.group(name);
        return value == null || value.isEmpty() ? 0 : Integer.parseInt(value);
    }

    /** Whole feet, discarding the inches. */
    public int feet() {
        return Math.floorDiv(inches, INCHES_PER_FOOT);
    }

    /** The inches left over after the whole feet. */
    public int inchesPart() {
        return Math.floorMod(inches, INCHES_PER_FOOT);
    }

    /** Decimal feet, for a report that wants one. Never for arithmetic. */
    public double inFeet() {
        return inches / (double) INCHES_PER_FOOT;
    }

    public Length plus(Length other) {
        return new Length(inches + other.inches);
    }

    public Length minus(Length other) {
        if (other.inches > inches) {
            throw new MeasureError(
                    "that subtraction runs past zero", Details.of("left", toString(), "right", other.toString()));
        }
        return new Length(inches - other.inches);
    }

    public Length times(int count) {
        if (count < 0) {
            throw new MeasureError("a length does not scale by a negative", Details.of("count", count));
        }
        return new Length(inches * count);
    }

    @Override
    public int compareTo(Length other) {
        return Integer.compare(inches, other.inches);
    }

    /** Feet with a tick, then any inches with a double tick: {@code 89'4"}. */
    @Override
    public String toString() {
        if (inchesPart() == 0) {
            return feet() + "'";
        }
        return feet() + "'" + inchesPart() + "\"";
    }

    /** The longest of several, refusing an empty list rather than guessing zero. */
    public static Length longest(List<Length> lengths) {
        if (lengths.isEmpty()) {
            throw new MeasureError("no lengths to compare");
        }
        return Collections.max(lengths);
    }

    /** The sum, which is zero over nothing because a train of no cars is short. */
    public static Length total(List<Length> lengths) {
        Length running = ZERO;
        for (Length one : lengths) {
            running = running.plus(one);
        }
        return running;
    }
}
