package io.github.rkaushikdev.drawbarline.scales;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.mileage.Mileage;
import io.github.rkaushikdev.drawbarline.util.Text;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.util.Objects;

/** One track scale: a legal instrument at a station, with the most it can weigh. */
public record Scale(String code, String station, Weight capacity) {

    public Scale {
        Objects.requireNonNull(capacity, "capacity");
        if (!Text.isUpper(code) || !Text.isAlnum(code)) {
            throw new ScaleError("a scale code is upper case", Details.of("code", code));
        }
        Mileage.milepost(station);
        if (capacity.pounds() < 1) {
            throw new ScaleError("a scale weighs something", Details.of("code", code));
        }
    }

    /** {@code ESC1 at ESC}. */
    @Override
    public String toString() {
        return code + " at " + station;
    }
}
