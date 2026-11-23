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
 * railroad and it is why nothing over plate F goes north of Powers; the Wallace highway bridge is
 * the narrow place, and a route is governed by both, each at its own place. A load that clears a
 * place by less than {@link #CLOSE_MARGIN} still goes, slowly. A station with nothing filed reads
 * as open. The readings here are off the valuation maps; a clearance filed since then is on a
 * {@link ClearanceChart}, which reads every route as of a day.
 */
public record Clearance(String station, Length overhead, Length side, String what) {

    /**
     * Off the valuation maps. Bark River is the governing overhead on the main and the highway
     * bridge at Wallace is the narrow one. Later filings are on a {@link ClearanceChart}.
     */
    public static final Map<String, Clearance> CHART = chart();

    /** How much room a load needs to spare, on top and at the side, to move at track speed. */
    public static final Length CLOSE_MARGIN = new Length(3);

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
        return load.within(overhead, side);
    }

    /** Every way a load does not fit here, the overhead before the side. */
    public List<String> shortfalls(Dimensions load) {
        List<String> found = new ArrayList<>();
        if (load.height().compareTo(overhead) > 0) {
            found.add(station + " clears " + overhead + " and the load is " + load.height());
        }
        if (load.width().compareTo(side) > 0) {
            found.add(station + " clears " + side + " wide and the load is " + load.width());
        }
        return List.copyOf(found);
    }

    /**
     * Whether a load that fits here fits by less than {@link #CLOSE_MARGIN} on top or at the side.
     * A load that fits by an inch is not a load that will fit if it shifted in transit, so it goes
     * through at restricted speed with somebody watching.
     */
    public boolean close(Dimensions load) {
        return clears(load) && !load.within(overhead.minus(CLOSE_MARGIN), side.minus(CLOSE_MARGIN));
    }

    /** {@code BKR (highway overpass) 17'4" over 12'6" wide}. */
    @Override
    public String toString() {
        String where = what.isEmpty() ? "" : " (" + what + ")";
        return station + where + " " + overhead + " over " + side + " wide";
    }

    /** The governing clearance at a station on the valuation maps, which is open where nothing is filed. */
    public static Clearance clearanceAt(String station) {
        return ClearanceChart.VALUATION.at(station, ClearanceChart.VALUATION_DATE);
    }

    /** The governing clearance at every place on a route on the valuation maps, in route order. */
    public static List<Clearance> along(List<String> route) {
        return ClearanceChart.VALUATION.along(route, ClearanceChart.VALUATION_DATE);
    }

    /** The place on a route that governs the height of a load, which is the lowest overhead. */
    public static Clearance tightestOn(List<String> route) {
        return ClearanceChart.VALUATION.tightestOn(route, ClearanceChart.VALUATION_DATE);
    }

    /** The place on a route that governs the width of a load, which is the narrowest side clearance. */
    public static Clearance narrowestOn(List<String> route) {
        return ClearanceChart.VALUATION.narrowestOn(route, ClearanceChart.VALUATION_DATE);
    }

    /** Every place on a route the load will not clear, overhead and side reported separately. */
    public static List<String> checkRoute(Dimensions load, List<String> route) {
        return ClearanceChart.VALUATION.checkRoute(load, route, ClearanceChart.VALUATION_DATE);
    }

    /** The places on a route a load clears by less than the margin: where the train slows. */
    public static List<String> slowOrders(Dimensions load, List<String> route) {
        return ClearanceChart.VALUATION.slowOrders(load, route, ClearanceChart.VALUATION_DATE);
    }

    /** Whether a load clears every place on a route. */
    public static boolean clearsRoute(Dimensions load, List<String> route) {
        return checkRoute(load, route).isEmpty();
    }
}
