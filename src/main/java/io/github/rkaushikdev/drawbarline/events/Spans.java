package io.github.rkaushikdev.drawbarline.events;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.EventError;
import io.github.rkaushikdev.drawbarline.clock.Interval;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Turning a stream of events into the spans somebody is charged for.
 *
 * <p>A car is on our rail from the moment we take it at a junction until the moment we hand it
 * back, and we owe the owner for every hour of that. A car is on a customer track from the moment
 * it is placed until the moment we pull it, and they owe us for the days of that. Underneath they
 * are the same walk: something opens, something else closes it, and whatever is still open at the
 * cut off is cut there. That walk is {@link #scanPairs} and everything here is a reading of it.
 *
 * <p>Both kinds can be open at the end of a month, which is the reason a month closes at all. An
 * open span is closed at the window end for the bill and reopened at the window start of the next,
 * and the two halves add up to the same total as one uninterrupted span would.
 */
public final class Spans {

    /** What a walk calls the state it follows when nobody says. */
    public static final String THAT_STATE = "that state";

    private Spans() {}

    /** Pair openings with closings up to a cut off, watching nothing and naming the state generically. */
    public static List<Pair> scanPairs(CarStream stream, Set<EventKind> opens, Set<EventKind> closes, Instant until) {
        return scanPairs(stream, opens, closes, until, Set.of(), THAT_STATE);
    }

    /** Pair openings with closings, gathering the watched kinds that happen inside each pair. */
    public static List<Pair> scanPairs(
            CarStream stream, Set<EventKind> opens, Set<EventKind> closes, Instant until, Set<EventKind> watch) {
        return scanPairs(stream, opens, closes, until, watch, THAT_STATE);
    }

    /** Pair openings with closings, naming the state in the refusals. */
    public static List<Pair> scanPairs(
            CarStream stream, Set<EventKind> opens, Set<EventKind> closes, Instant until, String what) {
        return scanPairs(stream, opens, closes, until, Set.of(), what);
    }

    /**
     * Walk a stream pairing openings with closings, cutting the last at a cut off.
     *
     * <p>An opening while one is already open, or a closing with nothing open, is refused with the
     * event named and the state described as {@code what}. Events after the cut off are not read.
     */
    public static List<Pair> scanPairs(
            CarStream stream,
            Set<EventKind> opens,
            Set<EventKind> closes,
            Instant until,
            Set<EventKind> watch,
            String what) {
        List<Pair> found = new ArrayList<>();
        Event opened = null;
        List<Event> seen = new ArrayList<>();
        for (Event event : stream) {
            if (event.when().isAfter(until)) {
                break;
            }
            if (opens.contains(event.kind())) {
                if (opened != null) {
                    throw new EventError(what + " was opened twice running", Details.of("event", event.toString()));
                }
                opened = event;
                seen = new ArrayList<>();
            } else if (closes.contains(event.kind())) {
                if (opened == null) {
                    throw new EventError(what + " was closed without opening", Details.of("event", event.toString()));
                }
                found.add(new Pair(opened, Optional.of(event), new Interval(opened.when(), event.when()), seen));
                opened = null;
                seen = new ArrayList<>();
            } else if (opened != null && watch.contains(event.kind())) {
                seen.add(event);
            }
        }
        if (opened != null) {
            found.add(new Pair(opened, Optional.empty(), new Interval(opened.when(), until), seen));
        }
        return List.copyOf(found);
    }

    /** Every stretch the car sat on our rail, up to a cut off. */
    public static List<OnLine> onLineSpans(CarStream stream, Instant until) {
        List<Pair> pairs =
                scanPairs(stream, EnumSet.of(EventKind.RECEIVED), EnumSet.of(EventKind.DELIVERED), until, "the car");
        List<OnLine> found = new ArrayList<>(pairs.size());
        for (Pair pair : pairs) {
            found.add(new OnLine(
                    stream.car(),
                    pair.interval(),
                    pair.openedBy(),
                    pair.closedBy(),
                    pair.openedBy().road(),
                    pair.closedBy().flatMap(Event::road)));
        }
        return List.copyOf(found);
    }

    /** Every stretch the car sat on a customer track, up to a cut off. */
    public static List<OnTrack> onTrackSpans(CarStream stream, Instant until) {
        List<Pair> pairs = scanPairs(
                stream,
                EnumSet.of(EventKind.PLACED, EventKind.CONSTRUCTIVE),
                EnumSet.of(EventKind.PULLED),
                until,
                EnumSet.of(EventKind.RELEASED),
                "the placement");
        List<OnTrack> found = new ArrayList<>(pairs.size());
        for (Pair pair : pairs) {
            found.add(new OnTrack(
                    stream.car(),
                    pair.interval(),
                    pair.openedBy(),
                    pair.closedBy(),
                    pair.openedBy().industry().orElse(""),
                    pair.openedBy().kind() == EventKind.CONSTRUCTIVE,
                    pair.firstInside(EventKind.RELEASED).map(Event::when)));
        }
        return List.copyOf(found);
    }

    /** Only the spans that touch a window, each cut down to it. */
    public static List<Span> within(List<? extends Span> spans, Interval window) {
        List<Span> found = new ArrayList<>();
        for (Span span : spans) {
            if (span.interval().overlaps(window) || span.interval().isEmpty()) {
                found.add(span.clipped(window));
            }
        }
        return List.copyOf(found);
    }
}
