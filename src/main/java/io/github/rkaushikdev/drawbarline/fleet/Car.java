package io.github.rkaushikdev.drawbarline.fleet;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.measure.Length;
import io.github.rkaushikdev.drawbarline.weight.NominalCapacity;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * The equipment record for one car: everything the road knows about a piece of rolling stock
 * that does not change when the car moves.
 *
 * <p>Two rules refuse cars rather than describe them. A car whose light weight is not under its
 * gross rail load cannot carry anything, which means somebody typed a number into the wrong box.
 * And a car may not go into interchange service more than fifty years after it was built, or after
 * it was rebuilt if it was, which is the rule that quietly retires a fleet.
 */
public record Car(
        CarMark mark,
        MechanicalDesignation designation,
        CarTypeCode typeCode,
        LocalDate built,
        Weight lightWeight,
        Weight grossRailLoad,
        Length outsideLength,
        Optional<LocalDate> rebuilt) {

    /** How long a car may stay in interchange service after it was built or rebuilt. */
    public static final int INTERCHANGE_LIFE_YEARS = 50;

    public Car {
        Objects.requireNonNull(mark, "mark");
        Objects.requireNonNull(designation, "designation");
        Objects.requireNonNull(typeCode, "typeCode");
        Objects.requireNonNull(built, "built");
        Objects.requireNonNull(lightWeight, "lightWeight");
        Objects.requireNonNull(grossRailLoad, "grossRailLoad");
        Objects.requireNonNull(outsideLength, "outsideLength");
        Objects.requireNonNull(rebuilt, "rebuilt");
        if (!Aar.agrees(designation, typeCode)) {
            throw new CarRecordError(
                    "the designation and the type code name different kinds of car",
                    Details.of(
                            "car", mark.toString(),
                            "designation", designation.toString(),
                            "type_code", typeCode.toString()));
        }
        if (rebuilt.isPresent() && rebuilt.get().isBefore(built)) {
            throw new CarRecordError(
                    "a car cannot be rebuilt before it was built",
                    Details.of(
                            "car", mark.toString(),
                            "built", built.toString(),
                            "rebuilt", rebuilt.get().toString()));
        }
        // This refuses on its own account when the light weight is not under the gross rail
        // load, which is the check and the message wanted here.
        Weight.loadLimit(grossRailLoad, lightWeight);
    }

    /** A car that has never been rebuilt. */
    public Car(
            CarMark mark,
            MechanicalDesignation designation,
            CarTypeCode typeCode,
            LocalDate built,
            Weight lightWeight,
            Weight grossRailLoad,
            Length outsideLength) {
        this(mark, designation, typeCode, built, lightWeight, grossRailLoad, outsideLength, Optional.empty());
    }

    /** A car rebuilt on a day. */
    public Car(
            CarMark mark,
            MechanicalDesignation designation,
            CarTypeCode typeCode,
            LocalDate built,
            Weight lightWeight,
            Weight grossRailLoad,
            Length outsideLength,
            LocalDate rebuilt) {
        this(mark, designation, typeCode, built, lightWeight, grossRailLoad, outsideLength, Optional.of(rebuilt));
    }

    public CarKind kind() {
        return designation.kind();
    }

    /** The road or company whose mark the car carries. */
    public ReportingMark owner() {
        return mark.mark();
    }

    /** A private car earns rent for a lessor, not car hire for a road. */
    public boolean isPrivate() {
        return mark.isPrivate();
    }

    /** What the car may carry: the gross rail load less its light weight. */
    public Weight loadLimit() {
        return Weight.loadLimit(grossRailLoad, lightWeight);
    }

    public NominalCapacity capacity() {
        return Weight.capacityFor(grossRailLoad);
    }

    /** The date the fifty year clock runs from, which a rebuild resets. */
    public LocalDate datesFrom() {
        return rebuilt.orElse(built);
    }

    /** The first day the car may no longer be in interchange service. A leap day falls back to the 28th. */
    public LocalDate interchangeExpires() {
        return datesFrom().plusYears(INTERCHANGE_LIFE_YEARS);
    }

    /** Whole years since the car was built, which is what a rate table asks. */
    public int ageOn(LocalDate day) {
        if (day.isBefore(built)) {
            throw new CarRecordError(
                    "that date is before the car existed", Details.of("car", mark.toString(), "day", day.toString()));
        }
        int years = day.getYear() - built.getYear();
        if (day.getMonthValue() < built.getMonthValue()
                || (day.getMonthValue() == built.getMonthValue() && day.getDayOfMonth() < built.getDayOfMonth())) {
            years -= 1;
        }
        return years;
    }

    /** Whether the car may be in interchange service on a day. */
    public boolean eligibleOn(LocalDate day) {
        return !day.isBefore(built) && day.isBefore(interchangeExpires());
    }

    /** The marks and the designation: {@code DBL 104207 LO}. */
    @Override
    public String toString() {
        return mark + " " + designation;
    }
}
