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
 * doing the work bears the rest whether it likes it or not. Where the owner is billed for a new
 * part, the old one that came off is the repairing road's scrap, and the owner is credited what it
 * is worth.
 */
public record PricedCard(RepairCard card, List<PricedLine> lines) {

    public PricedCard {
        Objects.requireNonNull(card, "card");
        lines = List.copyOf(lines);
    }

    /**
     * Put money on every line of a card: the master's hours at the performing road's labor rate,
     * the material at the master's price less any depreciation, and on a line the owner is billed
     * for, the scrap credit the family of the part carries, each times the quantity.
     */
    public static PricedCard price(RepairCard card, PriceMaster master, LaborRates rates) {
        LaborRate rate = rates.rateFor(card.performedBy(), card.on());
        List<PricedLine> lines = new ArrayList<>();
        for (Defect defect : card.defects()) {
            lines.add(priceLine(defect, card, master, rate));
        }
        return new PricedCard(card, lines);
    }

    private static PricedLine priceLine(Defect defect, RepairCard card, PriceMaster master, LaborRate rate) {
        int parts = defect.quantity();
        Money labor = rate.costOf(master.lineFor(defect.job(), card.on()).laborHundredths() * parts);
        Money material =
                master.materialFor(defect.job(), card.on(), defect.partYears()).times(parts);
        Money scrap = defect.answeredBy().billableToOwner()
                ? master.scrapFor(defect.job()).times(parts)
                : Money.ZERO;
        // A part worn nearly to its floor can be worth more as scrap than is left to bill for it;
        // the credit takes the material to nothing and no further.
        return new PricedLine(defect, labor, material, scrap.isGreaterThan(material) ? material : scrap);
    }

    /** Every scrap credit the owner is given on the card. */
    public Money scrapCredited() {
        return Money.total(lines.stream().map(PricedLine::scrap).toList());
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
