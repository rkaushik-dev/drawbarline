package io.github.rkaushikdev.drawbarline.persistence;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.UnknownCar;
import io.github.rkaushikdev.drawbarline.lading.Commodity;
import io.github.rkaushikdev.drawbarline.lading.Lading;
import io.github.rkaushikdev.drawbarline.lading.Stcc;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.persistence.models.CarRow;
import io.github.rkaushikdev.drawbarline.persistence.models.RouteStopRow;
import io.github.rkaushikdev.drawbarline.persistence.models.WaybillRow;
import io.github.rkaushikdev.drawbarline.waybill.Waybill;
import io.github.rkaushikdev.drawbarline.waybill.WaybillError;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Every waybill ever issued.
 *
 * <p>A waybill is written once and diverted by writing another one, so there is no update here at
 * all. The diversion carries its own number and the first bill stays exactly as it was issued, which
 * is what a claim needs three years later.
 */
public final class WaybillRepository {

    private static final String IN_ORDER = " order by w.dated, w.number";

    private final EntityManager session;

    public WaybillRepository(EntityManager session) {
        this.session = session;
    }

    static Waybill waybillFromRow(WaybillRow row) {
        Optional<Lading> lading = Optional.empty();
        if (row.getLadingStcc().isPresent() && row.getLadingPounds().isPresent()) {
            lading = Optional.of(new Lading(
                    new Commodity(
                            new Stcc(row.getLadingStcc().get()),
                            row.getLadingName().orElse(""),
                            row.isLadingBulk()),
                    row.getLadingPounds().get()));
        }
        return new Waybill(
                row.getNumber(),
                row.getDated(),
                row.getCarMark(),
                row.getShipper(),
                row.getConsignee(),
                row.getStops().stream().map(RouteStopRow::getStation).toList(),
                lading);
    }

    static WaybillRow rowFromWaybill(Waybill bill) {
        Optional<Lading> lading = bill.lading();
        WaybillRow row = new WaybillRow(
                bill.number(),
                bill.dated(),
                bill.car(),
                bill.shipper(),
                bill.consignee(),
                lading.map(one -> one.commodity().code().digits()).orElse(null),
                lading.map(one -> one.commodity().name()).orElse(null),
                lading.map(Lading::weight).orElse(null),
                lading.map(one -> one.commodity().bulk()).orElse(false));
        List<String> route = bill.route();
        for (int at = 0; at < route.size(); at++) {
            row.addStop(new RouteStopRow(at, route.get(at)));
        }
        return row;
    }

    /** Issue a waybill for a car on the register. A number already used is refused. */
    public Waybill issue(Waybill bill) {
        if (session.find(WaybillRow.class, bill.number()) != null) {
            throw new WaybillError("that waybill is already issued", Details.of("number", bill.number()));
        }
        if (session.find(CarRow.class, CarRow.keyOf(bill.car())) == null) {
            throw new UnknownCar(
                    "no record of that car", Details.of("car", bill.car().toString()));
        }
        session.persist(rowFromWaybill(bill));
        session.flush();
        return bill;
    }

    public Waybill get(int number) {
        WaybillRow found = session.find(WaybillRow.class, number);
        if (found == null) {
            throw new WaybillError("no such waybill", Details.of("number", number));
        }
        return waybillFromRow(found);
    }

    /** The waybills one car has moved on, by date and then by number. */
    public List<Waybill> forCar(CarMark car) {
        return mapped(
                session.createQuery("select w from WaybillRow w where w.carMark = :car" + IN_ORDER, WaybillRow.class)
                        .setParameter("car", car));
    }

    /** The waybills dated from one day through another, both included. */
    public List<Waybill> datedBetween(LocalDate first, LocalDate last) {
        return mapped(session.createQuery(
                        "select w from WaybillRow w where w.dated >= :first and w.dated <= :last" + IN_ORDER,
                        WaybillRow.class)
                .setParameter("first", first)
                .setParameter("last", last));
    }

    /** The waybills that carry a lading, by number. */
    public List<Waybill> loadedOnly() {
        return mapped(session.createQuery(
                "select w from WaybillRow w where w.ladingStcc is not null order by w.number", WaybillRow.class));
    }

    /** How many waybills have been issued. */
    public int size() {
        return Counting.countOf(session, WaybillRow.class);
    }

    private static List<Waybill> mapped(TypedQuery<WaybillRow> query) {
        return query.getResultList().stream()
                .map(WaybillRepository::waybillFromRow)
                .toList();
    }
}
