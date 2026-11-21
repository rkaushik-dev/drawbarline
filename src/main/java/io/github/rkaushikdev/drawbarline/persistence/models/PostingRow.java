package io.github.rkaushikdev.drawbarline.persistence.models;

import io.github.rkaushikdev.drawbarline.limits.Limits;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Locale;
import org.hibernate.annotations.Check;

/**
 * One line of the account with one road.
 *
 * <p>The account is a ledger, never a balance: a month's car hire is a posting, a correction is
 * another posting, and the balance is what they add up to.
 */
@Entity
@Table(
        name = "posting",
        indexes = {
            @Index(name = "ix_posting_road", columnList = "road"),
            @Index(name = "ix_posting_road_period", columnList = "road, period_year, period_month")
        })
@Check(name = "ck_posting_month", constraints = PostingRow.MONTH_CHECK)
public class PostingRow {

    /** The check every table keyed on a month carries. */
    static final String MONTH_CHECK = "period_month between 1 and 12";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "road", length = Limits.REPORTING_MARK, nullable = false)
    private ReportingMark road;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(name = "kind", length = 12, nullable = false)
    private String kind;

    @Column(name = "amount", nullable = false)
    private Money amount;

    @Column(name = "posted_on", nullable = false)
    private LocalDate postedOn;

    @Column(name = "reference", length = Limits.REFERENCE, nullable = false)
    private String reference = "";

    protected PostingRow() {}

    public PostingRow(
            ReportingMark road,
            int periodYear,
            int periodMonth,
            String kind,
            Money amount,
            LocalDate postedOn,
            String reference) {
        this.road = road;
        this.periodYear = periodYear;
        this.periodMonth = periodMonth;
        this.kind = kind;
        this.amount = amount;
        this.postedOn = postedOn;
        this.reference = reference;
    }

    public Integer getId() {
        return id;
    }

    public ReportingMark getRoad() {
        return road;
    }

    public int getPeriodYear() {
        return periodYear;
    }

    public int getPeriodMonth() {
        return periodMonth;
    }

    public String getKind() {
        return kind;
    }

    public Money getAmount() {
        return amount;
    }

    public LocalDate getPostedOn() {
        return postedOn;
    }

    public String getReference() {
        return reference;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "<PostingRow %s %d-%02d %s>", road, periodYear, periodMonth, kind);
    }
}
