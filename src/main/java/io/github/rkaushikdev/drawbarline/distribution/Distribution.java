package io.github.rkaushikdev.drawbarline.distribution;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.events.CarState;
import io.github.rkaushikdev.drawbarline.events.EventLog;
import io.github.rkaushikdev.drawbarline.events.Positions;
import io.github.rkaushikdev.drawbarline.fleet.FleetRegister;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.mileage.Mileage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Car orders, and which empties go against them: what went where, and what could not be filled.
 *
 * <p>A shipper orders four covered hoppers for Tuesday and the office has eleven empties standing
 * about the railroad. The rule this road runs on is oldest order first, and inside a day, lowest
 * order number first. It is not the rule that moves the fewest cars and nobody claims it is. It is
 * the rule a shipper can check.
 *
 * <p>An order is filled with the nearest suitable empty, measured along the track rather than as
 * the crow flies, because that is the car the crew can actually get there. Where two are the same
 * distance the lower car number wins, so two runs of the same morning distribute the same cars.
 */
public final class Distribution {

    private final List<Assignment> assignments = new ArrayList<>();
    private final Map<Integer, Integer> shortages = new LinkedHashMap<>();

    Distribution() {}

    /** Every empty assigned, in the order the orders were filled. */
    public List<Assignment> assignments() {
        return Collections.unmodifiableList(assignments);
    }

    /** The orders left short, by order number, with how many cars each is short. */
    public Map<Integer, Integer> shortages() {
        return Collections.unmodifiableMap(shortages);
    }

    /** How many cars were assigned. */
    public int filled() {
        return assignments.size();
    }

    /** How many cars were asked for and not found. */
    public int unfilled() {
        return shortages.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Whether every order was filled. */
    public boolean isComplete() {
        return shortages.isEmpty();
    }

    /** The empties going against one order. */
    public List<Assignment> forOrder(int number) {
        return assignments.stream().filter(one -> one.order() == number).toList();
    }

    /** How far the empties have to run, which is what this costs to do. */
    public int emptyTenths() {
        return assignments.stream().mapToInt(Assignment::tenths).sum();
    }

    /** {@code 1 cars assigned, every order filled}, or how many short. */
    @Override
    public String toString() {
        if (isComplete()) {
            return filled() + " cars assigned, every order filled";
        }
        return filled() + " cars assigned, " + unfilled() + " short";
    }

    /** Every car on our rail, off a customer track, movable, with nothing billed to it, in car order. */
    public static List<Empty> emptiesOnHand(FleetRegister register, EventLog log, Instant at) {
        Map<CarMark, CarState> where = Positions.positions(log, at);
        List<Empty> found = new ArrayList<>();
        for (CarMark car : Positions.unplaced(where)) {
            CarState state = where.get(car);
            if (!register.has(car) || !state.movable() || state.waybill().isPresent()) {
                continue;
            }
            found.add(new Empty(car, register.get(car).kind(), state.station().orElse("")));
        }
        found.sort(Comparator.comparing(one -> one.car().toString()));
        return List.copyOf(found);
    }

    /**
     * Fill the orders oldest first, each car from the nearest suitable empty left.
     *
     * @param stations the station each industry track is at
     */
    public static Distribution distribute(List<CarOrder> orders, List<Empty> empties, Map<String, String> stations) {
        List<Empty> left = new ArrayList<>(empties);
        Distribution found = new Distribution();
        List<CarOrder> queue =
                orders.stream().sorted(Comparator.comparing(CarOrder::priority)).toList();
        for (CarOrder order : queue) {
            String wanted = stations.get(order.industry());
            if (wanted == null) {
                throw new DistributionError(
                        "no station for that track", Details.of("order", order.number(), "industry", order.industry()));
            }
            Comparator<Empty> nearest = Comparator.<Empty>comparingInt(
                            one -> Mileage.tenthsBetween(one.station(), wanted))
                    .thenComparing(one -> one.car().toString());
            for (int car = 0; car < order.cars(); car++) {
                List<Empty> suitable =
                        left.stream().filter(one -> one.kind() == order.kind()).toList();
                if (suitable.isEmpty()) {
                    found.shortages.merge(order.number(), 1, Integer::sum);
                    continue;
                }
                Empty chosen = suitable.stream().min(nearest).orElseThrow();
                left.remove(chosen);
                found.assignments.add(new Assignment(order.number(), chosen.car(), chosen.station(), wanted));
            }
        }
        return found;
    }
}
