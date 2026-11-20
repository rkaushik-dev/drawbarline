package io.github.rkaushikdev.drawbarline.util;

import java.util.Locale;

/**
 * Character tests and trimming with one meaning everywhere.
 *
 * <p>White space here is every space character, including the no-break spaces a value copied out
 * of a spreadsheet or a mail tends to carry. Letters and digits are Unicode letters and digits.
 * Every filing and command line argument is read through these, so two readers never disagree
 * about whether {@code " ESC "} is a station code.
 */
public final class Text {

    private Text() {}

    /** Whether a character is white space: the ASCII spaces, the separators and no-break spaces. */
    public static boolean isSpace(int point) {
        return (point >= 0x09 && point <= 0x0d)
                || (point >= 0x1c && point <= 0x20)
                || point == 0x85
                || point == 0xa0
                || point == 0x1680
                || (point >= 0x2000 && point <= 0x200a)
                || point == 0x2028
                || point == 0x2029
                || point == 0x202f
                || point == 0x205f
                || point == 0x3000;
    }

    /** The text with white space taken off both ends. */
    public static String strip(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isSpace(text.charAt(start))) {
            start++;
        }
        while (end > start && isSpace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    /** Whether the text is empty once its white space is gone. */
    public static boolean isBlank(String text) {
        return strip(text).isEmpty();
    }

    /** Upper case, the same on every machine whatever its locale. */
    public static String upper(String text) {
        return text.toUpperCase(Locale.ROOT);
    }

    /** Lower case, the same on every machine whatever its locale. */
    public static String lower(String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    /**
     * Whether the text has at least one letter that has a case, and every such letter is a
     * capital. Digits and punctuation neither help nor hurt: {@code "ESC1"} is upper case, {@code
     * "123"} is not.
     */
    public static boolean isUpper(String text) {
        boolean cased = false;
        for (int at = 0; at < text.length(); ) {
            int point = text.codePointAt(at);
            if (Character.isLowerCase(point) || Character.isTitleCase(point)) {
                return false;
            }
            cased |= Character.isUpperCase(point);
            at += Character.charCount(point);
        }
        return cased;
    }

    /** Whether the text is not empty and every character is a letter or a number. */
    public static boolean isAlnum(String text) {
        return !text.isEmpty() && text.codePoints().allMatch(Text::isLetterOrNumber);
    }

    /** Whether the text is not empty and every character is a letter. */
    public static boolean isAlpha(String text) {
        return !text.isEmpty() && text.codePoints().allMatch(Character::isLetter);
    }

    /** Whether the text is not empty and every character is a decimal digit. */
    public static boolean isDigits(String text) {
        return !text.isEmpty() && text.codePoints().allMatch(Character::isDigit);
    }

    private static boolean isLetterOrNumber(int point) {
        if (Character.isLetter(point)) {
            return true;
        }
        int type = Character.getType(point);
        return type == Character.DECIMAL_DIGIT_NUMBER
                || type == Character.LETTER_NUMBER
                || type == Character.OTHER_NUMBER;
    }
}
