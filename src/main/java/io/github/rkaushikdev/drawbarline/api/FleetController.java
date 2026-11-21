package io.github.rkaushikdev.drawbarline.api;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.RegistryError;
import io.github.rkaushikdev.drawbarline.api.Schemas.CarIn;
import io.github.rkaushikdev.drawbarline.api.Schemas.CarOut;
import io.github.rkaushikdev.drawbarline.api.Schemas.LeaseIn;
import io.github.rkaushikdev.drawbarline.api.Schemas.OwnershipOut;
import io.github.rkaushikdev.drawbarline.api.Schemas.Retired;
import io.github.rkaushikdev.drawbarline.api.Schemas.RetirementIn;
import io.github.rkaushikdev.drawbarline.fleet.Car;
import io.github.rkaushikdev.drawbarline.fleet.CarTypeCode;
import io.github.rkaushikdev.drawbarline.fleet.Lease;
import io.github.rkaushikdev.drawbarline.fleet.MechanicalDesignation;
import io.github.rkaushikdev.drawbarline.fleet.Ownership;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.measure.Length;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fleet routes.
 *
 * <p>A car is addressed by its marks, which contain a space, so the path segment is whatever the
 * client sends and the parsing is done by the same reader the wheel report goes through. That means
 * {@code DBL%20104207} and {@code DBL104207} both reach the same car, which is what anybody typing
 * one into a browser expects.
 */
@RestController
@RequestMapping("/cars")
@Tag(name = "fleet")
final class FleetController {

    private final Work work;

    FleetController(Work work) {
        this.work = work;
    }

    static Car carOf(CarIn filing) {
        return new Car(
                CarMark.parse(filing.mark()),
                MechanicalDesignation.parse(filing.designation()),
                CarTypeCode.parse(filing.typeCode()),
                filing.built(),
                new Weight(filing.lightWeight()),
                new Weight(filing.grossRailLoad()),
                new Length(filing.outsideLength()),
                Optional.ofNullable(filing.rebuilt()));
    }

    /** File an equipment record. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CarOut fileCar(@Valid @RequestBody CarIn filing) {
        return work.apply(books -> CarOut.of(books.fleet().add(carOf(filing))));
    }

    /** Every car on the register, in mark order, or every car one road owns. */
    @GetMapping
    List<CarOut> listCars(@RequestParam(name = "owner", required = false) String owner) {
        return work.read(books -> {
            List<Car> cars = owner == null || owner.isEmpty()
                    ? books.fleet().all()
                    : books.fleet().byOwner(ReportingMark.parse(owner));
            return cars.stream().map(CarOut::of).toList();
        });
    }

    /** One car. */
    @GetMapping("/{mark}")
    CarOut showCar(@PathVariable("mark") String mark) {
        return work.read(books -> CarOut.of(books.fleet().get(CarMark.parse(mark))));
    }

    /** File a corrected record, keeping the lease history. */
    @PutMapping("/{mark}")
    CarOut correctCar(@PathVariable("mark") String mark, @Valid @RequestBody CarIn filing) {
        return work.apply(books -> {
            Car car = carOf(filing);
            if (!car.mark().equals(CarMark.parse(mark))) {
                throw new RegistryError(
                        "a correction does not change which car it is",
                        Details.of("path", mark, "filed", car.mark().toString()));
            }
            return CarOut.of(books.fleet().replace(car));
        });
    }

    /** Who is owed for a car. */
    @GetMapping("/{mark}/ownership")
    OwnershipOut showOwnership(@PathVariable("mark") String mark) {
        return work.read(books -> OwnershipOut.of(books.fleet().ownership(CarMark.parse(mark))));
    }

    /** File a lease term against a car. */
    @PostMapping("/{mark}/leases")
    @ResponseStatus(HttpStatus.CREATED)
    OwnershipOut addLease(@PathVariable("mark") String mark, @Valid @RequestBody LeaseIn filing) {
        return work.apply(books -> {
            CarMark car = CarMark.parse(mark);
            Ownership ownership = books.fleet().ownership(car);
            ownership.lease(new Lease(
                    ReportingMark.parse(filing.lessee()), filing.began(), Optional.ofNullable(filing.ended())));
            return OwnershipOut.of(books.fleet().saveOwnership(car, ownership));
        });
    }

    /** Take a car off the roster. */
    @PostMapping("/{mark}/retirement")
    @ResponseStatus(HttpStatus.CREATED)
    Retired retireCar(@PathVariable("mark") String mark, @Valid @RequestBody RetirementIn filing) {
        return work.apply(books -> {
            CarMark car = CarMark.parse(mark);
            books.fleet().retire(car, filing.on(), filing.reason());
            return new Retired(car.toString(), books.fleet().retiredOn(car));
        });
    }
}
