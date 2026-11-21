package io.github.rkaushikdev.drawbarline.persistence;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.distribution.CarOrder;
import io.github.rkaushikdev.drawbarline.distribution.DistributionError;
import io.github.rkaushikdev.drawbarline.fleet.CarKind;
import io.github.rkaushikdev.drawbarline.persistence.models.CarOrderRow;
import io.github.rkaushikdev.drawbarline.persistence.models.IndustryRow;
import io.github.rkaushikdev.drawbarline.persistence.models.ShipperRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every car order, open or not.
 *
 * <p>Open orders are the ones the distribution runs against, and an order is open until it is
 * cancelled or until the day it was wanted for has gone by. The second half matters: an order for
 * last Tuesday is not something to fill this morning, and leaving it open would let the oldest first
 * rule keep handing cars to an order nobody is waiting on.
 */
public final class OrderRepository {

    private final EntityManager session;

    public OrderRepository(EntityManager session) {
        this.session = session;
    }

    static CarOrder orderFromRow(CarOrderRow row) {
        return new CarOrder(
                row.getNumber(),
                row.getShipperCode(),
                row.getIndustryCode(),
                CarKind.fromLetter(row.getKind()),
                row.getCars(),
                row.getWantedOn());
    }

    private CarOrderRow row(int number) {
        CarOrderRow found = session.find(CarOrderRow.class, number);
        if (found == null) {
            throw new DistributionError("no such order", Details.of("number", number));
        }
        return found;
    }

    /**
     * Place an order on a day, for a shipper and a track on the register. An order placed after the
     * day its cars are wanted is refused.
     */
    public CarOrder place(CarOrder order, LocalDate on) {
        if (session.find(CarOrderRow.class, order.number()) != null) {
            throw new DistributionError("that order is already placed", Details.of("number", order.number()));
        }
        if (session.find(ShipperRow.class, order.shipper()) == null) {
            throw new DistributionError("no such shipper", Details.of("shipper", order.shipper()));
        }
        if (session.find(IndustryRow.class, order.industry()) == null) {
            throw new DistributionError("no such industry", Details.of("industry", order.industry()));
        }
        if (on.isAfter(order.wantedOn())) {
            throw new DistributionError(
                    "an order is placed before the cars are wanted",
                    Details.of(
                            "number", order.number(),
                            "placed", on.toString(),
                            "wanted", order.wantedOn().toString()));
        }
        session.persist(new CarOrderRow(
                order.number(),
                order.shipper(),
                order.industry(),
                order.kind().letter(),
                order.cars(),
                order.wantedOn(),
                on));
        session.flush();
        return order;
    }

    public CarOrder get(int number) {
        return orderFromRow(row(number));
    }

    /** Cancel an order on a day. An order is cancelled once, and not before it was placed. */
    public CarOrder cancel(int number, LocalDate on) {
        CarOrderRow found = row(number);
        if (found.getCancelledOn().isPresent()) {
            throw new DistributionError("that order is already cancelled", Details.of("number", number));
        }
        if (on.isBefore(found.getPlacedOn())) {
            throw new DistributionError(
                    "an order cannot be cancelled before it was placed", Details.of("number", number));
        }
        found.setCancelledOn(on);
        session.flush();
        return orderFromRow(found);
    }

    /** The day an order was cancelled, if it was. */
    public Optional<LocalDate> cancelledOn(int number) {
        return row(number).getCancelledOn();
    }

    /** Orders still wanting cars on a day, oldest first. */
    public List<CarOrder> openOn(LocalDate day) {
        return session
                .createQuery(
                        "select o from CarOrderRow o where o.cancelledOn is null and o.wantedOn >= :day"
                                + " and o.placedOn <= :day order by o.wantedOn, o.number",
                        CarOrderRow.class)
                .setParameter("day", day)
                .getResultList()
                .stream()
                .map(OrderRepository::orderFromRow)
                .toList();
    }

    /** Every order one shipper placed, by number. */
    public List<CarOrder> forShipper(String shipper) {
        return session
                .createQuery(
                        "select o from CarOrderRow o where o.shipperCode = :shipper order by o.number",
                        CarOrderRow.class)
                .setParameter("shipper", shipper)
                .getResultList()
                .stream()
                .map(OrderRepository::orderFromRow)
                .toList();
    }

    /** Which station each industry track is at, by track, which the distribution needs. */
    public Map<String, String> stations() {
        Map<String, String> found = new LinkedHashMap<>();
        for (Tuple row : session.createQuery(
                        "select i.code as code, i.station as station from IndustryRow i order by i.code", Tuple.class)
                .getResultList()) {
            found.put(row.get("code", String.class), row.get("station", String.class));
        }
        return Collections.unmodifiableMap(found);
    }

    /** How many orders have been placed. */
    public int size() {
        return Counting.countOf(session, CarOrderRow.class);
    }
}
