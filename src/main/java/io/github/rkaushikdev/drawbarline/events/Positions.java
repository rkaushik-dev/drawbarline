package io.github.rkaushikdev.drawbarline.events;

import io.github.rkaushikdev.drawbarline.marks.CarMark;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Where every car is at one moment.
 *
 * <p>Which cars are on hand, which are standing empty and which are on a particular industry track
 * are all readings of one replay of every car's stream. Doing the walk once means the answers come
 * from one replay and cannot disagree about whether a car is placed.
 */
public final class Positions {

    private Positions() {}

    /** Replay every car's stream once, up to a moment, keyed by car in mark order. */
    public static Map<CarMark, CarState> positions(EventLog log, Instant at) {
        Map<CarMark, CarState> found = new LinkedHashMap<>();
        for (CarMark car : log.cars()) {
            found.put(car, Replay.replay(log.stream(car), at));
        }
        return Collections.unmodifiableMap(found);
    }

    /** Every car we have taken and not handed back, in mark order. */
    public static List<CarMark> onLine(Map<CarMark, CarState> found) {
        return cars(found, CarState::onLine);
    }

    /** Every car standing on one industry track. */
    public static List<CarMark> onTrack(Map<CarMark, CarState> found, String industry) {
        return cars(found, state -> state.industry().filter(industry::equals).isPresent());
    }

    /** Every car standing on somebody's private track. */
    public static List<CarMark> placed(Map<CarMark, CarState> found) {
        return cars(found, CarState::placed);
    }

    /** Every car on our rail that is not sitting on a customer track. */
    public static List<CarMark> unplaced(Map<CarMark, CarState> found) {
        return cars(found, state -> state.onLine() && !state.placed());
    }

    /** Every car that is not bad ordered or held. */
    public static List<CarMark> movable(Map<CarMark, CarState> found) {
        return cars(found, CarState::movable);
    }

    /** One car's state, empty rather than missing when nothing has happened to it. */
    public static CarState stateOf(Map<CarMark, CarState> found, CarMark car) {
        CarState state = found.get(car);
        return state == null ? new CarState(car) : state;
    }

    private static List<CarMark> cars(Map<CarMark, CarState> found, Predicate<CarState> test) {
        return found.entrySet().stream()
                .filter(entry -> test.test(entry.getValue()))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }
}
