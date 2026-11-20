package io.github.rkaushikdev.drawbarline.repairs;

import io.github.rkaushikdev.drawbarline.BillingError;
import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.money.Money;

/**
 * How fast a family of parts is used up.
 *
 * <p>An owner whose forty year old wheelset is replaced does not pay for a new wheelset, because
 * they did not have a new one. They pay the new price less what the old one had already used up,
 * at a published percentage a year with a floor. The floor matters: without it a sufficiently old
 * part would be free and every road would wait for the owner's cars to get old before repairing
 * anything.
 *
 * @param family the first two digits of the job codes the rule covers
 */
public record Depreciation(String family, int percentAYear, int floorPercent) {

    public static final int PERCENT = 100;

    public Depreciation {
        if (percentAYear < 0 || percentAYear > PERCENT) {
            throw new BillingError("depreciation is a percentage", Details.of("family", family));
        }
        if (floorPercent < 0 || floorPercent > PERCENT) {
            throw new BillingError("the floor is a percentage", Details.of("family", family));
        }
    }

    /** What share of the new price the owner still pays after so many years. */
    public int payablePercent(int years) {
        if (years < 0) {
            throw new BillingError("a part has no negative age", Details.of("family", family));
        }
        long taken = Math.min((long) percentAYear * years, PERCENT - floorPercent);
        return (int) (PERCENT - taken);
    }

    /** The material price with the depreciation taken off, to the nearest cent, a tie going to the even one. */
    public Money applied(Money material, int years) {
        return new Money((long) Math.rint(material.cents() * payablePercent(years) / (double) PERCENT));
    }
}
