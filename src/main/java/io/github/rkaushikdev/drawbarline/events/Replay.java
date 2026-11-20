package io.github.rkaushikdev.drawbarline.events;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.EventError;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import java.time.Instant;

/**
 * Rebuilding what a car is doing from what has happened to it.
 *
 * <p>Replay is where the sequence is judged. A stream holds any event it is handed, because a
 * clerk keying an interchange sheet should not be stopped by an argument about a movement three
 * weeks ago. Replay is where a car delivered twice, or placed while it is sitting on somebody
 * else's railroad, turns into a refusal with the offending event named.
 */
public final class Replay {

    private Replay() {}

    /** Walk a whole stream and hand back what the car is doing at the end of it. */
    public static CarState replay(CarStream stream) {
        CarState state = new CarState(stream.car());
        for (Event event : stream) {
            state = apply(state, event);
        }
        return state;
    }

    /** Walk a stream up to and including a moment. */
    public static CarState replay(CarStream stream, Instant until) {
        CarState state = new CarState(stream.car());
        for (Event event : stream) {
            if (event.when().isAfter(until)) {
                break;
            }
            state = apply(state, event);
        }
        return state;
    }

    /** Replay a stream after checking it is the car being asked about. */
    public static CarState stateOf(CarStream stream, CarMark car) {
        if (!stream.car().equals(car)) {
            throw new EventError(
                    "that stream belongs to another car",
                    Details.of("stream", stream.car().toString(), "asked", car.toString()));
        }
        return replay(stream);
    }

    private static EventError refuse(Event event, String why) {
        return new EventError(why, Details.of("event", event.toString()));
    }

    private static CarState apply(CarState state, Event event) {
        EventKind kind = event.kind();
        if (kind != EventKind.RECEIVED && !state.onLine()) {
            throw refuse(event, "the car is not on our rail");
        }

        return switch (kind) {
            case RECEIVED -> {
                if (state.onLine()) {
                    throw refuse(event, "the car is already on our rail");
                }
                yield state.change()
                        .onLine(true)
                        .station(event.station())
                        .industry(null)
                        .waybill(event.waybill().orElse(null))
                        .receivedAt(event.when())
                        .placedAt(null)
                        .releasedAt(null)
                        .by(event)
                        .done();
            }
            case DELIVERED -> {
                if (state.placed()) {
                    throw refuse(event, "the car has to be pulled before it goes across");
                }
                if (state.badOrdered()) {
                    throw refuse(event, "a bad ordered car does not go across");
                }
                yield state.change()
                        .onLine(false)
                        .station(event.station())
                        .industry(null)
                        .waybill(null)
                        .placedAt(null)
                        .releasedAt(null)
                        .by(event)
                        .done();
            }
            case MOVED -> {
                if (state.placed()) {
                    throw refuse(event, "the car has to be pulled before it moves");
                }
                yield state.change()
                        .station(event.station())
                        .ran(event.tenths().orElse(0))
                        .by(event)
                        .done();
            }
            case PLACED, CONSTRUCTIVE -> {
                if (state.placed()) {
                    throw refuse(event, "the car is already placed");
                }
                yield state.change()
                        .station(event.station())
                        .industry(event.industry().orElse(null))
                        .placedAt(event.when())
                        .releasedAt(null)
                        .by(event)
                        .done();
            }
            case RELEASED -> {
                if (!state.placed()) {
                    throw refuse(event, "a car has to be placed before it can be released");
                }
                if (state.releasedAt().isPresent()) {
                    throw refuse(event, "the car is already released");
                }
                yield state.change().releasedAt(event.when()).by(event).done();
            }
            case PULLED -> {
                if (!state.placed()) {
                    throw refuse(event, "there is nothing to pull");
                }
                yield state.change()
                        .industry(null)
                        .placedAt(null)
                        .releasedAt(null)
                        .by(event)
                        .done();
            }
            case BAD_ORDERED -> {
                if (state.badOrdered()) {
                    throw refuse(event, "the car is already bad ordered");
                }
                yield state.change()
                        .badOrdered(true)
                        .station(event.station())
                        .by(event)
                        .done();
            }
            case REPAIRED -> {
                if (!state.badOrdered()) {
                    throw refuse(event, "the car was not bad ordered");
                }
                yield state.change().badOrdered(false).by(event).done();
            }
            case HELD -> {
                if (state.held()) {
                    throw refuse(event, "the car is already held");
                }
                yield state.change().held(true).by(event).done();
            }
            case FREED -> {
                if (!state.held()) {
                    throw refuse(event, "the car was not held");
                }
                yield state.change().held(false).by(event).done();
            }
            case NOTIFIED, WEIGHED ->
                state.change().station(event.station()).by(event).done();
        };
    }
}
