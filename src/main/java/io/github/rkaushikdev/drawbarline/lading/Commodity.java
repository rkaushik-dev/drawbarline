package io.github.rkaushikdev.drawbarline.lading;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.util.Objects;

/** A named product, as the tariff file carries it. */
public record Commodity(Stcc code, String name, boolean bulk) {

    public static final Commodity WHEAT = new Commodity(new Stcc("0113710"), "wheat", true);
    public static final Commodity PULPWOOD = new Commodity(new Stcc("2411110"), "pulpwood", true);
    public static final Commodity KRAFT_PAPER = new Commodity(new Stcc("2621315"), "kraft paper");
    public static final Commodity IRON_ORE_PELLETS = new Commodity(new Stcc("1011710"), "iron ore pellets", true);
    public static final Commodity SODIUM_HYDROXIDE = new Commodity(new Stcc("4930040"), "sodium hydroxide solution");
    public static final Commodity LUMBER = new Commodity(new Stcc("2421110"), "rough sawn lumber");

    public Commodity {
        Objects.requireNonNull(code, "code");
        if (Text.isBlank(name)) {
            throw new LadingError("a commodity needs a name", Details.of("code", code.toString()));
        }
    }

    /** A product shipped in packages rather than loose. */
    public Commodity(Stcc code, String name) {
        this(code, name, false);
    }

    public boolean hazardous() {
        return code.hazardous();
    }

    /** {@code 01 137 10 wheat}. */
    @Override
    public String toString() {
        return code + " " + name;
    }
}
