package io.github.rkaushikdev.drawbarline.repairs;

import io.github.rkaushikdev.drawbarline.BillingError;
import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every exception filed against the repair cards one road sent us, and how each was answered.
 *
 * <p>The filing clock is enforced here rather than left to whoever is reading the mail. A road that
 * disagrees with an exception counters it; after that the two either settle on an amount or it
 * goes to arbitration, which is simply an outcome as far as the register is concerned.
 */
public final class ExceptionRegister {

    private static final Comparator<Objection> LINE_ORDER =
            Comparator.comparingInt(Objection::cardNumber).thenComparingInt(Objection::line);

    private final Map<LineKey, Objection> filed = new LinkedHashMap<>();
    private final Map<LineKey, Resolution> settled = new LinkedHashMap<>();

    /** One line of one card. */
    private record LineKey(int card, int line) {

        static LineKey of(Objection objection) {
            return new LineKey(objection.cardNumber(), objection.line());
        }
    }

    /**
     * File an exception against a priced card, refusing one against another card, against a line
     * the card has not got, out of time, for more than the line was billed, or against a line
     * already under exception.
     */
    public Objection file(PricedCard card, Objection objection) {
        int number = card.card().number();
        if (objection.cardNumber() != number) {
            throw new BillingError(
                    "that exception is against a different card",
                    Details.of("card", number, "against", objection.cardNumber()));
        }
        if (objection.line() >= card.lines().size()) {
            throw new BillingError("that card has no such line", Details.of("card", number, "line", objection.line()));
        }
        if (objection.filedOn().isAfter(Objection.mustFileBy(card.card().on()))) {
            throw new BillingError(
                    "that exception is out of time",
                    Details.of(
                            "card",
                            number,
                            "by",
                            Objection.mustFileBy(card.card().on()).toString()));
        }
        PricedLine line = card.lines().get(objection.line());
        if (objection.disputed().isGreaterThan(line.amount())) {
            throw new BillingError(
                    "more is disputed than the line was billed for",
                    Details.of("card", number, "line", objection.line()));
        }
        LineKey key = LineKey.of(objection);
        if (filed.containsKey(key)) {
            throw new BillingError(
                    "that line is already under exception", Details.of("card", key.card(), "line", key.line()));
        }
        filed.put(key, objection);
        return objection;
    }

    /** Settle an exception on an amount: all of it allowed, part of it, or none. */
    public Resolution settle(Objection objection, Money allowed) {
        LineKey key = requireFiled(objection);
        if (settled.containsKey(key)) {
            throw new BillingError(
                    "that exception is already settled", Details.of("card", key.card(), "line", key.line()));
        }
        Outcome outcome;
        if (allowed.isZero()) {
            outcome = Outcome.DECLINED;
        } else if (allowed.equals(objection.disputed())) {
            outcome = Outcome.ALLOWED;
        } else {
            outcome = Outcome.PART_ALLOWED;
        }
        Resolution found = new Resolution(objection, outcome, allowed);
        settled.put(key, found);
        return found;
    }

    /** Send an exception to arbitration, which settles nothing but closes our file. */
    public Resolution refer(Objection objection) {
        LineKey key = requireFiled(objection);
        Resolution found = new Resolution(objection, Outcome.ARBITRATION, Money.ZERO);
        settled.put(key, found);
        return found;
    }

    private LineKey requireFiled(Objection objection) {
        LineKey key = LineKey.of(objection);
        if (!filed.containsKey(key)) {
            throw new BillingError(
                    "no exception is on file for that line", Details.of("card", key.card(), "line", key.line()));
        }
        return key;
    }

    /** The exceptions not yet answered, in card and line order. */
    public List<Objection> openObjections() {
        return filed.entrySet().stream()
                .filter(entry -> !settled.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .sorted(LINE_ORDER)
                .toList();
    }

    /** Every answer given, in card and line order. */
    public List<Resolution> resolutions() {
        return settled.values().stream()
                .sorted(Comparator.comparing(Resolution::objection, LINE_ORDER))
                .toList();
    }

    /** Everything allowed against one card. */
    public Money allowedAgainst(int cardNumber) {
        return Money.total(settled.values().stream()
                .filter(one -> one.objection().cardNumber() == cardNumber)
                .map(Resolution::allowed)
                .toList());
    }

    /** How many exceptions have been filed. */
    public int size() {
        return filed.size();
    }
}
