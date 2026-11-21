package io.github.rkaushikdev.drawbarline.persistence.models;

import io.github.rkaushikdev.drawbarline.events.EventKind;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.hibernate.annotations.Check;

/**
 * One thing that happened to one car, and nothing derived from it.
 *
 * <p>The table carries a surrogate key because the natural key would be the whole row, and a
 * unique constraint over the fields that make an event what it is, so a duplicate interchange sheet
 * is refused by the database as well as by the log.
 *
 * <p>The optional columns are optional because the kinds differ, not because data is missing. A
 * road on a placement or a track on an interchange would both be nonsense, and the domain refuses
 * them on the way in. The table does not try to say which kinds allow which columns: that would be
 * a clause a kind, edited every time a kind is added, and still not the whole rule. The kind is
 * stored by its name, so a dump of the table is legible.
 */
@Entity
@Table(
        name = "car_event",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_event_not_twice",
                        columnNames = {"car_mark", "kind", "happened", "station"}),
        indexes = {
            @Index(name = "ix_car_event_car_mark", columnList = "car_mark"),
            @Index(name = "ix_event_car_when", columnList = "car_mark, happened"),
            @Index(name = "ix_event_when", columnList = "happened")
        })
@Check(name = "ck_event_movement_covers_ground", constraints = "tenths is null or tenths > 0")
@Check(name = "ck_event_waybill_numbered", constraints = "waybill is null or waybill > 0")
public class EventRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "car_mark", length = Limits.CAR_MARK, nullable = false)
    private CarMark carMark;

    @Column(name = "kind", length = 16, nullable = false)
    private String kind;

    @Column(name = "happened", nullable = false)
    private Instant happened;

    @Column(name = "station", length = Limits.STATION_CODE, nullable = false)
    private String station;

    @Column(name = "road", length = Limits.REPORTING_MARK)
    private ReportingMark road;

    @Column(name = "industry", length = Limits.INDUSTRY_CODE)
    private String industry;

    @Column(name = "waybill")
    private Integer waybill;

    @Column(name = "tenths")
    private Integer tenths;

    @Column(name = "note", length = Limits.NOTE, nullable = false)
    private String note = "";

    /** Declares the foreign key on {@code car_mark}; the mark is written through its own column. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "car_mark",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_car_event_car_mark_car"))
    private CarRow car;

    protected EventRow() {}

    /** One event as it is filed. {@code null} stands for an optional column left empty. */
    public EventRow(
            CarMark carMark,
            EventKind kind,
            Instant happened,
            String station,
            ReportingMark road,
            String industry,
            Integer waybill,
            Integer tenths,
            String note) {
        this.carMark = carMark;
        this.kind = kind.name();
        this.happened = happened;
        this.station = station;
        this.road = road;
        this.industry = industry;
        this.waybill = waybill;
        this.tenths = tenths;
        this.note = note;
    }

    public Integer getId() {
        return id;
    }

    public CarMark getCarMark() {
        return carMark;
    }

    /** The kind, read back from the name it is stored by. */
    public EventKind getKind() {
        return EventKind.kindNamed(kind);
    }

    public Instant getHappened() {
        return happened;
    }

    public String getStation() {
        return station;
    }

    public Optional<ReportingMark> getRoad() {
        return Optional.ofNullable(road);
    }

    public Optional<String> getIndustry() {
        return Optional.ofNullable(industry);
    }

    public Optional<Integer> getWaybill() {
        return Optional.ofNullable(waybill);
    }

    public Optional<Integer> getTenths() {
        return Optional.ofNullable(tenths);
    }

    public String getNote() {
        return note;
    }

    @Override
    public String toString() {
        return "<EventRow " + carMark + " " + kind + " "
                + happened.atOffset(ZoneOffset.UTC).toLocalDate() + ">";
    }
}
