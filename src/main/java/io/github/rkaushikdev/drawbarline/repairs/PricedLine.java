package io.github.rkaushikdev.drawbarline.repairs;

import io.github.rkaushikdev.drawbarline.BillingError;
import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.util.Objects;

/**
 * One line of a repair card with the money on it: the labor, the material, and the scrap credit for
 * the part that came off.
 *
 * @param scrap what the removed parts are worth as scrap, credited to the owner billed for the line
 */
public record PricedLine(Defect defect, Money labor, Money material, Money scrap) {

    public PricedLine {
        Objects.requireNonNull(defect, "defect");
        Objects.requireNonNull(labor, "labor");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(scrap, "scrap");
        if (scrap.isGreaterThan(material)) {
            throw new BillingError(
                    "the scrap credit is more than the material it came off",
                    Details.of("job", defect.job().toString()));
        }
    }

    /** A line with no scrap credit on it. */
    public PricedLine(Defect defect, Money labor, Money material) {
        this(defect, labor, material, Money.ZERO);
    }

    /** Labor and material together, less the scrap credit. */
    public Money amount() {
        return labor.plus(material).minus(scrap);
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
