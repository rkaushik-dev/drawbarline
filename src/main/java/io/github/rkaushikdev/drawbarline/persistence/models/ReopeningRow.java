package io.github.rkaushikdev.drawbarline.persistence.models;

import io.github.rkaushikdev.drawbarline.limits.Limits;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Locale;
import org.hibernate.annotations.Check;

/** A month opened again, and the reason it had to be. A month can be reopened more than once. */
@Entity
@Table(name = "reopening")
@Check(name = "ck_reopening_month", constraints = PostingRow.MONTH_CHECK)
public class ReopeningRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(name = "reopened_on", nullable = false)
    private LocalDate reopenedOn;

    @Column(name = "reason", length = Limits.REASON, nullable = false)
    private String reason;

    protected ReopeningRow() {}

    public ReopeningRow(int periodYear, int periodMonth, LocalDate reopenedOn, String reason) {
        this.periodYear = periodYear;
        this.periodMonth = periodMonth;
        this.reopenedOn = reopenedOn;
        this.reason = reason;
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

    public LocalDate getReopenedOn() {
        return reopenedOn;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "<ReopeningRow %d-%02d>", periodYear, periodMonth);
    }
}
