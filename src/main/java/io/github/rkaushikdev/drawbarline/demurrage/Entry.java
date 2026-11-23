package io.github.rkaushikdev.drawbarline.demurrage;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.TariffError;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.util.Objects;

/**
 * What one car did to a month's balance under an average agreement: a credit for a car turned back
 * inside the first free day, or a debit for each of its first {@link AverageAgreement#OFFSET_DAYS}
 * chargeable days, never both. A car held longer than that owes its later days at the tariff, and
 * credits do not touch them.
 *
 * @param late the chargeable days past the ones an agreement offsets
 */
public record Entry(CarMark car, String industry, int credits, int debits, int late) {

    public Entry {
        Objects.requireNonNull(car, "car");
        Objects.requireNonNull(industry, "industry");
        if (credits != 0 && (debits != 0 || late != 0)) {
            throw new TariffError("a car earns a credit or owes debits, never both", Details.of("car", car.toString()));
        }
        if (late != 0 && debits < AverageAgreement.OFFSET_DAYS) {
            throw new TariffError(
                    "a car owes late days only once it has owed every offset debit", Details.of("car", car.toString()));
        }
    }

    /** An entry with no days past the offset. */
    public Entry(CarMark car, String industry, int credits, int debits) {
        this(car, industry, credits, debits, 0);
    }

    /**
     * Read one detention as a credit or as debits. A car with chargeable days owes one debit for
     * each up to the offset and a late day for each after it; one released on or before the day its
     * clock started earns a credit; anything between earns nothing.
     */
    public static Entry of(Detention detention) {
        int chargeable = detention.chargeable();
        if (chargeable != 0) {
            int offset = Math.min(chargeable, AverageAgreement.OFFSET_DAYS);
            return new Entry(detention.car(), detention.industry(), 0, offset, chargeable - offset);
        }
        int earned = detention.countedTo().isAfter(detention.clockStarts()) ? 0 : 1;
        return new Entry(detention.car(), detention.industry(), earned, 0);
    }

    /** What the late days come to at the tariff, priced on from the step after the offset. */
    public Money lateCharge(Tariff tariff) {
        return tariff.charge(debits + late).minus(tariff.charge(debits));
    }

    /** {@code CN 400100 1 credit}, {@code CN 400100 3 debits} or {@code CN 400100 4 debits 2 late}. */
    @Override
    public String toString() {
        if (credits != 0) {
            return car + " " + credits + " credit";
        }
        return car + " " + debits + " debits" + (late == 0 ? "" : " " + late + " late");
    }
}
