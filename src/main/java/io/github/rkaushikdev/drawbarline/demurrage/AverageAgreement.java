package io.github.rkaushikdev.drawbarline.demurrage;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.TariffError;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * A shipper's standing average agreement, and what a net debit costs under it.
 *
 * <p>A shipper who moves cars in volume can ask to be billed on an average instead of car by car.
 * Every car they turn back inside the first free day earns a credit, every chargeable day earns a
 * debit, and at the end of the month only the debits left over are billed, at the agreement's own
 * flat rate rather than up the tariff steps: the customer trades the cheap early days away for
 * protection against the expensive late ones. The credits offset that month and no other; see
 * {@link AverageLedger}.
 *
 * @param shipper the shipper's code
 * @param dailyCents what one net debit costs
 * @param expires the last day the agreement covers, absent while it stands
 */
public record AverageAgreement(String shipper, long dailyCents, LocalDate effective, Optional<LocalDate> expires) {

    public AverageAgreement {
        Objects.requireNonNull(shipper, "shipper");
        Objects.requireNonNull(effective, "effective");
        Objects.requireNonNull(expires, "expires");
        if (dailyCents < 0) {
            throw new TariffError("a debit does not pay the customer", Details.of("cents", dailyCents));
        }
        if (expires.isPresent() && expires.get().isBefore(effective)) {
            throw new TariffError(
                    "an agreement cannot expire before it starts",
                    Details.of("shipper", shipper, "effective", effective.toString()));
        }
    }

    /** An agreement that stands until somebody ends it. */
    public AverageAgreement(String shipper, long dailyCents, LocalDate effective) {
        this(shipper, dailyCents, effective, Optional.empty());
    }

    /** An agreement that ends on a known day. */
    public AverageAgreement(String shipper, long dailyCents, LocalDate effective, LocalDate expires) {
        this(shipper, dailyCents, effective, Optional.of(expires));
    }

    /** Whether the agreement was in force on a day. */
    public boolean covers(LocalDate day) {
        if (day.isBefore(effective)) {
            return false;
        }
        return expires.map(last -> !day.isAfter(last)).orElse(true);
    }

    /** What one net debit costs. */
    public Money daily() {
        return new Money(dailyCents);
    }

    /** {@code PWRGRN at $120.00 a debit from 2024-07-01}. */
    @Override
    public String toString() {
        return shipper + " at " + daily() + " a debit from " + effective;
    }
}
