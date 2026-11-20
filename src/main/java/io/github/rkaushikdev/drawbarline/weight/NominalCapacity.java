package io.github.rkaushikdev.drawbarline.weight;

/**
 * What the trade means by a seventy or a hundred ton car.
 *
 * <p>A hundred ton car does not weigh a hundred tons. It is a nominal capacity class, and what it
 * really names is a gross rail load of 263,000 pounds, so each class carries its real number.
 */
public enum NominalCapacity {
    SEVENTY(70, 220000),
    HUNDRED(100, 263000),
    ONE_TEN(110, 286000),
    ONE_TWENTY_FIVE(125, 315000);

    private final int tons;
    private final long grossRailLoadPounds;

    NominalCapacity(int tons, long grossRailLoadPounds) {
        this.tons = tons;
        this.grossRailLoadPounds = grossRailLoadPounds;
    }

    /** The nominal tons the class is named for. */
    public int value() {
        return tons;
    }

    /** The real number the class stands for. */
    public Weight grossRailLoad() {
        return new Weight(grossRailLoadPounds);
    }

    /** {@code 125 ton}. */
    public String label() {
        return tons + " ton";
    }
}
