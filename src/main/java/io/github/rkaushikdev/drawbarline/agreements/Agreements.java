package io.github.rkaushikdev.drawbarline.agreements;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.fleet.CarKind;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * What each connection will take, and when.
 *
 * <p>An interchange runs under an agreement, and handing a road a car outside it is not an error
 * the system stops: it is a car standing at a junction overnight earning car hire. The daily cap
 * is the one that bites. A connection that takes forty cars a day takes the forty first tomorrow,
 * and that car has to be picked before the train is made up rather than after it arrives.
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
        if (alreadyToday < 0) {
            throw new AgreementError("a count of cars is not negative", Details.of("given", alreadyToday));
        }
        List<String> found = new ArrayList<>();
        if (!agreement.inForceOn(local.toLocalDate())) {
            found.add(agreement.code() + " has no agreement in force");
        }
        if (!agreement.takesKind(kind)) {
            found.add(agreement.code() + " does not take a " + kind.description());
        }
        if (hazardous && !agreement.hazardous()) {
            found.add(agreement.code() + " does not take hazardous material");
        }
        if (!agreement.mannedAt(local)) {
            found.add(agreement.code() + " is closed at " + InterchangeAgreement.CLOCK.format(local)
                    + ", it is manned " + InterchangeAgreement.CLOCK.format(agreement.opens()) + " to "
                    + InterchangeAgreement.CLOCK.format(agreement.closes()));
        }
        if (alreadyToday >= agreement.dailyCap()) {
            found.add(agreement.code() + " has taken its " + agreement.dailyCap() + " cars for the day");
        }
        return List.copyOf(found);
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
        if (alreadyToday < 0) {
            throw new AgreementError("a count of cars is not negative", Details.of("given", alreadyToday));
        }
        return Math.max(agreement.dailyCap() - alreadyToday, 0);
    }
}
