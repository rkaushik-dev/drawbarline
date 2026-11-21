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
import java.time.LocalDate;
import java.util.Optional;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * A shipper billed on a monthly balance instead of car by car.
 *
 * <p>Agreements sit beside the tariff rather than inside it. A tariff is published to everybody; an
 * agreement is signed with one shipper, and the two change on different days for different
 * reasons. Deleting the shipper takes its agreements with it.
 */
@Entity
@Table(
        name = "average_agreement",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_agreement_one_a_day",
                        columnNames = {"shipper_code", "effective"}),
        indexes = @Index(name = "ix_average_agreement_shipper_code", columnList = "shipper_code"))
@Check(name = "ck_agreement_not_negative", constraints = "daily_cents >= 0")
@Check(name = "ck_agreement_ends_after", constraints = "expires is null or expires >= effective")
public class AverageAgreementRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "shipper_code", length = 8, nullable = false)
    private String shipperCode;

    @Column(name = "daily_cents", nullable = false)
    private int dailyCents;

    @Column(name = "effective", nullable = false)
    private LocalDate effective;

    @Column(name = "expires")
    private LocalDate expires;

    /** Declares the foreign key on {@code shipper_code}; the code is written through its own column. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "shipper_code",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_average_agreement_shipper_code_shipper"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ShipperRow shipper;

    protected AverageAgreementRow() {}

    /** An agreement as it is signed. {@code null} stands for one with no end date. */
    public AverageAgreementRow(String shipperCode, int dailyCents, LocalDate effective, LocalDate expires) {
        this.shipperCode = shipperCode;
        this.dailyCents = dailyCents;
        this.effective = effective;
        this.expires = expires;
    }

    public Integer getId() {
        return id;
    }

    public String getShipperCode() {
        return shipperCode;
    }

    public int getDailyCents() {
        return dailyCents;
    }

    public LocalDate getEffective() {
        return effective;
    }

    public Optional<LocalDate> getExpires() {
        return Optional.ofNullable(expires);
    }

    @Override
    public String toString() {
        return "<AverageAgreementRow " + shipperCode + " from " + effective + ">";
    }
}
