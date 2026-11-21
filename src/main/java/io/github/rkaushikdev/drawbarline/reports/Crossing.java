package io.github.rkaushikdev.drawbarline.reports;

import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import java.util.Objects;

/**
 * What went each way at one junction with one road.
 *
 * @param received the cars we took from the road there
 * @param delivered the cars we handed to the road there
 */
public record Crossing(String junction, ReportingMark road, int received, int delivered) {

    public Crossing {
        Objects.requireNonNull(junction, "junction");
        Objects.requireNonNull(road, "road");
    }

    /** Positive when we took more than we gave, which grows the fleet on hand. */
    public int net() {
        return received - delivered;
    }
}
