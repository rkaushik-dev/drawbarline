package io.github.rkaushikdev.drawbarline.clearance;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.mileage.Mileage;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every clearance the road has filed, each from the day it took effect, and the routes read off it.
 *
 * <p>Clearances change. A bridge is replaced, a track is undercut beneath an overpass, and the
 * governing dimension at a place is different from a date. A load measured against last year's
 * chart is refused at a place it now clears, or worse sent under a place it no longer does, so
 * every reading here is as of a day: the latest filing at each place that had taken effect by then.
 *
 * <p>A chart is never changed in place. Filing a new clearance gives back a new chart, so the one
 * off the valuation maps stays what it was.
 */
public final class ClearanceChart {

    /** The day the valuation maps were drawn, which every clearance on them is in force from. */
    public static final LocalDate VALUATION_DATE = LocalDate.of(1916, 6, 30);

    /** The chart as the valuation maps drew it, every restriction in force from that day. */
    public static final ClearanceChart VALUATION = fromValuationMaps();

    /** One clearance and the day it took effect. */
    private record Filed(LocalDate effective, Clearance clearance) {}

    private final Map<String, List<Filed>> filed;

    private ClearanceChart(Map<String, List<Filed>> filed) {
        this.filed = filed;
    }

    private static ClearanceChart fromValuationMaps() {
        ClearanceChart found = new ClearanceChart(Map.of());
        for (Clearance one : Clearance.CHART.values()) {
            found = found.filing(VALUATION_DATE, one);
        }
        return found;
    }

    /**
     * This chart with one more clearance filed from a day, refusing a second filing at the same
     * place on the same day.
     */
    public ClearanceChart filing(LocalDate effective, Clearance clearance) {
        List<Filed> standing = filed.getOrDefault(clearance.station(), List.of());
        if (standing.stream().anyMatch(one -> one.effective().equals(effective))) {
            throw new ClearanceError(
                    "a clearance is already filed there from that day",
                    Details.of("station", clearance.station(), "effective", effective.toString()));
        }
        List<Filed> grown = new ArrayList<>(standing);
        grown.add(new Filed(effective, clearance));
        grown.sort(Comparator.comparing(Filed::effective));
        Map<String, List<Filed>> next = new LinkedHashMap<>(filed);
        next.put(clearance.station(), List.copyOf(grown));
        return new ClearanceChart(Collections.unmodifiableMap(next));
    }

    /** The clearance filed at a station in force on a day, if one had taken effect. */
    public Optional<Clearance> filedAt(String station, LocalDate on) {
        Clearance found = null;
        for (Filed one : filed.getOrDefault(station, List.of())) {
            if (!one.effective().isAfter(on)) {
                found = one.clearance();
            }
        }
        return Optional.ofNullable(found);
    }

    /** The governing clearance at a station on a day, which is open where nothing is filed. */
    public Clearance at(String station, LocalDate on) {
        Mileage.milepost(station);
        return filedAt(station, on)
                .orElseGet(() -> new Clearance(
                        station, Clearance.OPEN.overhead(), Clearance.OPEN.side(), Clearance.OPEN.what()));
    }

    /** Every restricted place in force on a day, in the order the places were first filed. */
    public Map<String, Clearance> asOf(LocalDate on) {
        Map<String, Clearance> found = new LinkedHashMap<>();
        for (String station : filed.keySet()) {
            filedAt(station, on).ifPresent(one -> found.put(station, one));
        }
        return Collections.unmodifiableMap(found);
    }

    /** The governing clearance at every place on a route on a day, in route order, refusing an empty route. */
    public List<Clearance> along(List<String> route, LocalDate on) {
        if (route.isEmpty()) {
            throw new ClearanceError("a route has at least one place");
        }
        return route.stream().map(station -> at(station, on)).toList();
    }

    /** The place on a route that governs the height of a load on a day, which is the lowest overhead. */
    public Clearance tightestOn(List<String> route, LocalDate on) {
        return governing(along(route, on), Comparator.comparing(Clearance::overhead));
    }

    /**
     * The place on a route that governs the width of a load on a day, which is the narrowest side
     * clearance. It is seldom the place with the low overhead: Bark River is low and Wallace is
     * narrow, and a wide load measured against Bark River alone has been sent into the Wallace
     * bridge before.
     */
    public Clearance narrowestOn(List<String> route, LocalDate on) {
        return governing(along(route, on), Comparator.comparing(Clearance::side));
    }

    /** The first place that is least by a measure, so a tie goes to the place reached first. */
    private static Clearance governing(List<Clearance> places, Comparator<Clearance> least) {
        Clearance found = places.get(0);
        for (Clearance here : places) {
            if (least.compare(here, found) < 0) {
                found = here;
            }
        }
        return found;
    }

    /** Every place on a route the load will not clear on a day, overhead and side reported separately. */
    public List<String> checkRoute(Dimensions load, List<String> route, LocalDate on) {
        return along(route, on).stream()
                .flatMap(place -> place.shortfalls(load).stream())
                .toList();
    }

    /**
     * The places on a route a load clears by less than the margin on a day, in route order: where
     * the train slows to restricted speed and a man rides the car through. A place the load does
     * not clear at all is a refusal for {@link #checkRoute}, not a slow order.
     */
    public List<String> slowOrders(Dimensions load, List<String> route, LocalDate on) {
        return along(route, on).stream()
                .filter(place -> place.close(load))
                .map(place -> place + ", restricted speed")
                .toList();
    }
}
