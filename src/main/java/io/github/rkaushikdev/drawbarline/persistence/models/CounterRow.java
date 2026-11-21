package io.github.rkaushikdev.drawbarline.persistence.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Check;

/**
 * Where one series of document numbers has got to.
 *
 * <p>An invoice number is not decoration: a customer files invoices in number order and a gap is a
 * question somebody has to answer. So the counters are gapless, a number handed out inside the
 * transaction that uses it and given back if that transaction rolls away. This is the one row in
 * the schema updated in place, because the alternative, reading the highest number issued, races
 * two clerks into the same invoice number.
 */
@Entity
@Table(name = "counter")
@Check(name = "ck_counter_numbered_from_one", constraints = "next_number >= 1")
public class CounterRow {

    @Id
    @Column(name = "name", length = 24, nullable = false)
    private String name;

    @Column(name = "next_number", nullable = false)
    private int nextNumber;

    protected CounterRow() {}

    public CounterRow(String name, int nextNumber) {
        this.name = name;
        this.nextNumber = nextNumber;
    }

    public String getName() {
        return name;
    }

    public int getNextNumber() {
        return nextNumber;
    }

    @Override
    public String toString() {
        return "<CounterRow " + name + " next " + nextNumber + ">";
    }
}
