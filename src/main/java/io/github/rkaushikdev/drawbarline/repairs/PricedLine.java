package io.github.rkaushikdev.drawbarline.repairs;

import io.github.rkaushikdev.drawbarline.money.Money;
import java.util.Objects;

/** One line of a repair card with the money on it: the labor and the material. */
public record PricedLine(Defect defect, Money labor, Money material) {

    public PricedLine {
        Objects.requireNonNull(defect, "defect");
        Objects.requireNonNull(labor, "labor");
        Objects.requireNonNull(material, "material");
    }

    /** Labor and material together. */
    public Money amount() {
        return labor.plus(material);
    }

    public Responsibility answeredBy() {
        return defect.answeredBy();
    }

    /** {@code 4312 $1,385.00}. */
    @Override
    public String toString() {
        return defect.job() + " " + amount();
    }
}
