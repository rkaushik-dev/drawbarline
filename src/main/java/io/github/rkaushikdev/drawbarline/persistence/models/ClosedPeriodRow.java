package io.github.rkaushikdev.drawbarline.persistence.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.Locale;
import org.hibernate.annotations.Check;

/** A month that is shut to posting. A month is closed at most once. */
@Entity
@Table(
        name = "closed_period",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_closed_once",
                        columnNames = {"period_year", "period_month"}))
@Check(name = "ck_closed_month", constraints = PostingRow.MONTH_CHECK)
public class ClosedPeriodRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(name = "closed_on", nullable = false)
    private LocalDate closedOn;

    protected ClosedPeriodRow() {}

    public ClosedPeriodRow(int periodYear, int periodMonth, LocalDate closedOn) {
        this.periodYear = periodYear;
        this.periodMonth = periodMonth;
        this.closedOn = closedOn;
    }

    public Integer getId() {
        return id;
    }

    public int getPeriodYear() {
        return periodYear;
    }

    public int getPeriodMonth() {
        return periodMonth;
    }

    public LocalDate getClosedOn() {
        return closedOn;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "<ClosedPeriodRow %d-%02d>", periodYear, periodMonth);
    }
}
