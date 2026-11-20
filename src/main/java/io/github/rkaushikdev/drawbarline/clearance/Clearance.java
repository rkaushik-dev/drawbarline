package io.github.rkaushikdev.drawbarline.clearance;

import io.github.rkaushikdev.drawbarline.measure.Length;
import io.github.rkaushikdev.drawbarline.mileage.Mileage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The governing overhead and side clearance at one place, and the chart of every such place.
 *
 * <p>The clearances are the road's own, off the valuation maps, and they are the governing
 * dimension at each place rather than the nominal one. Bark River carries the low overhead on this
 * railroad and it is why nothing over plate F goes north of Powers. A station with nothing filed
 * reads as open.
 */
public record Clearance(String station, Length overhead, Length side, String what) {

    /**
     * Off the valuation maps. Bark River is the governing overhead on the main and the highway
     * bridge at Wallace is the narrow one.
     */
    public static final Map<String, Clearance> CHART = chart();

    /** What a station with no restriction filed clears. */
    public static final Clearance OPEN = new Clearance("ESC", Length.of(23, 0), Length.of(13, 6), "no restriction");

    public Clearance {
        Objects.requireNonNull(overhead, "overhead");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(what, "what");
        Mileage.milepost(station);
    }

    /** A clearance with nothing said about what governs it. */
    public Clearance(String station, Length overhead, Length side) {
        this(station, overhead, side, "");
    }

    private static Map<String, Clearance> chart() {
        Map<String, Clearance> found = new LinkedHashMap<>();
        found.put("BKR", new Clearance("BKR", Length.of(17, 4), Length.of(12, 6), "highway overpass"));
        found.put("WAL", new Clearance("WAL", Length.of(21, 0), Length.of(11, 2), "highway bridge"));
        found.put("MRN", new Clearance("MRN", Length.of(19, 6), Length.of(12, 0), "river bridge"));
        found.put("IMT", new Clearance("IMT", Length.of(18, 2), Length.of(12, 6), "mine trestle"));
        found.put("GRB", new Clearance("GRB", Length.of(22, 0), Length.of(13, 0), "yard lead"));
        return Collections.unmodifiableMap(found);
    }

    /** Whether a load fits under the overhead and inside the side clearance. */
    public boolean clears(Dimensions load) {
        return load.height().compareTo(overhead) <= 0 && load.width().compareTo(side) <= 0;
    }

    /** {@code BKR (highway overpass) 17'4" over 12'6" wide}. */
    @Override
    public String toString() {
        String where = what.isEmpty() ? "" : " (" + what + ")";
        return station + where + " " + overhead + " over " + side + " wide";
    }

    /** The governing clearance at a station, which is open where nothing is filed. */
    public static Clearance clearanceAt(String station) {
        Mileage.milepost(station);
        Clearance found = CHART.get(station);
        if (found == null) {
            return new Clearance(station, OPEN.overhead, OPEN.side, OPEN.what);
        }
        return found;
    }

    /** The place on a route that governs, which is the lowest overhead. */
    public static Clearance tightestOn(List<String> route) {
        if (route.isEmpty()) {
            throw new ClearanceError("a route has at least one place");
        }
        Clearance found = clearanceAt(route.get(0));
        for (String station : route) {
            Clearance here = clearanceAt(station);
            if (here.overhead.inches() < found.overhead.inches()) {
                found = here;
            }
        }
        return found;
    }

    /** Every place on a route the load will not clear, overhead and side reported separately. */
    public static List<String> checkRoute(Dimensions load, List<String> route) {
        if (route.isEmpty()) {
            throw new ClearanceError("a route has at least one place");
        }
        List<String> found = new ArrayList<>();
        for (String station : route) {
            Clearance where = clearanceAt(station);
            if (load.height().compareTo(where.overhead) > 0) {
                found.add(station + " clears " + where.overhead + " and the load is " + load.height());
            }
            if (load.width().compareTo(where.side) > 0) {
                found.add(station + " clears " + where.side + " wide and the load is " + load.width());
            }
        }
        return List.copyOf(found);
    }

    /** Whether a load clears every place on a route. */
    public static boolean clearsRoute(Dimensions load, List<String> route) {
        return checkRoute(load, route).isEmpty();
    }
}
