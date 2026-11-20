package io.github.rkaushikdev.drawbarline.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * How a number is written into a report, a message or a file.
 *
 * <p>Two forms matter. A fixed form with a stated number of places, rounded from the exact binary
 * value with a tie going to the even digit, which is what every column of hours and miles uses.
 * And a general form for a decimal that has no stated precision: the shortest digits that read
 * back as the same number, positional between one ten-thousandth and ten quadrillion and in
 * exponent form outside that, always with a decimal point.
 */
public final class Numbers {

    private Numbers() {}

    /** The number to {@code places} decimal places: {@code fixed(0.17, 2)} is {@code "0.17"}. */
    public static String fixed(double value, int places) {
        if (Double.isNaN(value)) {
            return "nan";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "inf" : "-inf";
        }
        String text =
                new BigDecimal(value).setScale(places, RoundingMode.HALF_EVEN).toPlainString();
        boolean negative = value < 0 || (value == 0 && 1 / value < 0);
        if (negative && !text.startsWith("-")) {
            return "-" + text;
        }
        return text;
    }

    /** The shortest text that reads back as this number: {@code 17.5}, {@code 2.0}, {@code 1e-05}. */
    public static String general(double value) {
        if (Double.isNaN(value)) {
            return "nan";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "inf" : "-inf";
        }
        String sign = value < 0 || (value == 0 && 1 / value < 0) ? "-" : "";
        if (value == 0) {
            return sign + "0.0";
        }
        BigDecimal shortest = new BigDecimal(Double.toString(Math.abs(value))).stripTrailingZeros();
        String digits = shortest.unscaledValue().toString();
        int point = digits.length() - shortest.scale();
        if (point > -4 && point <= 16) {
            return sign + positional(digits, point);
        }
        int exponent = point - 1;
        String mantissa = digits.length() == 1 ? digits : digits.charAt(0) + "." + digits.substring(1);
        String power = String.format(Locale.ROOT, "%02d", Math.abs(exponent));
        return sign + mantissa + "e" + (exponent < 0 ? "-" : "+") + power;
    }

    /** A whole number with its thousands grouped by commas: {@code 263,000}. */
    public static String grouped(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String positional(String digits, int point) {
        if (point <= 0) {
            return "0." + "0".repeat(-point) + digits;
        }
        if (point >= digits.length()) {
            return digits + "0".repeat(point - digits.length()) + ".0";
        }
        return digits.substring(0, point) + "." + digits.substring(point);
    }
}
