package io.github.rkaushikdev.drawbarline.persistence.models;

import io.github.rkaushikdev.drawbarline.limits.Limits;
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
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** One station on a waybill route, in the order the car passes it. It goes with its waybill. */
@Entity
@Table(
        name = "route_stop",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_stop_one_a_position",
                        columnNames = {"waybill_number", "position"}),
        indexes = @Index(name = "ix_route_stop_waybill_number", columnList = "waybill_number"))
@Check(name = "ck_stop_position_not_negative", constraints = "position >= 0")
public class RouteStopRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "waybill_number", nullable = false)
    private Integer waybillNumber;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "station", length = Limits.STATION_CODE, nullable = false)
    private String station;

    /** The waybill the stop is on; its number is written through {@code waybill_number}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "waybill_number",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_route_stop_waybill_number_waybill"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private WaybillRow waybill;

    protected RouteStopRow() {}

    /** A stop to be put on a waybill with {@link WaybillRow#addStop}. */
    public RouteStopRow(int position, String station) {
        this.position = position;
        this.station = station;
    }

    void placeOn(WaybillRow owner) {
        this.waybillNumber = owner.getNumber();
        this.waybill = owner;
    }

    public Integer getId() {
        return id;
    }

    public int getWaybillNumber() {
        return waybillNumber;
    }

    public int getPosition() {
        return position;
    }

    public String getStation() {
        return station;
    }

    @Override
    public String toString() {
        return "<RouteStopRow " + waybillNumber + " " + position + " " + station + ">";
    }
}
