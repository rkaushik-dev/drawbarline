package io.github.rkaushikdev.drawbarline.persistence;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.RegistryError;
import io.github.rkaushikdev.drawbarline.fleet.Car;
import io.github.rkaushikdev.drawbarline.fleet.Lease;
import io.github.rkaushikdev.drawbarline.fleet.Ownership;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.persistence.models.CarRow;
import io.github.rkaushikdev.drawbarline.persistence.models.LeaseRow;
import java.util.Comparator;
import java.util.List;

/**
 * Equipment records, in both directions.
 *
 * <p>Both directions live together on purpose: the test that matters is the round trip, an object
 * stored and read back equal to what went in, and keeping the two halves side by side makes that
 * test obvious to write and to keep passing.
 *
 * <p>A car row and a car carry the same fields, so the mapping is dull, which is what a mapping
 * should be. The one decision is that the owner is stored as its own column even though it can be
 * read off the marks, because every query that matters filters on it.
 *
 * <p>Ownership is the interesting half. The domain holds it as a small history with the rule that
 * terms do not overlap; the table holds one row a term. Reading it back replays the terms through
 * the same object that enforces the rule, so a database somebody edited by hand fails at the read
 * rather than producing a car owed to two parties at once.
 */
public final class FleetMappers {

    private FleetMappers() {}

    public static Car carFromRow(CarRow row) {
        return new Car(
                row.getMark(),
                row.getDesignation(),
                row.getTypeCode(),
                row.getBuilt(),
                row.getLightWeight(),
                row.getGrossRailLoad(),
                row.getOutsideLength(),
                row.getRebuilt());
    }

    public static CarRow rowFromCar(Car car) {
        return new CarRow(
                car.mark(),
                car.owner(),
                car.designation(),
                car.typeCode(),
                car.built(),
                car.rebuilt().orElse(null),
                car.lightWeight(),
                car.grossRailLoad(),
                car.outsideLength());
    }

    /** Write a corrected record onto the row already in the session. */
    public static CarRow updateRowFromCar(CarRow row, Car car) {
        if (!row.getMark().equals(car.mark())) {
            throw new RegistryError(
                    "a correction does not change which car it is",
                    Details.of(
                            "row", row.getMark().toString(), "car", car.mark().toString()));
        }
        row.setOwner(car.owner());
        row.setDesignation(car.designation());
        row.setTypeCode(car.typeCode());
        row.setBuilt(car.built());
        row.setRebuilt(car.rebuilt().orElse(null));
        row.setLightWeight(car.lightWeight());
        row.setGrossRailLoad(car.grossRailLoad());
        row.setOutsideLength(car.outsideLength());
        return row;
    }

    /** Replay lease rows, in the order they began, through the object that holds the no overlap rule. */
    public static Ownership ownershipFromRows(ReportingMark owner, List<LeaseRow> rows) {
        Ownership ownership = new Ownership(owner);
        rows.stream()
                .sorted(Comparator.comparing(LeaseRow::getBegan))
                .forEach(row -> ownership.lease(new Lease(row.getLessee(), row.getBegan(), row.getEnded())));
        return ownership;
    }

    /** One row a term, against the car named. */
    public static List<LeaseRow> leaseRowsFor(CarMark car, Ownership ownership) {
        return ownership.terms().stream()
                .map(term -> new LeaseRow(
                        car, term.lessee(), term.began(), term.ended().orElse(null)))
                .toList();
    }
}
