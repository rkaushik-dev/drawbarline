package io.github.rkaushikdev.drawbarline.fleet;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.DuplicateCar;
import io.github.rkaushikdev.drawbarline.RegistryError;
import io.github.rkaushikdev.drawbarline.UnknownCar;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Every car the road has a record for, whether it is still in service, and who is owed for it.
 *
 * <p>Cars go in and are retired out. Nothing is ever deleted, because a car retired in March is
 * still on three months of unsettled car hire and the settlement has to be able to look it up. The
 * register holds foreign cars as well as our own: we keep a record of any car we have ever
 * received in interchange, because we cannot bill a car we cannot describe.
 */
public final class FleetRegister implements Iterable<Car> {

    private final Map<CarMark, Car> cars = new TreeMap<>();
    private final Map<CarMark, Ownership> ownership = new HashMap<>();
    private final Map<CarMark, LocalDate> retired = new HashMap<>();

    /** Put a car on the register, owed to its owner until somebody files a lease. */
    public Car add(Car car) {
        if (cars.containsKey(car.mark())) {
            throw new DuplicateCar(
                    "that car is already on the register",
                    Details.of("car", car.mark().toString()));
        }
        cars.put(car.mark(), car);
        ownership.put(car.mark(), new Ownership(car.owner()));
        return car;
    }

    /** File a corrected record, keeping the lease history the car already has. */
    public Car replace(Car car) {
        if (!cars.containsKey(car.mark())) {
            throw new UnknownCar(
                    "no record of that car", Details.of("car", car.mark().toString()));
        }
        cars.put(car.mark(), car);
        return car;
    }

    public Car get(CarMark mark) {
        Car found = cars.get(mark);
        if (found == null) {
            throw new UnknownCar("no record of that car", Details.of("car", mark.toString()));
        }
        return found;
    }

    public boolean has(CarMark mark) {
        return cars.containsKey(mark);
    }

    /** Whether a value is the mark of a car on the register. Anything that is not a mark is not. */
    public boolean contains(Object mark) {
        return mark instanceof CarMark car && cars.containsKey(car);
    }

    public Ownership ownership(CarMark mark) {
        Ownership found = ownership.get(mark);
        if (found == null) {
            throw new UnknownCar("no record of that car", Details.of("car", mark.toString()));
        }
        return found;
    }

    /** Take a car out of service after its last day. */
    public void retire(CarMark mark, LocalDate on) {
        Car car = get(mark);
        if (on.isBefore(car.built())) {
            throw new RegistryError(
                    "a car cannot be retired before it was built",
                    Details.of("car", mark.toString(), "on", on.toString()));
        }
        LocalDate already = retired.get(mark);
        if (already != null) {
            throw new RegistryError(
                    "that car is already retired", Details.of("car", mark.toString(), "on", already.toString()));
        }
        retired.put(mark, on);
    }

    public Optional<LocalDate> retiredOn(CarMark mark) {
        return Optional.ofNullable(retired.get(mark));
    }

    /** Whether the car exists on a day and has not been retired before it. */
    public boolean inServiceOn(CarMark mark, LocalDate day) {
        Car car = get(mark);
        if (day.isBefore(car.built())) {
            return false;
        }
        LocalDate last = retired.get(mark);
        return last == null || !day.isAfter(last);
    }

    /** Every car in service on a day, in mark order. */
    public List<Car> snapshot(LocalDate day) {
        return where(car -> inServiceOn(car.mark(), day));
    }

    public List<Car> byKind(CarKind kind) {
        return where(car -> car.kind() == kind);
    }

    public List<Car> byTypeCode(CarTypeCode code) {
        return where(car -> car.typeCode().equals(code));
    }

    public List<Car> byOwner(ReportingMark owner) {
        return where(car -> car.owner().equals(owner));
    }

    public List<Car> privateCars() {
        return where(Car::isPrivate);
    }

    /** Cars whose fifty years run out before a date, which is the retirement list. */
    public List<Car> expiringBefore(LocalDate day) {
        return where(car -> car.interchangeExpires().isBefore(day));
    }

    /** Cars owed to a road or lessor on a day. */
    public List<Car> heldBy(ReportingMark holder, LocalDate day) {
        return where(car -> ownership(car.mark()).holderOn(day).equals(holder));
    }

    private List<Car> where(Predicate<Car> test) {
        return cars.values().stream().filter(test).toList();
    }

    /** Every car, in mark order. */
    @Override
    public Iterator<Car> iterator() {
        return List.copyOf(cars.values()).iterator();
    }

    public int size() {
        return cars.size();
    }
}
