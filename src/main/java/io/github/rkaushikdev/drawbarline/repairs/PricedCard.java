package io.github.rkaushikdev.drawbarline.repairs;

import io.github.rkaushikdev.drawbarline.money.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A repair card with every line priced and split by who answers for it.
 *
 * <p>The price comes out of the master; the responsibility comes off the why made code, or off an
 * inspector who overrode it. The owner is only ever billed for wear and joint work, and the road
 * doing the work bears the rest whether it likes it or not.
 */
public record PricedCard(RepairCard card, List<PricedLine> lines) {

    public PricedCard {
        Objects.requireNonNull(card, "card");
        lines = List.copyOf(lines);
    }

    /**
     * Put money on every line of a card: the master's hours at the performing road's labor rate,
     * and the material at the master's price less any depreciation, each times the quantity.
     */
    public static PricedCard price(RepairCard card, PriceMaster master, LaborRates rates) {
        LaborRate rate = rates.rateFor(card.performedBy(), card.on());
        List<PricedLine> lines = new ArrayList<>();
        for (Defect defect : card.defects()) {
            PriceLine line = master.lineFor(defect.job(), card.on());
            Money labor = rate.costOf(line.laborHundredths() * defect.quantity());
            Money material = master.materialFor(defect.job(), card.on(), defect.partYears());
            lines.add(new PricedLine(defect, labor, material.times(defect.quantity())));
        }
        return new PricedCard(card, lines);
    }

    /** Every line together. */
    public Money total() {
        return Money.total(lines.stream().map(PricedLine::amount).toList());
    }

    /** What one party answers for. */
    public Money owedBy(Responsibility who) {
        return Money.total(lines.stream()
                .filter(line -> line.answeredBy() == who)
                .map(PricedLine::amount)
                .toList());
    }

    /** What actually goes on the owner's bill, which is wear and joint work. */
    public Money billedToOwner() {
        return Money.total(lines.stream()
                .filter(line -> line.answeredBy().billableToOwner())
                .map(PricedLine::amount)
                .toList());
    }

    /** What the road that did the work bears itself. */
    public Money borneByTheRoad() {
        return total().minus(billedToOwner());
    }

    /** {@code card 88104 DBL 104207 by CN on 2025-01-14 $1,385.00 of which $1,385.00 to the owner}. */
    @Override
    public String toString() {
        return card + " " + total() + " of which " + billedToOwner() + " to the owner";
    }
}
