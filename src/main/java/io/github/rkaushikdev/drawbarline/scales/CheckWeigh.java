package io.github.rkaushikdev.drawbarline.scales;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.lading.Commodity;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.util.Text;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One car out of a shipper's agreed traffic put across a certified scale to see whether the agreed
 * weight still holds.
 *
 * @param net the lading the scale found, the car's light weight already taken off
 */
public record CheckWeigh(String shipper, Commodity commodity, CarMark car, LocalDate on, Weight net) {

    public CheckWeigh {
        Objects.requireNonNull(commodity, "commodity");
        Objects.requireNonNull(car, "car");
        Objects.requireNonNull(on, "on");
        Objects.requireNonNull(net, "net");
        if (Text.isBlank(shipper)) {
            throw new ScaleError("a check weigh names the shipper", Details.of("car", car.toString()));
        }
        if (net.pounds() < 1) {
            throw new ScaleError("a check weigh finds some lading", Details.of("car", car.toString()));
        }
    }

    /** How far the scale and the agreement disagree, in tenths of a percent of the agreed weight. */
    public long varianceTenthsPercent(WeightAgreement agreement) {
        long apart = Math.abs(net.pounds() - agreement.agreed().pounds());
        return Math.floorDiv(
                apart * Scales.TENTHS_OF_A_PERCENT, agreement.agreed().pounds());
    }

    /** Whether this weigh was taken against the agreement: same shipper, same commodity, inside its term. */
    public boolean against(WeightAgreement agreement) {
        return agreement.appliesTo(shipper, commodity, on);
    }

    /** {@code CN 400100 PWRGRN wheat 201,300 lb on 2025-02-03}. */
    @Override
    public String toString() {
        return car + " " + shipper + " " + commodity.name() + " " + net + " on " + on;
    }
}
