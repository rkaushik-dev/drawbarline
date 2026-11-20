package io.github.rkaushikdev.drawbarline.fleet;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.util.regex.Pattern;

/**
 * The short letter code a car has carried since long before computers: one to four letters, the
 * first of which says what the car is and the rest how it is equipped.
 */
public record MechanicalDesignation(String letters) {

    private static final Pattern DESIGNATION = Pattern.compile("[A-Z]{1,4}");

    public MechanicalDesignation {
        if (letters == null || !DESIGNATION.matcher(letters).matches()) {
            throw new EquipmentError("a designation is one to four capitals", Details.of("letters", letters));
        }
        CarKind.fromLetter(letters.substring(0, 1));
    }

    /** Read a designation, taking lower case and surrounding space. */
    public static MechanicalDesignation parse(String text) {
        return new MechanicalDesignation(Text.upper(Text.strip(text)));
    }

    public CarKind kind() {
        return CarKind.fromLetter(letters.substring(0, 1));
    }

    /** The letters after the kind, which say how the car is equipped. */
    public String qualifiers() {
        return letters.substring(1);
    }

    @Override
    public String toString() {
        return letters;
    }
}
