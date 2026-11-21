package io.github.rkaushikdev.drawbarline.persistence;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.DuplicateCar;
import io.github.rkaushikdev.drawbarline.RegistryError;
import io.github.rkaushikdev.drawbarline.UnknownCar;
import io.github.rkaushikdev.drawbarline.fleet.Car;
import io.github.rkaushikdev.drawbarline.fleet.CarKind;
import io.github.rkaushikdev.drawbarline.fleet.FleetRegister;
import io.github.rkaushikdev.drawbarline.fleet.Lease;
import io.github.rkaushikdev.drawbarline.fleet.Ownership;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.persistence.models.CarRow;
import io.github.rkaushikdev.drawbarline.persistence.models.LeaseRow;
import io.github.rkaushikdev.drawbarline.persistence.models.RetirementRow;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Every car we have a record for, on the other side of a session.
 *
 * <p>Same shape as the register in memory and the same refusals, so a caller that works against
 * one works against the other. The reports and the close are written against the register, and
 * being able to hand them either one built in a test or one read from the books is what makes them
 * testable at all.
 *
 * <p>Nothing here caches. A repository that keeps its own copy of a car is a second source of
 * truth, and the session already keeps one instance a row.
 */
public final class FleetRepository {

    /** Every car with its retirement, which comes in the same read as the car. */
    private static final String EVERY_CAR = "select c from CarRow c left join fetch c.retirement";

    private final EntityManager session;

    public FleetRepository(EntityManager session) {
        this.session = session;
    }

    private CarRow row(CarMark mark) {
        CarRow found = session.find(CarRow.class, CarRow.keyOf(mark));
        if (found == null) {
            throw new UnknownCar("no record of that car", Details.of("car", mark.toString()));
        }
        return found;
    }

    /** File a car. One already on the register is refused. */
    public Car add(Car car) {
        if (has(car.mark())) {
            throw new DuplicateCar(
                    "that car is already on the register",
                    Details.of("car", car.mark().toString()));
        }
        session.persist(FleetMappers.rowFromCar(car));
        session.flush();
        return car;
    }

    /** Write a corrected record over a car already on the register. */
    public Car replace(Car car) {
        FleetMappers.updateRowFromCar(row(car.mark()), car);
        session.flush();
        return car;
    }

    public Car get(CarMark mark) {
        return FleetMappers.carFromRow(row(mark));
    }

    public boolean has(CarMark mark) {
        return session.find(CarRow.class, CarRow.keyOf(mark)) != null;
    }

    /** Who the car is owed to, term by term, replayed from its lease rows. */
    public Ownership ownership(CarMark mark) {
        CarRow found = row(mark);
        return FleetMappers.ownershipFromRows(found.getOwner(), found.getLeases());
    }

    /** Replace the car's leases with the terms of an ownership that belongs to it. */
    public Ownership saveOwnership(CarMark mark, Ownership ownership) {
        CarRow found = row(mark);
        if (!ownership.owner().equals(found.getOwner())) {
            throw new RegistryError(
                    "that ownership belongs to another car",
                    Details.of(
                            "car", mark.toString(), "owner", ownership.owner().toString()));
        }
        for (LeaseRow standing : List.copyOf(found.getLeases())) {
            found.removeLease(standing);
        }
        session.flush();
        for (LeaseRow term : FleetMappers.leaseRowsFor(mark, ownership)) {
            found.addLease(term);
        }
        session.flush();
        return ownership;
    }

    /** Take a car off the roster after a day, with no reason given. */
    public void retire(CarMark mark, LocalDate on) {
        retire(mark, on, "");
    }

    /** Take a car off the roster after a day. A car is retired once, and not before it was built. */
    public void retire(CarMark mark, LocalDate on, String reason) {
        CarRow found = row(mark);
        if (on.isBefore(found.getBuilt())) {
            throw new RegistryError(
                    "a car cannot be retired before it was built",
                    Details.of("car", mark.toString(), "on", on.toString()));
        }
        Optional<RetirementRow> already = found.getRetirement();
        if (already.isPresent()) {
            throw new RegistryError(
                    "that car is already retired",
                    Details.of(
                            "car", mark.toString(), "on", already.get().getOn().toString()));
        }
        found.setRetirement(new RetirementRow(on, reason));
        session.flush();
    }

    /** The day the car came off the roster, if it has. */
    public Optional<LocalDate> retiredOn(CarMark mark) {
        return row(mark).getRetirement().map(RetirementRow::getOn);
    }

    /** Every car, in mark order. */
    public List<Car> all() {
        return rows().stream().map(FleetMappers::carFromRow).toList();
    }

    /** Every car one owner holds the marks of, in mark order. */
    public List<Car> byOwner(ReportingMark owner) {
        return session
                .createQuery(EVERY_CAR + " where c.owner = :owner order by c.mark", CarRow.class)
                .setParameter("owner", owner)
                .getResultList()
                .stream()
                .map(FleetMappers::carFromRow)
                .toList();
    }

    /** Every car of a kind, in mark order. */
    public List<Car> byKind(CarKind kind) {
        return all().stream().filter(car -> car.kind() == kind).toList();
    }

    /** Build the register in memory that the reports and the close work against. */
    public FleetRegister register() {
        FleetRegister register = new FleetRegister();
        for (CarRow found : rows()) {
            Car car = FleetMappers.carFromRow(found);
            register.add(car);
            Ownership held = register.ownership(car.mark());
            for (Lease term : FleetMappers.ownershipFromRows(found.getOwner(), found.getLeases())
                    .terms()) {
                held.lease(term);
            }
            found.getRetirement().ifPresent(gone -> register.retire(car.mark(), gone.getOn()));
        }
        return register;
    }

    /** How many cars are on the register. */
    public int size() {
        return Counting.countOf(session, CarRow.class);
    }

    private List<CarRow> rows() {
        return session.createQuery(EVERY_CAR + " order by c.mark", CarRow.class).getResultList();
    }
}
