package io.github.rkaushikdev.drawbarline.fleet;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The AAR car type code: the kind letter and three digits that narrow it down to the door width,
 * the roof and the discharge gear.
 */
public record CarTypeCode(String code) {

    private static final Pattern TYPE_CODE = Pattern.compile("([A-Z])(\\d{3})");

    public CarTypeCode {
        if (code == null) {
            throw new EquipmentError("a type code is text", Details.of("code", null));
        }
        Matcher found = TYPE_CODE.matcher(code);
        if (!found.matches()) {
            throw new EquipmentError("a type code is a letter and three digits", Details.of("code", code));
        }
        CarKind.fromLetter(found.group(1));
    }

    /** Read a type code, taking lower case and surrounding space. */
    public static CarTypeCode parse(String text) {
        return new CarTypeCode(Text.upper(Text.strip(text)));
    }

    public CarKind kind() {
        return CarKind.fromLetter(code.substring(0, 1));
    }

    /** The three digits, which narrow the kind down to a body style. */
    public int detail() {
        return Integer.parseInt(code.substring(1));
    }

    @Override
    public String toString() {
        return code;
    }
}
