package io.github.rkaushikdev.drawbarline.persistence.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Check;

/**
 * How fast one family of parts is used up.
 *
 * <p>Depreciation is by family rather than by job, keyed on the two digit family. There are about a
 * dozen rows and they change when the office manual is reissued, in place: the old percentages are
 * of no interest once a card has been priced.
 */
@Entity
@Table(name = "depreciation")
@Check(name = "ck_depreciation_is_a_percentage", constraints = "percent_a_year between 0 and 100")
@Check(name = "ck_depreciation_floor", constraints = "floor_percent between 0 and 100")
public class DepreciationRow {

    @Id
    @Column(name = "family", length = 2, nullable = false)
    private String family;

    @Column(name = "percent_a_year", nullable = false)
    private int percentAYear;

    @Column(name = "floor_percent", nullable = false)
    private int floorPercent;

    protected DepreciationRow() {}

    public DepreciationRow(String family, int percentAYear, int floorPercent) {
        this.family = family;
        this.percentAYear = percentAYear;
        this.floorPercent = floorPercent;
    }

    public String getFamily() {
        return family;
    }

    public int getPercentAYear() {
        return percentAYear;
    }

    public void setPercentAYear(int percentAYear) {
        this.percentAYear = percentAYear;
    }

    public int getFloorPercent() {
        return floorPercent;
    }

    public void setFloorPercent(int floorPercent) {
        this.floorPercent = floorPercent;
    }

    @Override
    public String toString() {
        return "<DepreciationRow " + family + ">";
    }
}
