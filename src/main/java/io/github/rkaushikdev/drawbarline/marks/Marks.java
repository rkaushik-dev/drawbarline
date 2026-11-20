package io.github.rkaushikdev.drawbarline.marks;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.MarkError;
import java.util.Locale;

/**
 * The check digit stencilled after a car number.
 *
 * <p>It is a modulus ten digit over the six digit number, doubling from the right, and it exists
 * to catch a clerk transposing two digits off a waybill.
 */
public final class Marks {

    /** The largest number a car can carry: six digits. */
    public static final int MAX_CAR_NUMBER = 999999;

    private Marks() {}

    /**
     * The modulus ten check digit for a car number.
     *
     * <p>Digits are taken from the right, every second one doubled, the digits of each product
     * summed, and the total completed to the next multiple of ten.
     */
    public static int checkDigit(int number) {
        if (number < 0 || number > MAX_CAR_NUMBER) {
            throw new MarkError("car number is out of range", Details.of("number", number));
        }
        String digits = String.format(Locale.ROOT, "%06d", number);
        int total = 0;
        for (int position = 0; position < digits.length(); position++) {
            int value = digits.charAt(digits.length() - 1 - position) - '0';
            if (position % 2 == 0) {
                value *= 2;
                if (value > 9) {
                    value -= 9;
                }
            }
            total += value;
        }
        return (10 - total % 10) % 10;
    }
}
