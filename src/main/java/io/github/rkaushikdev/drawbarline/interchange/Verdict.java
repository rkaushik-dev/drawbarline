package io.github.rkaushikdev.drawbarline.interchange;

import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import java.util.List;
import java.util.Objects;

/**
 * One answer for the crew at the junction: whether a car goes across, and if not, every reason it
 * is held in the order somebody would find them.
 */
public record Verdict(CarMark car, String junction, ReportingMark road, List<String> findings) {

    public Verdict {
        Objects.requireNonNull(car, "car");
        Objects.requireNonNull(junction, "junction");
        Objects.requireNonNull(road, "road");
        findings = List.copyOf(findings);
    }

    /** Whether nothing stops the car. */
    public boolean goes() {
        return findings.isEmpty();
    }

    /** {@code CN 400100 goes to CN at ESC}, or the car held with the first reason. */
    @Override
    public String toString() {
        if (goes()) {
            return car + " goes to " + road + " at " + junction;
        }
        return car + " held at " + junction + ": " + findings.get(0);
    }
}
