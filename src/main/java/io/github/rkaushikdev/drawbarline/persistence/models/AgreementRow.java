package io.github.rkaushikdev.drawbarline.persistence.models;

import io.github.rkaushikdev.drawbarline.limits.Limits;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.hibernate.annotations.Check;

/**
 * What one road takes at one junction, from a date.
 *
 * <p>The kinds of car an agreement takes are a string of the kind letters rather than a join
 * table: there are ten kinds and an agreement takes a handful. The hours are stored as times of
 * day rather than as a number of minutes, because the agreement says seven in the morning to seven
 * at night and that is what somebody checks the row against.
 *
 * <p>Constraint names are unique across the whole schema, so the two this table shares in spirit
 * with the average agreement table carry the table's name.
 */
@Entity
@Table(
        name = "interchange_agreement",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_interchange_agreement_one_a_day",
                        columnNames = {"road", "junction", "effective"}),
        indexes = {
            @Index(name = "ix_agreement_junction", columnList = "junction, effective"),
            @Index(name = "ix_interchange_agreement_road", columnList = "road")
        })
@Check(name = "ck_agreement_takes_a_car", constraints = "daily_cap >= 1")
@Check(name = "ck_agreement_takes_a_kind", constraints = "length(kinds) > 0")
@Check(name = "ck_agreement_opens_first", constraints = "opens < closes")
@Check(name = "ck_interchange_agreement_ends_after", constraints = "expires is null or expires >= effective")
public class AgreementRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "road", length = Limits.REPORTING_MARK, nullable = false)
    private ReportingMark road;

    @Column(name = "junction", length = Limits.STATION_CODE, nullable = false)
    private String junction;

    @Column(name = "effective", nullable = false)
    private LocalDate effective;

    @Column(name = "kinds", length = 16, nullable = false)
    private String kinds;

    @Column(name = "daily_cap", nullable = false)
    private int dailyCap;

    @Column(name = "opens", nullable = false)
    private LocalTime opens;

    @Column(name = "closes", nullable = false)
    private LocalTime closes;

    @Column(name = "hazardous", nullable = false)
    private boolean hazardous;

    @Column(name = "expires")
    private LocalDate expires;

    protected AgreementRow() {}

    /** An agreement as it is signed. {@code null} stands for one with no end date. */
    public AgreementRow(
            ReportingMark road,
            String junction,
            LocalDate effective,
            String kinds,
            int dailyCap,
            LocalTime opens,
            LocalTime closes,
            boolean hazardous,
            LocalDate expires) {
        this.road = road;
        this.junction = junction;
        this.effective = effective;
        this.kinds = kinds;
        this.dailyCap = dailyCap;
        this.opens = opens;
        this.closes = closes;
        this.hazardous = hazardous;
        this.expires = expires;
    }

    public Integer getId() {
        return id;
    }

    public ReportingMark getRoad() {
        return road;
    }

    public String getJunction() {
        return junction;
    }

    public LocalDate getEffective() {
        return effective;
    }

    /** The letters of the kinds of car taken. */
    public String getKinds() {
        return kinds;
    }

    public int getDailyCap() {
        return dailyCap;
    }

    public LocalTime getOpens() {
        return opens;
    }

    public LocalTime getCloses() {
        return closes;
    }

    public boolean isHazardous() {
        return hazardous;
    }

    public Optional<LocalDate> getExpires() {
        return Optional.ofNullable(expires);
    }

    @Override
    public String toString() {
        return "<AgreementRow " + junction + "/" + road + " from " + effective + ">";
    }
}
