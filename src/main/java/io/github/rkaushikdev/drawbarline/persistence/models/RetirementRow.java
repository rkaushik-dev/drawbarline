package io.github.rkaushikdev.drawbarline.persistence.models;

import io.github.rkaushikdev.drawbarline.limits.Limits;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * The day a car came off the roster, kept so settlement can still find it. At most one a car,
 * keyed on the car's marks.
 *
 * <p>The day is stored in a column called {@code on}, a word the database reserves, so the column
 * name is quoted wherever it is written.
 */
@Entity
@Table(name = "retirement")
public class RetirementRow {

    @Id
    @Column(name = "car_mark", length = Limits.CAR_MARK, nullable = false)
    private String carMark;

    @Column(name = "`ON`", nullable = false)
    private LocalDate retiredOn;

    @Column(name = "reason", length = 120, nullable = false)
    private String reason = "";

    /** The car retired, which is also where the key comes from. */
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "car_mark", foreignKey = @ForeignKey(name = "fk_retirement_car_mark_car"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CarRow car;

    protected RetirementRow() {}

    /** A retirement to be put on a car with {@link CarRow#setRetirement}. */
    public RetirementRow(LocalDate on, String reason) {
        this.retiredOn = on;
        this.reason = reason;
    }

    void placeOn(CarRow owner) {
        this.carMark = CarRow.keyOf(owner.getMark());
        this.car = owner;
    }

    public CarMark getCarMark() {
        return CarMark.parse(carMark);
    }

    /** The day the car came off the roster. */
    public LocalDate getOn() {
        return retiredOn;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "<RetirementRow " + carMark + " " + retiredOn + ">";
    }
}
