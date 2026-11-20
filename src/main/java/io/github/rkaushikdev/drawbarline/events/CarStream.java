package io.github.rkaushikdev.drawbarline.events;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.EventError;
import io.github.rkaushikdev.drawbarline.clock.Interval;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Every event recorded against one car, in the order they happened.
 *
 * <p>Events arrive out of order: a junction sends its interchange sheet the next morning and a
 * correction for last Tuesday lands on Friday. The stream keeps itself sorted by each event's own
 * moment, so reading it back gives the same sequence whatever order the paper came in. An event
 * arriving with the same sort key as one already held goes after it.
 */
public final class CarStream implements Iterable<Event> {

    private final CarMark car;
    private final List<Event> events = new ArrayList<>();
    private final Set<Event> seen = new HashSet<>();

    public CarStream(CarMark car) {
        this.car = Objects.requireNonNull(car, "car");
    }

    /** The car this stream belongs to. */
    public CarMark car() {
        return car;
    }

    /** Put an event in its place. The same event twice is refused. */
    public Event record(Event event) {
        if (!event.car().equals(car)) {
            throw new EventError(
                    "that event belongs to another car",
                    Details.of("stream", car.toString(), "event", event.car().toString()));
        }
        if (seen.contains(event)) {
            throw new EventError("that event is already on the stream", Details.of("event", event.toString()));
        }
        events.add(insertionPoint(event.order()), event);
        seen.add(event);
        return event;
    }

    /** The first position whose key sorts after {@code key}, so equal keys keep arrival order. */
    private int insertionPoint(Event.Order key) {
        int low = 0;
        int high = events.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (key.compareTo(events.get(middle).order()) < 0) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }
        return low;
    }

    public List<Event> ofKind(EventKind kind) {
        return events.stream().filter(event -> event.kind() == kind).toList();
    }

    public Optional<Event> lastOf(EventKind kind) {
        for (int at = events.size() - 1; at >= 0; at--) {
            if (events.get(at).kind() == kind) {
                return Optional.of(events.get(at));
            }
        }
        return Optional.empty();
    }

    public Optional<Event> firstOf(EventKind kind) {
        return events.stream().filter(event -> event.kind() == kind).findFirst();
    }

    /** The events inside a window. */
    public List<Event> during(Interval window) {
        return events.stream().filter(event -> window.contains(event.when())).toList();
    }

    /** Everything before the end of a window, which is how a state is rebuilt. */
    public List<Event> upTo(Interval window) {
        return events.stream()
                .filter(event -> event.when().isBefore(window.end()))
                .toList();
    }

    public Optional<Event> first() {
        return events.isEmpty() ? Optional.empty() : Optional.of(events.get(0));
    }

    public Optional<Event> last() {
        return events.isEmpty() ? Optional.empty() : Optional.of(events.get(events.size() - 1));
    }

    /** The events in order, over a copy so the stream itself cannot be changed through it. */
    @Override
    public Iterator<Event> iterator() {
        return List.copyOf(events).iterator();
    }

    /** The events in order, as a list of their own. */
    public List<Event> events() {
        return List.copyOf(events);
    }

    public int size() {
        return events.size();
    }

    public boolean isEmpty() {
        return events.isEmpty();
    }
}
