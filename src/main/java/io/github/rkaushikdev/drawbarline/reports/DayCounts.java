package io.github.rkaushikdev.drawbarline.reports;

/**
 * What happened in one day, by the kind of thing it was.
 *
 * @param placed actual and constructive placements together
 */
public record DayCounts(int received, int delivered, int placed, int pulled, int badOrdered) {

    /** The industry work: placements and pulls. */
    public int worked() {
        return placed + pulled;
    }

    /** Positive when the railroad grew, which is more cars to pay for. */
    public int netCars() {
        return received - delivered;
    }
}
