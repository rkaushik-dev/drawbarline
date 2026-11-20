package io.github.rkaushikdev.drawbarline.events;

import io.github.rkaushikdev.drawbarline.clock.Interval;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every event for every car.
 *
 * <p>The one thing the log refuses is the same event twice. Duplicate interchange sheets are the
 * most common thing a connection sends us, and a second copy of a receipt would open a second car
 * hire liability against the same car.
 */
public final class EventLog {

    private static final Comparator<Event> IN_ORDER = Comparator.comparing(Event::order);

    private final Map<CarMark, CarStream> streams = new LinkedHashMap<>();

    /** Put an event on its car's stream, opening the stream if this is the car's first. */
    public Event record(Event event) {
        return streams.computeIfAbsent(event.car(), CarStream::new).record(event);
    }

    /** Record every event of a batch, handing back how many there were. */
    public int recordAll(List<Event> events) {
        for (Event event : events) {
            record(event);
        }
        return events.size();
    }

    /** One car's stream, empty rather than missing when nothing has happened to it. */
    public CarStream stream(CarMark car) {
        CarStream found = streams.get(car);
        return found == null ? new CarStream(car) : found;
    }

    public boolean has(CarMark car) {
        return streams.containsKey(car);
    }

    /** Every car with something recorded, in mark order. */
    public List<CarMark> cars() {
        return streams.keySet().stream().sorted().toList();
    }

    /** Every event inside a window, across cars, in order. */
    public List<Event> during(Interval window) {
        List<Event> found = new ArrayList<>();
        for (CarStream stream : streams.values()) {
            found.addAll(stream.during(window));
        }
        found.sort(IN_ORDER);
        return List.copyOf(found);
    }

    /** Every event at one station, across cars, in order. */
    public List<Event> atStation(String station) {
        List<Event> found = new ArrayList<>();
        for (CarStream stream : streams.values()) {
            for (Event event : stream) {
                if (event.station().equals(station)) {
                    found.add(event);
                }
            }
        }
        found.sort(IN_ORDER);
        return List.copyOf(found);
    }

    /** Every event of one kind, across cars, in order. */
    public List<Event> ofKind(EventKind kind) {
        List<Event> found = new ArrayList<>();
        for (CarStream stream : streams.values()) {
            found.addAll(stream.ofKind(kind));
        }
        found.sort(IN_ORDER);
        return List.copyOf(found);
    }

    /** How many events the log holds, across every car. */
    public int size() {
        int total = 0;
        for (CarStream stream : streams.values()) {
            total += stream.size();
        }
        return total;
    }
}
