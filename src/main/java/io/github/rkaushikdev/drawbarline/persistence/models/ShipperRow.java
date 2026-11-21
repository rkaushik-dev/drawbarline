package io.github.rkaushikdev.drawbarline.persistence.models;

import io.github.rkaushikdev.drawbarline.limits.Limits;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A party we bill demurrage to, keyed on its code. The private track its cars are placed on hangs
 * off it, and deleting a shipper takes its industries with it.
 */
@Entity
@Table(name = "shipper")
public class ShipperRow {

    @Id
    @Column(name = "code", length = Limits.SHIPPER_CODE, nullable = false)
    private String code;

    @Column(name = "name", length = Limits.PARTY_NAME, nullable = false)
    private String name;

    @Column(name = "average_agreement", nullable = false)
    private boolean averageAgreement;

    @OneToMany(mappedBy = "shipper", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("code")
    private List<IndustryRow> industries = new ArrayList<>();

    protected ShipperRow() {}

    public ShipperRow(String code, String name, boolean averageAgreement) {
        this.code = code;
        this.name = name;
        this.averageAgreement = averageAgreement;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    /** Whether the shipper is billed on a monthly balance rather than car by car. */
    public boolean isAverageAgreement() {
        return averageAgreement;
    }

    /** The industries on file for this shipper, in code order. */
    public List<IndustryRow> getIndustries() {
        return Collections.unmodifiableList(industries);
    }

    /** Put an industry under this shipper, which is what writes its shipper code. */
    public IndustryRow addIndustry(IndustryRow industry) {
        industry.placeUnder(this);
        industries.add(industry);
        return industry;
    }

    @Override
    public String toString() {
        return "<ShipperRow " + code + ">";
    }
}
