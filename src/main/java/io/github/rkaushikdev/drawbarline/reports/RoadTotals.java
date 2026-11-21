package io.github.rkaushikdev.drawbarline.reports;

import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.util.Objects;

/**
 * What one road billed us for repairs in a month, and how it split.
 *
 * @param lines how many card lines the cards carry between them
 * @param toOwner what goes on the owner's bill, which is wear and joint work
 */
public record RoadTotals(ReportingMark road, int cards, int lines, Money billed, Money toOwner) {

    public RoadTotals {
        Objects.requireNonNull(road, "road");
        Objects.requireNonNull(billed, "billed");
        Objects.requireNonNull(toOwner, "toOwner");
    }

    /** What the road that did the work bears itself. */
    public Money borneByTheRoad() {
        return billed.minus(toOwner);
    }
}
