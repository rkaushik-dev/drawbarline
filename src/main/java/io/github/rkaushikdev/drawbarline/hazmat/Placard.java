package io.github.rkaushikdev.drawbarline.hazmat;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.util.Locale;
import java.util.Objects;

/** What goes on the four sides of a car carrying a hazardous commodity. */
public record Placard(HazardClass hazard, int unNumber, String name) {

    public Placard {
        Objects.requireNonNull(hazard, "hazard");
        if (unNumber < 1 || unNumber > 3999) {
            throw new HazmatError("a un number is four digits", Details.of("given", unNumber));
        }
        if (Text.isBlank(name)) {
            throw new HazmatError("a placard names what is in the car");
        }
    }

    /** {@code sodium hydroxide solution UN1824 class 8}. */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s UN%04d class %d", name, unNumber, hazard.number());
    }
}
