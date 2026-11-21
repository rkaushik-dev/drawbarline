package io.github.rkaushikdev.drawbarline.persistence.models;

import io.github.rkaushikdev.drawbarline.limits.Limits;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.Optional;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** A term somebody other than the owner collected for. Deleting the car takes its leases with it. */
@Entity
@Table(
        name = "lease",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_lease_one_start_a_day",
                        columnNames = {"car_mark", "began"}),
        indexes = {
            @Index(name = "ix_lease_car_mark", columnList = "car_mark"),
            @Index(name = "ix_lease_lessee", columnList = "lessee")
        })
@Check(name = "ck_lease_ends_after", constraints = "ended is null or ended >= began")
public class LeaseRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "car_mark", length = Limits.CAR_MARK, nullable = false)
    private CarMark carMark;

    @Column(name = "lessee", length = Limits.REPORTING_MARK, nullable = false)
    private ReportingMark lessee;

    @Column(name = "began", nullable = false)
    private LocalDate began;

    @Column(name = "ended")
    private LocalDate ended;

    /** Declares the foreign key on {@code car_mark}; the mark is written through its own column. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "car_mark",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_lease_car_mark_car"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CarRow car;

    protected LeaseRow() {}

    /** A term to be put on a car with {@link CarRow#addLease}. */
    public LeaseRow(ReportingMark lessee, LocalDate began, LocalDate ended) {
        this.lessee = lessee;
        this.began = began;
        this.ended = ended;
    }

    /** A term against a car named by its marks. */
    public LeaseRow(CarMark carMark, ReportingMark lessee, LocalDate began, LocalDate ended) {
        this(lessee, began, ended);
        this.carMark = carMark;
    }

    void placeOn(CarRow owner) {
        this.carMark = owner.getMark();
        this.car = owner;
    }

    public Integer getId() {
        return id;
    }

    public CarMark getCarMark() {
        return carMark;
    }

    public ReportingMark getLessee() {
        return lessee;
    }

    public LocalDate getBegan() {
        return began;
    }

    public Optional<LocalDate> getEnded() {
        return Optional.ofNullable(ended);
    }

    /** End the term, or pass {@code null} to leave it open. */
    public void setEnded(LocalDate ended) {
        this.ended = ended;
    }

    @Override
    public String toString() {
        return "<LeaseRow " + carMark + " to " + lessee + ">";
    }
}
