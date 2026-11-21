package io.github.rkaushikdev.drawbarline.persistence.models;

import io.github.rkaushikdev.drawbarline.limits.Limits;
import io.github.rkaushikdev.drawbarline.money.Money;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.Optional;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * An exception filed against one line of a repair card, at most one a line, and how it was answered
 * once it was. It goes with its card.
 */
@Entity
@Table(
        name = "objection",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_objection_one_a_line",
                        columnNames = {"card_number", "line"}),
        indexes = @Index(name = "ix_objection_card_number", columnList = "card_number"))
@Check(name = "ck_objection_disputes_something", constraints = "disputed > 0")
public class ObjectionRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "card_number", nullable = false)
    private int cardNumber;

    @Column(name = "line", nullable = false)
    private int line;

    @Column(name = "ground", length = 20, nullable = false)
    private String ground;

    @Column(name = "disputed", nullable = false)
    private Money disputed;

    @Column(name = "filed_on", nullable = false)
    private LocalDate filedOn;

    @Column(name = "note", length = Limits.REASON, nullable = false)
    private String note = "";

    @OneToOne(mappedBy = "objection", cascade = CascadeType.ALL, orphanRemoval = true)
    private ResolutionRow resolution;

    /** Declares the foreign key on {@code card_number}; the number is written through its own column. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "card_number",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_objection_card_number_repair_card"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RepairCardRow card;

    protected ObjectionRow() {}

    public ObjectionRow(int cardNumber, int line, String ground, Money disputed, LocalDate filedOn, String note) {
        this.cardNumber = cardNumber;
        this.line = line;
        this.ground = ground;
        this.disputed = disputed;
        this.filedOn = filedOn;
        this.note = note;
    }

    public Integer getId() {
        return id;
    }

    public int getCardNumber() {
        return cardNumber;
    }

    /** The position of the line on the card the exception is against. */
    public int getLine() {
        return line;
    }

    public String getGround() {
        return ground;
    }

    public Money getDisputed() {
        return disputed;
    }

    public LocalDate getFiledOn() {
        return filedOn;
    }

    public String getNote() {
        return note;
    }

    public Optional<ResolutionRow> getResolution() {
        return Optional.ofNullable(resolution);
    }

    /** Answer the exception. */
    public void setResolution(ResolutionRow resolution) {
        resolution.placeOn(this);
        this.resolution = resolution;
    }

    @Override
    public String toString() {
        return "<ObjectionRow " + cardNumber + "/" + line + ">";
    }
}
