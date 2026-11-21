package io.github.rkaushikdev.drawbarline.persistence.models;

import io.github.rkaushikdev.drawbarline.fleet.CarTypeCode;
import io.github.rkaushikdev.drawbarline.fleet.MechanicalDesignation;
import io.github.rkaushikdev.drawbarline.limits.Limits;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.measure.Length;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.hibernate.annotations.Check;

/**
 * One equipment record: the stencil and the filing, one row a car, keyed on the marks because that
 * is what every other table and every piece of paper refers to it by. Leases and the retirement
 * hang off it.
 *
 * <p>The lease table carries no constraint against overlapping terms; that rule lives in the domain,
 * is tested there, and is enforced on the way in. What the table does carry is the foreign key, so a
 * lease cannot outlive the car it is against.
 */
@Entity
@Table(
        name = "car",
        indexes = {
            @Index(name = "ix_car_owner", columnList = "owner"),
            @Index(name = "ix_car_type_code", columnList = "type_code")
        })
@Check(name = "ck_car_light_under_gross", constraints = "light_weight < gross_rail_load")
@Check(name = "ck_car_has_length", constraints = "outside_length > 0")
public class CarRow {

    /** The marks as a clerk writes them, {@code DBL 104207}, which is the key every other table uses. */
    @Id
    @Column(name = "mark", length = Limits.CAR_MARK, nullable = false)
    private String mark;

    @Column(name = "owner", length = Limits.REPORTING_MARK, nullable = false)
    private ReportingMark owner;

    @Column(name = "designation", length = Limits.DESIGNATION, nullable = false)
    private MechanicalDesignation designation;

    @Column(name = "type_code", length = Limits.TYPE_CODE, nullable = false)
    private CarTypeCode typeCode;

    @Column(name = "built", nullable = false)
    private LocalDate built;

    @Column(name = "rebuilt")
    private LocalDate rebuilt;

    @Column(name = "light_weight", nullable = false)
    private Weight lightWeight;

    @Column(name = "gross_rail_load", nullable = false)
    private Weight grossRailLoad;

    @Column(name = "outside_length", nullable = false)
    private Length outsideLength;

    @OneToMany(mappedBy = "car", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("began")
    private List<LeaseRow> leases = new ArrayList<>();

    @OneToOne(mappedBy = "car", cascade = CascadeType.ALL, orphanRemoval = true)
    private RetirementRow retirement;

    protected CarRow() {}

    public CarRow(
            CarMark mark,
            ReportingMark owner,
            MechanicalDesignation designation,
            CarTypeCode typeCode,
            LocalDate built,
            LocalDate rebuilt,
            Weight lightWeight,
            Weight grossRailLoad,
            Length outsideLength) {
        this.mark = keyOf(mark);
        this.owner = owner;
        this.designation = designation;
        this.typeCode = typeCode;
        this.built = built;
        this.rebuilt = rebuilt;
        this.lightWeight = lightWeight;
        this.grossRailLoad = grossRailLoad;
        this.outsideLength = outsideLength;
    }

    /** The key a car's row is found by: its marks as a clerk writes them. */
    public static String keyOf(CarMark mark) {
        return mark.toString();
    }

    public CarMark getMark() {
        return CarMark.parse(mark);
    }

    public ReportingMark getOwner() {
        return owner;
    }

    public void setOwner(ReportingMark owner) {
        this.owner = owner;
    }

    public MechanicalDesignation getDesignation() {
        return designation;
    }

    public void setDesignation(MechanicalDesignation designation) {
        this.designation = designation;
    }

    public CarTypeCode getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(CarTypeCode typeCode) {
        this.typeCode = typeCode;
    }

    public LocalDate getBuilt() {
        return built;
    }

    public void setBuilt(LocalDate built) {
        this.built = built;
    }

    public Optional<LocalDate> getRebuilt() {
        return Optional.ofNullable(rebuilt);
    }

    /** The rebuild date, or {@code null} for a car never rebuilt. */
    public void setRebuilt(LocalDate rebuilt) {
        this.rebuilt = rebuilt;
    }

    public Weight getLightWeight() {
        return lightWeight;
    }

    public void setLightWeight(Weight lightWeight) {
        this.lightWeight = lightWeight;
    }

    public Weight getGrossRailLoad() {
        return grossRailLoad;
    }

    public void setGrossRailLoad(Weight grossRailLoad) {
        this.grossRailLoad = grossRailLoad;
    }

    public Length getOutsideLength() {
        return outsideLength;
    }

    public void setOutsideLength(Length outsideLength) {
        this.outsideLength = outsideLength;
    }

    /** The leases on file, in the order they began. */
    public List<LeaseRow> getLeases() {
        return Collections.unmodifiableList(leases);
    }

    /** Put a lease on this car, which is what writes its car mark. */
    public LeaseRow addLease(LeaseRow lease) {
        lease.placeOn(this);
        leases.add(lease);
        return lease;
    }

    /** Take a lease off this car; the row goes with it. */
    public void removeLease(LeaseRow lease) {
        leases.remove(lease);
    }

    public Optional<RetirementRow> getRetirement() {
        return Optional.ofNullable(retirement);
    }

    /** Retire the car, or pass {@code null} to bring it back onto the roster. */
    public void setRetirement(RetirementRow retirement) {
        if (retirement != null) {
            retirement.placeOn(this);
        }
        this.retirement = retirement;
    }

    @Override
    public String toString() {
        return "<CarRow " + mark + ">";
    }
}
