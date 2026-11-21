package io.github.rkaushikdev.drawbarline.persistence.models;

import io.github.rkaushikdev.drawbarline.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** How one exception was answered, keyed on the exception it answers. At most one an exception. */
@Entity
@Table(name = "resolution")
@Check(name = "ck_resolution_not_negative", constraints = "allowed >= 0")
public class ResolutionRow {

    @Id
    @Column(name = "objection_id", nullable = false)
    private Integer objectionId;

    @Column(name = "outcome", length = 16, nullable = false)
    private String outcome;

    @Column(name = "allowed", nullable = false)
    private Money allowed;

    /** The exception answered, which is also where the key comes from. */
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "objection_id", foreignKey = @ForeignKey(name = "fk_resolution_objection_id_objection"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ObjectionRow objection;

    protected ResolutionRow() {}

    /** An answer to be put on an exception with {@link ObjectionRow#setResolution}. */
    public ResolutionRow(String outcome, Money allowed) {
        this.outcome = outcome;
        this.allowed = allowed;
    }

    void placeOn(ObjectionRow owner) {
        this.objection = owner;
    }

    public Integer getObjectionId() {
        return objectionId;
    }

    public String getOutcome() {
        return outcome;
    }

    public Money getAllowed() {
        return allowed;
    }

    @Override
    public String toString() {
        return "<ResolutionRow " + objectionId + " " + outcome + ">";
    }
}
