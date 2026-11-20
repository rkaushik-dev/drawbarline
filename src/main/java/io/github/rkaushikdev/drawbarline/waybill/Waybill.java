package io.github.rkaushikdev.drawbarline.waybill;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.lading.Lading;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.mileage.Mileage;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One movement of one car, as the billing copy of the waybill carries it.
 *
 * <p>A waybill says which car, whose freight, from where to where, by what route, and what is in it.
 * The route is not advisory: it is the sequence of our stations the car is authorised over, and the
 * chargeable mileage is measured along it, so a car that went a different way needs a corrected
 * waybill rather than a corrected mileage figure. An empty car moves on a waybill too, with no
 * lading on it: a lading of nothing is not a thing, but an empty movement very much is.
 */
public record Waybill(
        int number,
        LocalDate dated,
        CarMark car,
        String shipper,
        String consignee,
        List<String> route,
        Optional<Lading> lading) {

    public Waybill {
        Objects.requireNonNull(dated, "dated");
        Objects.requireNonNull(car, "car");
        Objects.requireNonNull(shipper, "shipper");
        Objects.requireNonNull(consignee, "consignee");
        Objects.requireNonNull(lading, "lading");
        route = List.copyOf(route);
        if (number < 1) {
            throw new WaybillError("a waybill is numbered from one", Details.of("number", number));
        }
        if (route.size() < 2) {
            throw new WaybillError(
                    "a route runs from somewhere to somewhere else", Details.of("number", number, "route", route));
        }
        for (String station : route) {
            Mileage.milepost(station);
        }
        for (int at = 1; at < route.size(); at++) {
            if (route.get(at - 1).equals(route.get(at))) {
                throw new WaybillError(
                        "a route does not stop at the same place twice running",
                        Details.of("number", number, "station", route.get(at - 1)));
            }
        }
        if (Text.isBlank(shipper) || Text.isBlank(consignee)) {
            throw new WaybillError("a waybill names a shipper and a consignee", Details.of("number", number));
        }
    }

    /** A loaded movement. */
    public Waybill(
            int number,
            LocalDate dated,
            CarMark car,
            String shipper,
            String consignee,
            List<String> route,
            Lading lading) {
        this(number, dated, car, shipper, consignee, route, Optional.of(lading));
    }

    /** An empty movement. */
    public Waybill(int number, LocalDate dated, CarMark car, String shipper, String consignee, List<String> route) {
        this(number, dated, car, shipper, consignee, route, Optional.empty());
    }

    public String origin() {
        return route.get(0);
    }

    public String destination() {
        return route.get(route.size() - 1);
    }

    public boolean loaded() {
        return lading.isPresent();
    }

    public boolean hazardous() {
        return lading.map(Lading::hazardous).orElse(false);
    }

    /** Chargeable tenths of a mile along the authorised route. */
    public int tenths() {
        return Mileage.routeTenths(route);
    }

    /** Chargeable miles along the authorised route, for a report. */
    public double miles() {
        return tenths() / (double) Mileage.TENTHS_PER_MILE;
    }

    /** Whether the route runs through a station. */
    public boolean passes(String station) {
        return route.contains(station);
    }

    /** Reconsign the car at a station it is routed through after its origin, under a new number. */
    public Waybill divert(String at, String to, int number) {
        int cut = route.indexOf(at);
        if (cut < 0) {
            throw new WaybillError("the car is not routed through there", Details.of("at", at, "number", this.number));
        }
        if (to.equals(at)) {
            throw new WaybillError("a diversion goes somewhere else", Details.of("at", at, "number", this.number));
        }
        if (cut == 0) {
            throw new WaybillError("a diversion happens after the origin", Details.of("at", at, "number", this.number));
        }
        List<String> rerouted = new ArrayList<>(route.subList(0, cut + 1));
        rerouted.add(to);
        return new Waybill(number, dated, car, shipper, consignee, rerouted, lading);
    }

    /** {@code wb 41207 DBL 104207 PWR to ESC wheat}, or {@code empty} for an empty movement. */
    @Override
    public String toString() {
        String what = lading.map(one -> one.commodity().name()).orElse("empty");
        return "wb " + number + " " + car + " " + origin() + " to " + destination() + " " + what;
    }
}
