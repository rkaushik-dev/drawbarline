package io.github.rkaushikdev.drawbarline.persistence.models;

import io.github.rkaushikdev.drawbarline.limits.Limits;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.hibernate.annotations.Check;

/**
 * One movement of one car, as the billing copy carries it, and the route it is authorised over.
 *
 * <p>The route is its own table with a position column rather than a delimited string, because a
 * route is walked, joined against and reported on.
 *
 * <p>A waybill points at a car and the foreign key says so; a car with waybills on file cannot be
 * deleted. It does not point at a shipper or a consignee, because half the parties on a waybill are
 * on somebody else's railroad and there is no record of them beyond the name on the paper.
 */
@Entity
@Table(
        name = "waybill",
        indexes = {
            @Index(name = "ix_waybill_car_mark", columnList = "car_mark"),
            @Index(name = "ix_waybill_dated", columnList = "dated")
        })
@Check(name = "ck_waybill_numbered_from_one", constraints = "number >= 1")
@Check(name = "ck_waybill_lading_weighs", constraints = "lading_stcc is null or lading_pounds > 0")
public class WaybillRow {

    @Id
    @Column(name = "number", nullable = false)
    private Integer number;

    @Column(name = "dated", nullable = false)
    private LocalDate dated;

    @Column(name = "car_mark", length = Limits.CAR_MARK, nullable = false)
    private CarMark carMark;

    @Column(name = "shipper", length = 60, nullable = false)
    private String shipper;

    @Column(name = "consignee", length = 60, nullable = false)
    private String consignee;

    @Column(name = "lading_stcc", length = 7)
    private String ladingStcc;

    @Column(name = "lading_name", length = 60)
    private String ladingName;

    @Column(name = "lading_pounds")
    private Weight ladingPounds;

    @Column(name = "lading_bulk", nullable = false)
    private boolean ladingBulk;

    @OneToMany(mappedBy = "waybill", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<RouteStopRow> stops = new ArrayList<>();

    /** Declares the foreign key on {@code car_mark}; the mark is written through its own column. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "car_mark",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_waybill_car_mark_car"))
    private CarRow car;

    protected WaybillRow() {}

    /**
     * A waybill as it is issued. The lading columns are {@code null} for an empty car moving on a
     * bill.
     */
    public WaybillRow(
            int number,
            LocalDate dated,
            CarMark carMark,
            String shipper,
            String consignee,
            String ladingStcc,
            String ladingName,
            Weight ladingPounds,
            boolean ladingBulk) {
        this.number = number;
        this.dated = dated;
        this.carMark = carMark;
        this.shipper = shipper;
        this.consignee = consignee;
        this.ladingStcc = ladingStcc;
        this.ladingName = ladingName;
        this.ladingPounds = ladingPounds;
        this.ladingBulk = ladingBulk;
    }

    public int getNumber() {
        return number;
    }

    public LocalDate getDated() {
        return dated;
    }

    public CarMark getCarMark() {
        return carMark;
    }

    public String getShipper() {
        return shipper;
    }

    public String getConsignee() {
        return consignee;
    }

    public Optional<String> getLadingStcc() {
        return Optional.ofNullable(ladingStcc);
    }

    public Optional<String> getLadingName() {
        return Optional.ofNullable(ladingName);
    }

    public Optional<Weight> getLadingPounds() {
        return Optional.ofNullable(ladingPounds);
    }

    public boolean isLadingBulk() {
        return ladingBulk;
    }

    /** The stations on the route, in the order the car passes them. */
    public List<RouteStopRow> getStops() {
        return Collections.unmodifiableList(stops);
    }

    /** Put a station on the route, which is what writes its waybill number. */
    public RouteStopRow addStop(RouteStopRow stop) {
        stop.placeOn(this);
        stops.add(stop);
        return stop;
    }

    @Override
    public String toString() {
        return "<WaybillRow " + number + " " + carMark + ">";
    }
}
