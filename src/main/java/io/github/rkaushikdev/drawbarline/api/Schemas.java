package io.github.rkaushikdev.drawbarline.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.rkaushikdev.drawbarline.fleet.Car;
import io.github.rkaushikdev.drawbarline.fleet.Lease;
import io.github.rkaushikdev.drawbarline.fleet.Ownership;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * What goes over the wire for the fleet.
 *
 * <p>The domain objects are records holding value objects, and none of that serialises usefully.
 * So there is a shape a resource, and every one of them is written as plain scalars: pounds as
 * whole numbers, inches as whole numbers, money as cents. A client that wants dollars formats them,
 * because a number that arrives as a string with a currency symbol on it has been rounded by
 * somebody and there is no way to tell who.
 *
 * <p>Derived fields go out on the way back and are never accepted on the way in. A load limit
 * posted by a client is a load limit somebody can disagree with the car about.
 */
final class Schemas {

    private Schemas() {}

    /** An equipment record as it is filed. */
    record CarIn(
            @NotNull @Schema(description = "Reporting mark and number, such as DBL 104207.") String mark,
            @NotNull @Schema(description = "Mechanical designation, such as LO.") String designation,
            @NotNull @Schema(description = "AAR car type code, such as L351.") String typeCode,
            @NotNull LocalDate built,
            @NotNull @Schema(description = "Pounds.") Long lightWeight,
            @NotNull @Schema(description = "Pounds.") Long grossRailLoad,
            @NotNull @Schema(description = "Inches over the pulling faces.") Integer outsideLength,
            LocalDate rebuilt) {}

    /** An equipment record as it reads back. */
    record CarOut(
            String mark,
            String stencil,
            String owner,
            String designation,
            String typeCode,
            String kind,
            LocalDate built,
            Optional<LocalDate> rebuilt,
            long lightWeight,
            long grossRailLoad,
            long loadLimit,
            int outsideLength,
            @JsonProperty("private") boolean isPrivate,
            LocalDate interchangeExpires) {

        static CarOut of(Car car) {
            return new CarOut(
                    car.mark().toString(),
                    car.mark().stencilled(),
                    car.owner().toString(),
                    car.designation().toString(),
                    car.typeCode().toString(),
                    car.kind().description(),
                    car.built(),
                    car.rebuilt(),
                    car.lightWeight().pounds(),
                    car.grossRailLoad().pounds(),
                    car.loadLimit().pounds(),
                    car.outsideLength().inches(),
                    car.isPrivate(),
                    car.interchangeExpires());
        }
    }

    /** A term somebody other than the owner collects for. */
    record LeaseIn(@NotNull String lessee, @NotNull LocalDate began, LocalDate ended) {}

    /** One lease term as it reads back. */
    record LeaseOut(String lessee, LocalDate began, Optional<LocalDate> ended) {

        static LeaseOut of(Lease term) {
            return new LeaseOut(term.lessee().toString(), term.began(), term.ended());
        }
    }

    /** Who is owed for a car, and every term on file. */
    record OwnershipOut(String owner, List<LeaseOut> terms) {

        static OwnershipOut of(Ownership ownership) {
            return new OwnershipOut(
                    ownership.owner().toString(),
                    ownership.terms().stream().map(LeaseOut::of).toList());
        }
    }

    /** Taking a car off the roster. */
    record RetirementIn(@NotNull LocalDate on, String reason) {

        RetirementIn {
            reason = reason == null ? "" : reason;
        }
    }

    /** A car taken off the roster, and the day it went. */
    record Retired(String mark, Optional<LocalDate> on) {}
}
