package io.github.rkaushikdev.drawbarline.persistence.scratch;

import io.github.rkaushikdev.drawbarline.fleet.CarTypeCode;
import io.github.rkaushikdev.drawbarline.fleet.MechanicalDesignation;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.measure.Length;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A table that exists only to exercise the column types. It lives in a package of its own, outside
 * the mapped packages, so it is never part of the books and never compared with the migrations.
 */
@Entity
@Table(name = "sample_of_every_type")
public class SampleOfEveryType {

    @Id
    @Column(name = "id")
    public int id;

    @Column(name = "amount")
    public Money amount;

    @Column(name = "weight")
    public Weight weight;

    @Column(name = "length")
    public Length length;

    @Column(name = "road", length = 4)
    public ReportingMark road;

    @Column(name = "car", length = 11)
    public CarMark car;

    @Column(name = "type_code", length = 4)
    public CarTypeCode typeCode;

    @Column(name = "designation", length = 4)
    public MechanicalDesignation designation;

    @Column(name = "moment")
    public Instant moment;

    public SampleOfEveryType() {}

    public SampleOfEveryType(int id) {
        this.id = id;
    }
}
