package io.github.rkaushikdev.drawbarline.demurrage;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.TariffError;
import io.github.rkaushikdev.drawbarline.customers.Industry;
import io.github.rkaushikdev.drawbarline.days.Days;
import io.github.rkaushikdev.drawbarline.days.TariffCalendar;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Relief a customer is owed when the bunching was our fault: how many days a car's clock is
 * pushed back, and why.
 *
 * <p>A shipper orders four cars a day. We hold them at Powers for a week because we are short of
 * power, then set out twenty eight on a Tuesday. Every one of those cars starts its clock the same
 * morning, the shipper can only work four a day, and by Friday they are paying demurrage on cars
 * they could not physically have touched. The relief for that is old and settled: count each car
 * as though it had arrived on the day it would have arrived had we delivered evenly.
 *
 * <p>The relief is per track and per day, because a customer with two tracks works them
 * independently and at their own rates: see {@link Bunching}.
 *
 * <p>Constructive placement is the other half. A car we could not place because the track was full
 * is still on the customer's time, but only if the track really was full; if it was not, we simply
 * failed to deliver, and the clock has no business running.
 */
public record Relief(CarMark car, String industry, int days, String reason) {

    /** The reason most relief is given for. */
    public static final String BUNCHING = "bunching";

    public Relief {
        Objects.requireNonNull(car, "car");
        Objects.requireNonNull(industry, "industry");
        Objects.requireNonNull(reason, "reason");
    }

    /** Relief for bunching. */
    public Relief(CarMark car, String industry, int days) {
        this(car, industry, days, BUNCHING);
    }

    /** {@code CN 000001 2 days relief for bunching}. */
    @Override
    public String toString() {
        return car + " " + days + " days relief for " + reason;
    }

    /**
     * Recount a detention as though its clock had started {@code daysLater} chargeable days later.
     * No relief hands back the very same detention.
     */
    public static Detention recount(Detention detention, Tariff tariff, int daysLater) {
        if (daysLater < 0) {
            throw new TariffError("relief does not run backwards", Details.of("days", daysLater));
        }
        if (daysLater == 0) {
            return detention;
        }
        LocalDate clock = Days.addChargeableDays(detention.clockStarts(), daysLater);
        Optional<LocalDate> freeUntil = Counting.freeTimeEnds(clock, tariff.freeDays(detention.loaded()));
        int charged = Counting.chargedDays(
                Counting.firstCharged(clock, freeUntil, TariffCalendar.STANDARD),
                detention.countedTo(),
                TariffCalendar.STANDARD);
        return detention.recounted(clock, freeUntil, charged);
    }

    /**
     * How far back each car has to be pushed for the delivery to read as even, every track worked
     * at the same rate; see {@link Bunching} for tracks worked at their own.
     */
    public static List<Relief> bunching(List<Detention> detentions, int perDay) {
        return Bunching.flat(perDay).reliefs(detentions);
    }

    /** Recount a set of detentions with bunching relief applied, in the order they were given. */
    public static List<Detention> relieve(List<Detention> detentions, Tariff tariff, int perDay) {
        return Bunching.flat(perDay).relieve(detentions, tariff);
    }

    /** Whether a constructive placement was really the customer holding us out: the track was full. */
    public static boolean constructiveStands(Industry industry, int carsOnHand) {
        return industry.full(carsOnHand);
    }

    /**
     * Refuse a constructive placement onto a track that had room. An actual placement, or a
     * constructive one that stands, is handed back as it is.
     */
    public static Detention checkConstructive(Detention detention, Industry industry, int carsOnHand) {
        if (!detention.constructive()) {
            return detention;
        }
        if (!constructiveStands(industry, carsOnHand)) {
            throw new TariffError(
                    "the track had room, so nothing was constructively placed",
                    Details.of("car", detention.car().toString(), "industry", industry.code(), "on_hand", carsOnHand));
        }
        return detention;
    }
}
