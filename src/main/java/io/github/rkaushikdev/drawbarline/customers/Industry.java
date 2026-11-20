package io.github.rkaushikdev.drawbarline.customers;

import io.github.rkaushikdev.drawbarline.ConfigurationError;
import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * A private track off ours that we place cars on.
 *
 * <p>The spot capacity is how many cars physically fit, and it is what turns an actual placement
 * into a constructive one when the twelfth car arrives at an eleven car track. The switching
 * schedule is which days we switch the industry at all, and a car cannot be pulled on a day nobody
 * goes there.
 *
 * @param switched the days of the week the industry is switched, written {@code mon} to {@code sun}
 */
public record Industry(String code, String name, String station, Shipper shipper, int spots, Set<String> switched) {

    /** The days of the week as a schedule writes them, Monday first. */
    public static final List<String> WEEKDAYS = List.of("mon", "tue", "wed", "thu", "fri", "sat", "sun");

    public Industry {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(shipper, "shipper");
        switched = Set.copyOf(switched);
        if (!Text.isUpper(code) || !Text.isAlnum(code)) {
            throw new ConfigurationError("an industry code is upper case", Details.of("code", code));
        }
        if (spots < 1) {
            throw new ConfigurationError("an industry track holds at least one car", Details.of("code", code));
        }
        Set<String> unknown = new TreeSet<>(switched);
        unknown.removeAll(WEEKDAYS);
        if (!unknown.isEmpty()) {
            throw new ConfigurationError("no such day", Details.of("code", code, "days", List.copyOf(unknown)));
        }
        if (switched.isEmpty()) {
            throw new ConfigurationError("an industry we never switch is closed", Details.of("code", code));
        }
    }

    /** An industry switched on weekdays, which is most of them. */
    public Industry(String code, String name, String station, Shipper shipper, int spots) {
        this(code, name, station, shipper, spots, Set.copyOf(WEEKDAYS.subList(0, 5)));
    }

    /** Whether we switch the industry on a day. */
    public boolean switchedOn(LocalDate day) {
        return switched.contains(WEEKDAYS.get(day.getDayOfWeek().getValue() - 1));
    }

    /** Whether another car has nowhere to go, which makes the next one constructive. */
    public boolean full(int carsOnHand) {
        return carsOnHand >= spots;
    }

    /** {@code ESCPAP1 mill house track at ESC}. */
    @Override
    public String toString() {
        return code + " " + name + " at " + station;
    }
}
