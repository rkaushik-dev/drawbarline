package io.github.rkaushikdev.drawbarline.persistence.models;

import io.github.rkaushikdev.drawbarline.limits.Limits;
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
import java.util.Optional;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** One line of a repair card. It goes with its card. */
@Entity
@Table(
        name = "defect",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_defect_one_a_position",
                        columnNames = {"card_number", "position"}),
        indexes = @Index(name = "ix_defect_card_number", columnList = "card_number"))
@Check(name = "ck_defect_covers_a_part", constraints = "quantity >= 1")
@Check(name = "ck_defect_part_age", constraints = "part_years >= 0")
public class DefectRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "card_number", nullable = false)
    private Integer cardNumber;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "job", length = Limits.JOB_CODE, nullable = false)
    private String job;

    @Column(name = "why_made", length = Limits.WHY_MADE_CODE, nullable = false)
    private String whyMade;

    @Column(name = "quantity", nullable = false)
    private int quantity = 1;

    @Column(name = "overridden", length = 16)
    private String overridden;

    @Column(name = "part_years", nullable = false)
    private int partYears;

    @Column(name = "note", length = Limits.NOTE, nullable = false)
    private String note = "";

    /** The card the line is on; its number is written through {@code card_number}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "card_number",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_defect_card_number_repair_card"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RepairCardRow card;

    protected DefectRow() {}

    /**
     * A line to be put on a card with {@link RepairCardRow#addDefect}. {@code overridden} names the
     * party made responsible by hand, or is {@code null} when the why made code decides it.
     */
    public DefectRow(
            int position, String job, String whyMade, int quantity, String overridden, int partYears, String note) {
        this.position = position;
        this.job = job;
        this.whyMade = whyMade;
        this.quantity = quantity;
        this.overridden = overridden;
        this.partYears = partYears;
        this.note = note;
    }

    void placeOn(RepairCardRow owner) {
        this.cardNumber = owner.getNumber();
        this.card = owner;
    }

    public Integer getId() {
        return id;
    }

    public int getCardNumber() {
        return cardNumber;
    }

    public int getPosition() {
        return position;
    }

    public String getJob() {
        return job;
    }

    public String getWhyMade() {
        return whyMade;
    }

    public int getQuantity() {
        return quantity;
    }

    public Optional<String> getOverridden() {
        return Optional.ofNullable(overridden);
    }

    public int getPartYears() {
        return partYears;
    }

    public String getNote() {
        return note;
    }

    @Override
    public String toString() {
        return "<DefectRow " + cardNumber + "/" + position + " " + job + ">";
    }
}
