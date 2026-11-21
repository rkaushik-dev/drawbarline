package io.github.rkaushikdev.drawbarline.persistence.models;

import io.github.rkaushikdev.drawbarline.limits.Limits;
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
 * One priced job, from a date.
 *
 * <p>The price master is one row a job a date, the same shape as the car hire schedule and for the
 * same reason: a price that changed on the first of July has to still be readable in December when
 * somebody disputes a card written in June.
 */
@Entity
@Table(
        name = "price_line",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_price_one_a_day",
                        columnNames = {"job", "effective"}),
        indexes = @Index(name = "ix_price_line_job", columnList = "job"))
@Check(name = "ck_price_labor_not_negative", constraints = "labor_hundredths >= 0")
@Check(name = "ck_price_material_not_negative", constraints = "material_cents >= 0")
public class PriceLineRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "job", length = Limits.JOB_CODE, nullable = false)
    private String job;

    @Column(name = "effective", nullable = false)
    private LocalDate effective;

    @Column(name = "labor_hundredths", nullable = false)
    private int laborHundredths;

    @Column(name = "material_cents", nullable = false)
    private int materialCents;

    protected PriceLineRow() {}

    public PriceLineRow(String job, LocalDate effective, int laborHundredths, int materialCents) {
        this.job = job;
        this.effective = effective;
        this.laborHundredths = laborHundredths;
        this.materialCents = materialCents;
    }

    public Integer getId() {
        return id;
    }

    public String getJob() {
        return job;
    }

    public LocalDate getEffective() {
        return effective;
    }

    /** The labor the job takes, in hundredths of an hour. */
    public int getLaborHundredths() {
        return laborHundredths;
    }

    public int getMaterialCents() {
        return materialCents;
    }

    @Override
    public String toString() {
        return "<PriceLineRow " + job + " from " + effective + ">";
    }
}
