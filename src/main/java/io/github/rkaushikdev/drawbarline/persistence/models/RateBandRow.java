package io.github.rkaushikdev.drawbarline.persistence.models;

import io.github.rkaushikdev.drawbarline.fleet.CarTypeCode;
import io.github.rkaushikdev.drawbarline.limits.Limits;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import org.hibernate.annotations.Check;

/** One line of the car hire schedule: a type of car, a band of ages, a date and the two rates. */
@Entity
@Table(
        name = "car_hire_band",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_band_not_filed_twice",
                        columnNames = {"type_code", "from_age", "effective"}),
        indexes = @Index(name = "ix_band_lookup", columnList = "type_code, effective"))
@Check(name = "ck_band_age_from_zero", constraints = "from_age >= 0")
@Check(name = "ck_band_age_climbs", constraints = "to_age >= from_age")
@Check(name = "ck_band_hourly_not_negative", constraints = "hourly_tenths >= 0")
@Check(name = "ck_band_mileage_not_negative", constraints = "mileage_mills >= 0")
public class RateBandRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "type_code", length = Limits.TYPE_CODE, nullable = false)
    private CarTypeCode typeCode;

    @Column(name = "from_age", nullable = false)
    private int fromAge;

    @Column(name = "to_age", nullable = false)
    private int toAge;

    @Column(name = "effective", nullable = false)
    private LocalDate effective;

    @Column(name = "hourly_tenths", nullable = false)
    private int hourlyTenths;

    @Column(name = "mileage_mills", nullable = false)
    private int mileageMills;

    protected RateBandRow() {}

    public RateBandRow(
            CarTypeCode typeCode, int fromAge, int toAge, LocalDate effective, int hourlyTenths, int mileageMills) {
        this.typeCode = typeCode;
        this.fromAge = fromAge;
        this.toAge = toAge;
        this.effective = effective;
        this.hourlyTenths = hourlyTenths;
        this.mileageMills = mileageMills;
    }

    public Integer getId() {
        return id;
    }

    public CarTypeCode getTypeCode() {
        return typeCode;
    }

    public int getFromAge() {
        return fromAge;
    }

    public int getToAge() {
        return toAge;
    }

    public LocalDate getEffective() {
        return effective;
    }

    public int getHourlyTenths() {
        return hourlyTenths;
    }

    public int getMileageMills() {
        return mileageMills;
    }

    @Override
    public String toString() {
        return "<RateBandRow " + typeCode + " " + fromAge + "-" + toAge + " " + effective + ">";
    }
}
