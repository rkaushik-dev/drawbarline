package io.github.rkaushikdev.drawbarline.persistence.models;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.annotations.Check;

/**
 * One published demurrage schedule: a header and its steps, at most one taking effect a day.
 *
 * <p>The steps are ordered by their position, and the last one runs to the end. That last step is
 * stored with a zero, which is the convention the domain uses too, so a row and a step say the same
 * thing without a translation nobody would remember.
 */
@Entity
@Table(name = "tariff", uniqueConstraints = @UniqueConstraint(name = "uq_tariff_one_a_day", columnNames = "effective"))
@Check(name = "ck_tariff_loaded_free_time", constraints = "free_days_loaded >= 0")
@Check(name = "ck_tariff_empty_free_time", constraints = "free_days_empty >= 0")
public class TariffRow {

    @Id
    @Column(name = "code", length = 8, nullable = false)
    private String code;

    @Column(name = "effective", nullable = false)
    private LocalDate effective;

    @Column(name = "free_days_loaded", nullable = false)
    private int freeDaysLoaded;

    @Column(name = "free_days_empty", nullable = false)
    private int freeDaysEmpty;

    @OneToMany(mappedBy = "tariff", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<TariffStepRow> steps = new ArrayList<>();

    protected TariffRow() {}

    public TariffRow(String code, LocalDate effective, int freeDaysLoaded, int freeDaysEmpty) {
        this.code = code;
        this.effective = effective;
        this.freeDaysLoaded = freeDaysLoaded;
        this.freeDaysEmpty = freeDaysEmpty;
    }

    public String getCode() {
        return code;
    }

    public LocalDate getEffective() {
        return effective;
    }

    public int getFreeDaysLoaded() {
        return freeDaysLoaded;
    }

    public int getFreeDaysEmpty() {
        return freeDaysEmpty;
    }

    /** The steps, in the order they are charged. */
    public List<TariffStepRow> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    /** Put a step on this tariff, which is what writes its tariff code. */
    public TariffStepRow addStep(TariffStepRow step) {
        step.placeIn(this);
        steps.add(step);
        return step;
    }

    @Override
    public String toString() {
        return "<TariffRow " + code + " from " + effective + ">";
    }
}
