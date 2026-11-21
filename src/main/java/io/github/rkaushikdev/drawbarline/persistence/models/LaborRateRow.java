package io.github.rkaushikdev.drawbarline.persistence.models;

import io.github.rkaushikdev.drawbarline.limits.Limits;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
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

/**
 * What one road charges an hour, from a date. Every road charges its own, and a card is priced at
 * the rate of the road that did the work.
 */
@Entity
@Table(
        name = "labor_rate",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_labor_one_a_day",
                        columnNames = {"road", "effective"}),
        indexes = @Index(name = "ix_labor_rate_road", columnList = "road"))
@Check(name = "ck_labor_not_negative", constraints = "cents_an_hour >= 0")
public class LaborRateRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "road", length = Limits.REPORTING_MARK, nullable = false)
    private ReportingMark road;

    @Column(name = "effective", nullable = false)
    private LocalDate effective;

    @Column(name = "cents_an_hour", nullable = false)
    private int centsAnHour;

    protected LaborRateRow() {}

    public LaborRateRow(ReportingMark road, LocalDate effective, int centsAnHour) {
        this.road = road;
        this.effective = effective;
        this.centsAnHour = centsAnHour;
    }

    public Integer getId() {
        return id;
    }

    public ReportingMark getRoad() {
        return road;
    }

    public LocalDate getEffective() {
        return effective;
    }

    public int getCentsAnHour() {
        return centsAnHour;
    }

    @Override
    public String toString() {
        return "<LaborRateRow " + road + " from " + effective + ">";
    }
}
