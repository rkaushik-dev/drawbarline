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
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * A private track we place cars on.
 *
 * <p>An industry belongs to a station and to a shipper, and only the shipper is a foreign key. The
 * station is a code out of the reference data, which changes when track is built and not when
 * somebody signs a contract, so it is a plain column of the same width.
 *
 * <p>The days the track is switched are stored as a comma separated list of three letter days.
 */
@Entity
@Table(
        name = "industry",
        indexes = {
            @Index(name = "ix_industry_shipper_code", columnList = "shipper_code"),
            @Index(name = "ix_industry_station", columnList = "station")
        })
@Check(name = "ck_industry_holds_a_car", constraints = "spots >= 1")
@Check(name = "ck_industry_is_switched", constraints = "length(switched) > 0")
public class IndustryRow {

    @Id
    @Column(name = "code", length = Limits.INDUSTRY_CODE, nullable = false)
    private String code;

    @Column(name = "name", length = Limits.PARTY_NAME, nullable = false)
    private String name;

    @Column(name = "station", length = Limits.STATION_CODE, nullable = false)
    private String station;

    @Column(name = "shipper_code", length = Limits.SHIPPER_CODE, nullable = false)
    private String shipperCode;

    @Column(name = "spots", nullable = false)
    private int spots;

    @Column(name = "switched", length = 32, nullable = false)
    private String switched;

    /** The shipper the track belongs to; its code is written through {@code shipper_code}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "shipper_code",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_industry_shipper_code_shipper"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ShipperRow shipper;

    protected IndustryRow() {}

    /** A track to be put under a shipper with {@link ShipperRow#addIndustry}. */
    public IndustryRow(String code, String name, String station, int spots, String switched) {
        this.code = code;
        this.name = name;
        this.station = station;
        this.spots = spots;
        this.switched = switched;
    }

    /** A track under a shipper named by its code. */
    public IndustryRow(String code, String name, String station, String shipperCode, int spots, String switched) {
        this(code, name, station, spots, switched);
        this.shipperCode = shipperCode;
    }

    void placeUnder(ShipperRow owner) {
        this.shipperCode = owner.getCode();
        this.shipper = owner;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getStation() {
        return station;
    }

    public String getShipperCode() {
        return shipperCode;
    }

    /** The shipper the track belongs to. */
    public ShipperRow getShipper() {
        return shipper;
    }

    public int getSpots() {
        return spots;
    }

    /** The days the track is switched, as three letter days separated by commas. */
    public String getSwitched() {
        return switched;
    }

    @Override
    public String toString() {
        return "<IndustryRow " + code + " at " + station + ">";
    }
}
