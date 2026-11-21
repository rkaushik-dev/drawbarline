package io.github.rkaushikdev.drawbarline.persistence.models;

import io.github.rkaushikdev.drawbarline.limits.Limits;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Optional;
import org.hibernate.annotations.Check;

/**
 * One restriction, from a date to a date.
 *
 * <p>Lifting an embargo is an update in place rather than a second row, which is the only place in
 * the schema besides the counters where that happens. It earns it: an embargo is a live instrument
 * with an end date that is not known when it is issued, and a second row saying the same embargo
 * with a different end would leave two rows that both look current.
 *
 * <p>Every restriction column is optional and at least one of them has to be set. The table checks
 * it, because an embargo with all three empty stops the entire railroad.
 */
@Entity
@Table(name = "embargo", indexes = @Index(name = "ix_embargo_effective", columnList = "effective"))
@Check(name = "ck_embargo_numbered_from_one", constraints = "number >= 1")
@Check(name = "ck_embargo_restricts_something", constraints = EmbargoRow.RESTRICTS_SOMETHING)
@Check(name = "ck_embargo_ends_after", constraints = "expires is null or expires >= effective")
public class EmbargoRow {

    /** The check that an embargo restricts at least one thing. */
    public static final String RESTRICTS_SOMETHING =
            "station is not null or commodity_family is not null or road is not null";

    @Id
    @Column(name = "number", nullable = false)
    private Integer number;

    @Column(name = "effective", nullable = false)
    private LocalDate effective;

    @Column(name = "reason", length = Limits.REASON, nullable = false)
    private String reason;

    @Column(name = "station", length = Limits.STATION_CODE)
    private String station;

    @Column(name = "commodity_family", length = Limits.COMMODITY_CODE)
    private String commodityFamily;

    @Column(name = "road", length = Limits.REPORTING_MARK)
    private ReportingMark road;

    @Column(name = "expires")
    private LocalDate expires;

    @Column(name = "permits_allowed", nullable = false)
    private boolean permitsAllowed;

    protected EmbargoRow() {}

    /** An embargo as it is issued. {@code null} stands for a restriction or an end left empty. */
    public EmbargoRow(
            int number,
            LocalDate effective,
            String reason,
            String station,
            String commodityFamily,
            ReportingMark road,
            LocalDate expires,
            boolean permitsAllowed) {
        this.number = number;
        this.effective = effective;
        this.reason = reason;
        this.station = station;
        this.commodityFamily = commodityFamily;
        this.road = road;
        this.expires = expires;
        this.permitsAllowed = permitsAllowed;
    }

    public int getNumber() {
        return number;
    }

    public LocalDate getEffective() {
        return effective;
    }

    public String getReason() {
        return reason;
    }

    public Optional<String> getStation() {
        return Optional.ofNullable(station);
    }

    public Optional<String> getCommodityFamily() {
        return Optional.ofNullable(commodityFamily);
    }

    public Optional<ReportingMark> getRoad() {
        return Optional.ofNullable(road);
    }

    public Optional<LocalDate> getExpires() {
        return Optional.ofNullable(expires);
    }

    /** Set the last day the embargo is in force, which is how one is lifted. */
    public void setExpires(LocalDate expires) {
        this.expires = expires;
    }

    public boolean isPermitsAllowed() {
        return permitsAllowed;
    }

    @Override
    public String toString() {
        return "<EmbargoRow " + number + " from " + effective + ">";
    }
}
