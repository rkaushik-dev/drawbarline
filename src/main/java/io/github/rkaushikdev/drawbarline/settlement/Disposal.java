package io.github.rkaushikdev.drawbarline.settlement;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.SettlementError;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.util.Objects;

/**
 * How a dispute was closed: settled, with the part of it we conceded, or withdrawn by the road.
 *
 * <p>Most disputes settle in the middle. The road said four hundred and fifty dollars of hours were
 * not theirs, the records showed they were right about a hundred and twenty of it, and that is the
 * figure that goes on next month's statement as a credit. The rest the road pays.
 *
 * @param conceded what we gave back, from nothing up to the whole disputed amount
 */
public record Disposal(Standing standing, Money conceded) {

    public Disposal {
        Objects.requireNonNull(standing, "standing");
        Objects.requireNonNull(conceded, "conceded");
        if (standing == Standing.OPEN) {
            throw new SettlementError("an open dispute has not been disposed of");
        }
        if (conceded.cents() < 0) {
            throw new SettlementError("nothing is conceded below nothing", Details.of("cents", conceded.cents()));
        }
        if (standing == Standing.WITHDRAWN && !conceded.isZero()) {
            throw new SettlementError("a withdrawn dispute concedes nothing");
        }
    }

    /** The road took the dispute back and pays it all. */
    public static Disposal withdrawn() {
        return new Disposal(Standing.WITHDRAWN, Money.ZERO);
    }

    /** Settled with so much conceded. */
    public static Disposal settled(Money conceded) {
        return new Disposal(Standing.SETTLED, conceded);
    }
}
