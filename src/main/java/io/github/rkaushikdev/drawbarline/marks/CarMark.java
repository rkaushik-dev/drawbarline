package io.github.rkaushikdev.drawbarline.marks;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.MarkError;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A reporting mark and a number, which together name one car anywhere on the continent.
 *
 * <p>Numbers are often stencilled with a check digit after a dash. A number is accepted with or
 * without it and refused where it disagrees.
 */
public record CarMark(ReportingMark mark, int number) implements Comparable<CarMark> {

    private static final Pattern SPLIT = Pattern.compile("([A-Z]{2,4})[\\s.]*([0-9]{1,6})(?:-([0-9]))?");

    private static final Comparator<CarMark> ORDER =
            Comparator.comparing(CarMark::mark).thenComparingInt(CarMark::number);

    public CarMark {
        Objects.requireNonNull(mark, "mark");
        if (number < 1 || number > Marks.MAX_CAR_NUMBER) {
            throw new MarkError("a car number is one to six digits", Details.of("number", number));
        }
    }

    /** Read {@code DBLX 104207}, {@code DBLX104207} or {@code DBLX 104207-6}. */
    public static CarMark parse(String text) {
        Matcher found = SPLIT.matcher(Text.upper(Text.strip(text)));
        if (!found.matches()) {
            throw new MarkError("cannot read that as a car", Details.of("text", text));
        }
        CarMark car = new CarMark(new ReportingMark(found.group(1)), Integer.parseInt(found.group(2)));
        String stencilled = found.group(3);
        if (stencilled != null && Integer.parseInt(stencilled) != car.checkDigit()) {
            throw new MarkError(
                    "the stencilled check digit does not match the number",
                    Details.of("text", text, "expected", car.checkDigit()));
        }
        return car;
    }

    public int checkDigit() {
        return Marks.checkDigit(number);
    }

    public boolean isPrivate() {
        return mark.isPrivate();
    }

    /** How the car reads on its side, check digit and all: {@code DBLX 104207-6}. */
    public String stencilled() {
        return String.format(Locale.ROOT, "%s %06d-%d", mark, number, checkDigit());
    }

    @Override
    public int compareTo(CarMark other) {
        return ORDER.compare(this, other);
    }

    /** The marks and the number padded to six digits: {@code DBLX 004207}. */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s %06d", mark, number);
    }
}
