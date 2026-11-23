package io.github.rkaushikdev.drawbarline.demurrage;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.TariffError;
import io.github.rkaushikdev.drawbarline.clock.Term;
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
 * flat rate rather than up the tariff steps. The protection stops at {@link #OFFSET_DAYS}: a car
 * kept longer than that is not being worked on an average, and its later days go out at the tariff
 * with no credit set against them. The credits offset that month and no other; see {@link
 * AverageLedger}.
 *
 * @param shipper the shipper's code
 * @param dailyCents what one net debit costs
 * @param expires the last day the agreement covers, absent while it stands
 */
public record AverageAgreement(String shipper, long dailyCents, LocalDate effective, Optional<LocalDate> expires) {

    /** How many chargeable days of one car the credits may be set against. */
    public static final int OFFSET_DAYS = 4;

    public AverageAgreement {
        Objects.requireNonNull(shipper, "shipper");
        Objects.requireNonNull(effective, "effective");
        Objects.requireNonNull(expires, "expires");
        if (dailyCents < 0) {
            throw new TariffError("a debit does not pay the customer", Details.of("cents", dailyCents));
        }
        Term.checked(
                effective,
                expires,
                () -> new TariffError(
                        "an agreement cannot expire before it starts",
                        Details.of("shipper", shipper, "effective", effective.toString())));
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
        return term().covers(day);
    }

    /** The days the agreement is in force. */
    public Term term() {
        return new Term(effective, expires);
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
