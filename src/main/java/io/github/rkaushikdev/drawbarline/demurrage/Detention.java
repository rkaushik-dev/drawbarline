package io.github.rkaushikdev.drawbarline.demurrage;

import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * One car on one track, counted out into free days and chargeable ones.
 *
 * <p>Every date here is local at the station the car stood at: see {@link Counting}.
 *
 * @param placedOn the day the car was placed, actually or constructively
 * @param clockStarts where free time begins: the first chargeable day after placement, or after the
 *     day the car was ordered for when it came early
 * @param freeUntil the last free day, absent when the tariff allows none
 * @param countedTo the day the car was released, or the day counting stopped
 * @param chargeable the chargeable days past free time
 * @param loaded whether the car was placed loaded, which decides its free time
 * @param constructive whether the placement was constructive rather than actual
 */
public record Detention(
        CarMark car,
        String industry,
        LocalDate placedOn,
        LocalDate clockStarts,
        Optional<LocalDate> freeUntil,
        LocalDate countedTo,
        int chargeable,
        boolean loaded,
        boolean constructive) {

    public Detention {
        Objects.requireNonNull(car, "car");
        Objects.requireNonNull(industry, "industry");
        Objects.requireNonNull(placedOn, "placedOn");
        Objects.requireNonNull(clockStarts, "clockStarts");
        Objects.requireNonNull(freeUntil, "freeUntil");
        Objects.requireNonNull(countedTo, "countedTo");
    }

    /** Whether the car came back inside free time, so nothing is owed. */
    public boolean stillFree() {
        return chargeable == 0;
    }

    /** What the chargeable days come to, each at its own step of the tariff. */
    public Money charge(Tariff tariff) {
        return tariff.charge(chargeable);
    }

    /** The same detention counted again from a later clock start, which is what relief does. */
    public Detention recounted(LocalDate clockStarts, Optional<LocalDate> freeUntil, int chargeable) {
        return new Detention(
                car, industry, placedOn, clockStarts, freeUntil, countedTo, chargeable, loaded, constructive);
    }

    /** {@code CN 400100 at ESCPAP1 3 chargeable days}, or {@code ... inside free time}. */
    @Override
    public String toString() {
        if (stillFree()) {
            return car + " at " + industry + " inside free time";
        }
        return car + " at " + industry + " " + chargeable + " chargeable days";
    }
}
