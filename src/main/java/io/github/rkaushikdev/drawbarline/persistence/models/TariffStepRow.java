package io.github.rkaushikdev.drawbarline.persistence.models;

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

/**
 * One price band inside a tariff: the day it runs through, zero for the last, and the cents a day.
 * Deleting the tariff takes its steps with it.
 */
@Entity
@Table(
        name = "tariff_step",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_step_one_a_position",
                        columnNames = {"tariff_code", "position"}),
        indexes = @Index(name = "ix_tariff_step_tariff_code", columnList = "tariff_code"))
@Check(name = "ck_step_through_not_negative", constraints = "through >= 0")
@Check(name = "ck_step_not_negative", constraints = "cents >= 0")
public class TariffStepRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "tariff_code", length = 8, nullable = false)
    private String tariffCode;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "through", nullable = false)
    private int through;

    @Column(name = "cents", nullable = false)
    private int cents;

    /** The tariff the step is part of; its code is written through {@code tariff_code}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "tariff_code",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_tariff_step_tariff_code_tariff"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private TariffRow tariff;

    protected TariffStepRow() {}

    /** A step to be put on a tariff with {@link TariffRow#addStep}. */
    public TariffStepRow(int position, int through, int cents) {
        this.position = position;
        this.through = through;
        this.cents = cents;
    }

    void placeIn(TariffRow owner) {
        this.tariffCode = owner.getCode();
        this.tariff = owner;
    }

    public Integer getId() {
        return id;
    }

    public String getTariffCode() {
        return tariffCode;
    }

    public int getPosition() {
        return position;
    }

    /** The last day the step runs through, or zero for the step that runs to the end. */
    public int getThrough() {
        return through;
    }

    public int getCents() {
        return cents;
    }

    @Override
    public String toString() {
        return "<TariffStepRow " + tariffCode + " through " + through + ">";
    }
}
