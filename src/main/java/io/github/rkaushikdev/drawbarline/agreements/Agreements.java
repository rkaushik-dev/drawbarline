package io.github.rkaushikdev.drawbarline.agreements;

import io.github.rkaushikdev.drawbarline.fleet.CarKind;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * What each connection will take, and when.
 *
 * <p>An interchange runs under an agreement, and handing a road a car outside it is not an error
 * the system stops: it is a car standing at a junction overnight earning car hire. The daily cap
 * is the one that bites. A connection that takes forty cars a day takes the forty first tomorrow,
 * and that car has to be picked before the train is made up rather than after it arrives. So the
 * question a yardmaster asks is not only whether the car goes now but when it will: see {@link
 * #nextTaken}.
 */
public final class Agreements {

    private Agreements() {}

    /** Every reason the connection will not take an ordinary car right now, the first of the day. */
    public static List<String> checkDelivery(InterchangeAgreement agreement, CarKind kind, LocalDateTime local) {
        return checkDelivery(agreement, kind, local, false, 0);
    }

    /** Every reason the connection will not take this car right now. */
    public static List<String> checkDelivery(
            InterchangeAgreement agreement, CarKind kind, LocalDateTime local, boolean hazardous, int alreadyToday) {
        return agreement.refusals(kind, local, hazardous, alreadyToday);
    }

    /** Whether the connection takes an ordinary car right now, the first of the day. */
    public static boolean willTake(InterchangeAgreement agreement, CarKind kind, LocalDateTime local) {
        return checkDelivery(agreement, kind, local).isEmpty();
    }

    /** Whether the connection takes this car right now. */
    public static boolean willTake(
            InterchangeAgreement agreement, CarKind kind, LocalDateTime local, boolean hazardous, int alreadyToday) {
        return checkDelivery(agreement, kind, local, hazardous, alreadyToday).isEmpty();
    }

    /** How many more cars the connection will take today, never fewer than none. */
    public static int roomLeft(InterchangeAgreement agreement, int alreadyToday) {
        return agreement.roomLeft(alreadyToday);
    }

    /**
     * The first moment, local at the junction, the connection will take this car: now if it would
     * take it now, the opening this morning if it is early, or the opening tomorrow once today's
     * cap is used or the junction has shut, when the agreement is still in force then. Nothing when
     * the agreement never takes this car at all.
     *
     * @param alreadyToday the cars already gone across today, which tomorrow starts again from none
     */
    public static Optional<LocalDateTime> nextTaken(
            InterchangeAgreement agreement, CarKind kind, LocalDateTime local, boolean hazardous, int alreadyToday) {
        if (!agreement.takesKind(kind) || (hazardous && !agreement.hazardous())) {
            return Optional.empty();
        }
        if (agreement.refusals(kind, local, hazardous, alreadyToday).isEmpty()) {
            return Optional.of(local);
        }
        LocalDateTime opening = local.toLocalDate().atTime(agreement.opens());
        if (local.isBefore(opening)
                && agreement.refusals(kind, opening, hazardous, alreadyToday).isEmpty()) {
            return Optional.of(opening);
        }
        LocalDateTime tomorrow = opening.plusDays(1);
        if (agreement.refusals(kind, tomorrow, hazardous, 0).isEmpty()) {
            return Optional.of(tomorrow);
        }
        return Optional.empty();
    }
}
