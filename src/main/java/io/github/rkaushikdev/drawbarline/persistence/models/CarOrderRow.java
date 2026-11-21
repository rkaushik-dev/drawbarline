package io.github.rkaushikdev.drawbarline.persistence.models;

import io.github.rkaushikdev.drawbarline.limits.Limits;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Optional;
import org.hibernate.annotations.Check;

/**
 * A shipper asking for cars of a kind on a day.
 *
 * <p>The row keeps the day the order was placed as well as the day the cars are wanted for. Both
 * are needed: the wanted day is what the distribution sorts on, and the placed day is what a
 * shipper points at when they say they asked first. An order is never edited, only cancelled; a
 * shipper who wants fewer cars cancels and orders again, which leaves a record of both.
 */
@Entity
@Table(
        name = "car_order",
        indexes = {
            @Index(name = "ix_car_order_industry_code", columnList = "industry_code"),
            @Index(name = "ix_car_order_shipper_code", columnList = "shipper_code"),
            @Index(name = "ix_order_wanted", columnList = "wanted_on")
        })
@Check(name = "ck_order_numbered_from_one", constraints = "number >= 1")
@Check(name = "ck_order_asks_for_a_car", constraints = "cars >= 1")
public class CarOrderRow {

    @Id
    @Column(name = "number", nullable = false)
    private Integer number;

    @Column(name = "shipper_code", length = Limits.SHIPPER_CODE, nullable = false)
    private String shipperCode;

    @Column(name = "industry_code", length = Limits.INDUSTRY_CODE, nullable = false)
    private String industryCode;

    @Column(name = "kind", length = 1, nullable = false)
    private String kind;

    @Column(name = "cars", nullable = false)
    private int cars;

    @Column(name = "wanted_on", nullable = false)
    private LocalDate wantedOn;

    @Column(name = "placed_on", nullable = false)
    private LocalDate placedOn;

    @Column(name = "cancelled_on")
    private LocalDate cancelledOn;

    /** Declares the foreign key on {@code shipper_code}; the code is written through its own column. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "shipper_code",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_car_order_shipper_code_shipper"))
    private ShipperRow shipper;

    /** Declares the foreign key on {@code industry_code}; the code is written through its own column. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "industry_code",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_car_order_industry_code_industry"))
    private IndustryRow industry;

    protected CarOrderRow() {}

    /** An order as it is placed, with the letter of the kind of car wanted. */
    public CarOrderRow(
            int number,
            String shipperCode,
            String industryCode,
            String kind,
            int cars,
            LocalDate wantedOn,
            LocalDate placedOn) {
        this.number = number;
        this.shipperCode = shipperCode;
        this.industryCode = industryCode;
        this.kind = kind;
        this.cars = cars;
        this.wantedOn = wantedOn;
        this.placedOn = placedOn;
    }

    public int getNumber() {
        return number;
    }

    public String getShipperCode() {
        return shipperCode;
    }

    public String getIndustryCode() {
        return industryCode;
    }

    /** The letter of the kind of car wanted. */
    public String getKind() {
        return kind;
    }

    public int getCars() {
        return cars;
    }

    public LocalDate getWantedOn() {
        return wantedOn;
    }

    public LocalDate getPlacedOn() {
        return placedOn;
    }

    public Optional<LocalDate> getCancelledOn() {
        return Optional.ofNullable(cancelledOn);
    }

    /** Cancel the order on a day. */
    public void setCancelledOn(LocalDate cancelledOn) {
        this.cancelledOn = cancelledOn;
    }

    @Override
    public String toString() {
        return "<CarOrderRow " + number + " " + shipperCode + " " + cars + ">";
    }
}
