package io.github.rkaushikdev.drawbarline.storage;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.TariffError;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.time.LocalDate;
import java.util.Objects;

/**
 * When a car stops being late and starts being stored: after a stated number of chargeable days
 * the car comes off the demurrage schedule and onto a flat daily storage rate.
 *
 * @param afterDays the chargeable days billed as demurrage before storage starts
 * @param dailyCents what each day of storage costs, every day counted
 */
public record StorageTariff(String code, LocalDate effective, int afterDays, long dailyCents) {

    public StorageTariff {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(effective, "effective");
        if (Text.isBlank(code)) {
            throw new TariffError("storage is published under a number");
        }
        if (afterDays < 1) {
            throw new TariffError("storage starts after at least one chargeable day", Details.of("code", code));
        }
        if (dailyCents < 0) {
            throw new TariffError("storage does not pay the customer", Details.of("code", code));
        }
    }

    /** What one day of storage costs. */
    public Money daily() {
        return new Money(dailyCents);
    }

    /** {@code storage 4500 at $35.00 a day after 5 days}. */
    @Override
    public String toString() {
        return "storage " + code + " at " + daily() + " a day after " + afterDays + " days";
    }
}
