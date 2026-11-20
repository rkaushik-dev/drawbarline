package io.github.rkaushikdev.drawbarline.fleet;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.util.Text;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/**
 * What the scale said, where, and when: the light weight a car carries painted on its side. A
 * weighing at {@link Stencil#NEW} is the builder's certified figure rather than a reweigh; any other
 * place is the station code of the scale, read in capitals.
 */
public record Weighing(Weight pounds, LocalDate on, String where) {

    public Weighing {
        Objects.requireNonNull(pounds, "pounds");
        Objects.requireNonNull(on, "on");
        String place = Text.upper(Text.strip(where));
        if (place.isEmpty()) {
            throw new StencilError("a weighing records where it happened", Details.of("on", on.toString()));
        }
        if (!place.equals(Stencil.NEW) && !Text.isAlnum(place)) {
            throw new StencilError("a scale is named by a station code", Details.of("where", where));
        }
        where = place;
    }

    /** The builder's certified weight. */
    public Weighing(Weight pounds, LocalDate on) {
        this(pounds, on, Stencil.NEW);
    }

    /** The light weight the builder certified, which is not a reweigh. */
    public boolean asBuilt() {
        return where.equals(Stencil.NEW);
    }

    /** How the date reads on the car: the month and a two digit year, {@code 3-15}. */
    public String stamp() {
        return String.format(Locale.ROOT, "%d-%02d", on.getMonthValue(), Math.floorMod(on.getYear(), 100));
    }

    /** {@code ESC 3-15}. */
    @Override
    public String toString() {
        return where + " " + stamp();
    }
}
