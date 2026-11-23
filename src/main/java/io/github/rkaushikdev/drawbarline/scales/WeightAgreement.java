package io.github.rkaushikdev.drawbarline.scales;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.clock.Term;
import io.github.rkaushikdev.drawbarline.lading.Commodity;
import io.github.rkaushikdev.drawbarline.util.Text;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * A weight agreed with a shipper instead of taken on a scale. Every car of the commodity from that
 * shipper bills at it until somebody asks for a check weigh; the car on the scale reading four
 * hundred pounds different is not an error, it is the agreement working.
 */
public record WeightAgreement(
        String shipper, Commodity commodity, Weight agreed, LocalDate effective, Optional<LocalDate> expires) {

    public WeightAgreement {
        Objects.requireNonNull(shipper, "shipper");
        Objects.requireNonNull(commodity, "commodity");
        Objects.requireNonNull(agreed, "agreed");
        Objects.requireNonNull(effective, "effective");
        Objects.requireNonNull(expires, "expires");
        if (Text.isBlank(shipper)) {
            throw new ScaleError("an agreement names a shipper");
        }
        if (agreed.pounds() < 1) {
            throw new ScaleError("an agreed weight is a weight", Details.of("shipper", shipper));
        }
        Term.checked(
                effective,
                expires,
                () -> new ScaleError("an agreement cannot expire before it starts", Details.of("shipper", shipper)));
    }

    /** An agreement with no end date. */
    public WeightAgreement(String shipper, Commodity commodity, Weight agreed, LocalDate effective) {
        this(shipper, commodity, agreed, effective, Optional.empty());
    }

    /** An agreement that runs to a last day. */
    public WeightAgreement(String shipper, Commodity commodity, Weight agreed, LocalDate effective, LocalDate expires) {
        this(shipper, commodity, agreed, effective, Optional.of(expires));
    }

    /** The days the agreement is the billing weight on. */
    public Term term() {
        return new Term(effective, expires);
    }

    public boolean covers(LocalDate day) {
        return term().covers(day);
    }

    /** Whether the agreement is the billing weight for a shipper's commodity on a day. */
    public boolean appliesTo(String shipper, Commodity commodity, LocalDate day) {
        return this.shipper.equals(shipper) && this.commodity.code().equals(commodity.code()) && covers(day);
    }

    /** {@code PWRGRN wheat at 198,000 lb}. */
    @Override
    public String toString() {
        return shipper + " " + commodity.name() + " at " + agreed;
    }
}
