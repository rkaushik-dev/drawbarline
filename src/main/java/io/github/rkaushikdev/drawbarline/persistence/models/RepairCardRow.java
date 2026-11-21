package io.github.rkaushikdev.drawbarline.persistence.models;

import io.github.rkaushikdev.drawbarline.limits.Limits;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
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
import org.hibernate.annotations.Check;

/**
 * What one road did to one car on one day, and the lines of work on the card.
 *
 * <p>The card tables are append only. A card is never edited: a card that was wrong is answered
 * with an exception, and an exception that was wrong is answered by allowing less of it. A line is
 * identified by its position, which is what an exception points at, so a card written with its
 * lines in a different order is a different card as far as an exception is concerned.
 */
@Entity
@Table(
        name = "repair_card",
        indexes = {
            @Index(name = "ix_repair_card_car_mark", columnList = "car_mark"),
            @Index(name = "ix_repair_card_performed_by", columnList = "performed_by"),
            @Index(name = "ix_repair_card_performed_on", columnList = "performed_on")
        })
@Check(name = "ck_card_numbered_from_one", constraints = "number >= 1")
public class RepairCardRow {

    @Id
    @Column(name = "number", nullable = false)
    private Integer number;

    @Column(name = "car_mark", length = Limits.CAR_MARK, nullable = false)
    private CarMark carMark;

    @Column(name = "performed_by", length = Limits.REPORTING_MARK, nullable = false)
    private ReportingMark performedBy;

    @Column(name = "performed_on", nullable = false)
    private LocalDate performedOn;

    @Column(name = "station", length = Limits.STATION_CODE, nullable = false)
    private String station;

    @OneToMany(mappedBy = "card", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<DefectRow> defects = new ArrayList<>();

    /** Declares the foreign key on {@code car_mark}; the mark is written through its own column. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "car_mark",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_repair_card_car_mark_car"))
    private CarRow car;

    protected RepairCardRow() {}

    public RepairCardRow(
            int number, CarMark carMark, ReportingMark performedBy, LocalDate performedOn, String station) {
        this.number = number;
        this.carMark = carMark;
        this.performedBy = performedBy;
        this.performedOn = performedOn;
        this.station = station;
    }

    public int getNumber() {
        return number;
    }

    public CarMark getCarMark() {
        return carMark;
    }

    public ReportingMark getPerformedBy() {
        return performedBy;
    }

    public LocalDate getPerformedOn() {
        return performedOn;
    }

    public String getStation() {
        return station;
    }

    /** The lines of the card, in the order they were written. */
    public List<DefectRow> getDefects() {
        return Collections.unmodifiableList(defects);
    }

    /** Put a line on this card, which is what writes its card number. */
    public DefectRow addDefect(DefectRow defect) {
        defect.placeOn(this);
        defects.add(defect);
        return defect;
    }

    @Override
    public String toString() {
        return "<RepairCardRow " + number + " " + carMark + ">";
    }
}
