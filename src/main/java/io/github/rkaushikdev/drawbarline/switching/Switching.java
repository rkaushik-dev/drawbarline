package io.github.rkaushikdev.drawbarline.switching;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.customers.CustomerRegister;
import io.github.rkaushikdev.drawbarline.customers.Industry;
import io.github.rkaushikdev.drawbarline.events.CarState;
import io.github.rkaushikdev.drawbarline.events.EventLog;
import io.github.rkaushikdev.drawbarline.events.Positions;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Building the switch list for a station.
 *
 * <p>The local works one station at a time. It pulls the cars the customers have released, then it
 * spots the cars the yard has for them, and the order matters: a track with eleven spots and eleven
 * cars on it has room for a twelfth only because the crew took one off first. Planning the
 * placements before the pulls gives a list with constructive placements on it that would not have
 * happened, and the customer is charged for them.
 *
 * <p>Three things stop a car and each reads differently on the list. A car that is bad ordered or
 * held does not move at all. A track not switched today is not worked. A track that is genuinely
 * full takes a constructive placement, which starts the customer's clock without the car ever being
 * spotted.
 *
 * <p>The whole log is replayed once at the top of the plan, the same walk the on hand and the empty
 * reports use. Nothing here records anything: it produces a list, the crew works it, and what
 * actually happened comes back as events.
 */
public final class Switching {

    private Switching() {}

    /** Every car standing on one industry track at a moment, in car order. */
    public static List<CarMark> carsOnTrack(EventLog log, String industry, Instant at) {
        return Positions.onTrack(Positions.positions(log, at), industry);
    }

    /** Work out the list, pulls first so the placements see the room they make. */
    public static SwitchList plan(
            EventLog log,
            CustomerRegister customers,
            String station,
            LocalDate day,
            Instant at,
            List<Placement> orders) {
        List<Move> moves = new ArrayList<>();
        Map<CarMark, CarState> where = Positions.positions(log, at);
        Map<String, List<CarMark>> standingOn = new HashMap<>();

        for (Industry track : customers.atStation(station)) {
            List<CarMark> standing = Positions.onTrack(where, track.code());
            standingOn.put(track.code(), new ArrayList<>(standing));
            if (!track.switchedOn(day)) {
                continue;
            }
            for (CarMark car : standing) {
                CarState state = Positions.stateOf(where, car);
                if (state.releasedAt().isEmpty()) {
                    continue;
                }
                if (state.badOrdered() || state.held()) {
                    moves.add(new Move(car, MoveKind.STOPPED, track.code(), why(state)));
                    continue;
                }
                moves.add(new Move(car, MoveKind.PULL, track.code()));
                standingOn.get(track.code()).remove(car);
            }
        }

        for (Placement order : orders) {
            CarMark car = order.car();
            String industry = order.industry();
            Industry track = customers.industry(industry);
            if (!track.station().equals(station)) {
                throw new SwitchError(
                        "that track is at another station",
                        Details.of("industry", industry, "station", station, "track_station", track.station()));
            }
            CarState state = Positions.stateOf(where, car);
            if (!state.onLine()) {
                throw new SwitchError("that car is not on our rail", Details.of("car", car.toString()));
            }
            if (state.badOrdered() || state.held()) {
                moves.add(new Move(car, MoveKind.STOPPED, industry, why(state)));
                continue;
            }
            if (!track.switchedOn(day)) {
                moves.add(new Move(car, MoveKind.NOT_SWITCHED, industry));
                continue;
            }
            List<CarMark> standing =
                    standingOn.computeIfAbsent(industry, code -> new ArrayList<>(Positions.onTrack(where, code)));
            if (track.full(standing.size())) {
                moves.add(new Move(car, MoveKind.CONSTRUCTIVE, industry, standing.size() + " on hand"));
                continue;
            }
            moves.add(new Move(car, MoveKind.PLACE, industry));
            standing.add(car);
        }

        return new SwitchList(station, day, moves);
    }

    /** How a stopped car reads on the list: {@code bad order}, {@code held}, or both. */
    private static String why(CarState state) {
        List<String> reasons = new ArrayList<>();
        if (state.badOrdered()) {
            reasons.add("bad order");
        }
        if (state.held()) {
            reasons.add("held");
        }
        return String.join(" and ", reasons);
    }
}
