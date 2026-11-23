package io.github.rkaushikdev.drawbarline.hazmat;

import io.github.rkaushikdev.drawbarline.util.Text;

/** The nine hazard classes, as the placard names them. */
public enum HazardClass {
    EXPLOSIVE(1),
    GAS(2),
    FLAMMABLE_LIQUID(3),
    FLAMMABLE_SOLID(4),
    OXIDIZER(5),
    POISON(6),
    RADIOACTIVE(7),
    CORROSIVE(8),
    MISCELLANEOUS(9);

    private final int number;

    HazardClass(int number) {
        this.number = number;
    }

    /** The class number printed on the placard. */
    public int number() {
        return number;
    }

    /** The class in words: {@code flammable liquid}. */
    public String text() {
        return Text.lower(name()).replace('_', ' ');
    }

    /**
     * Whether a car of this class may not be coupled to a car of the other. Explosives are kept
     * apart from every other placarded class but the miscellaneous one, and an oxidizer is kept
     * apart from anything that burns. The reading runs both ways.
     */
    public boolean keptApartFrom(HazardClass other) {
        return separates(this, other) || separates(other, this);
    }

    private static boolean separates(HazardClass one, HazardClass other) {
        return switch (one) {
            case EXPLOSIVE -> other != EXPLOSIVE && other != MISCELLANEOUS;
            case OXIDIZER -> other == FLAMMABLE_LIQUID || other == FLAMMABLE_SOLID;
            default -> false;
        };
    }

    /** Whether a car of this class has to be kept off the engine and off an occupied car. */
    public boolean needsBuffer() {
        return this != MISCELLANEOUS;
    }
}
