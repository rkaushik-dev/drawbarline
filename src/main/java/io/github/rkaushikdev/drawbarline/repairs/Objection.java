package io.github.rkaushikdev.drawbarline.repairs;

import io.github.rkaushikdev.drawbarline.BillingError;
import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.LocalDate;
import java.util.Objects;

/**
 * An owner's exception to one line of one repair card, for one reason.
 *
 * <p>Not against the whole card, against a line, because half a card being right is the normal
 * case. The exception says which line, what is wrong with it, and how much of the amount the owner
 * refuses to pay. There is a clock on it: see {@link #mustFileBy}.
 *
 * @param line the line of the card, counted from zero
 * @param disputed how much of the line the owner refuses to pay
 */
public record Objection(int cardNumber, int line, Ground ground, Money disputed, LocalDate filedOn, String note) {

    /** How many days after the card an exception may still be filed. */
    public static final int FILING_DAYS = 90;

    public Objection {
        Objects.requireNonNull(ground, "ground");
        Objects.requireNonNull(disputed, "disputed");
        Objects.requireNonNull(filedOn, "filedOn");
        Objects.requireNonNull(note, "note");
        if (line < 0) {
            throw new BillingError("a line is numbered from zero", Details.of("line", line));
        }
        if (disputed.cents() <= 0) {
            throw new BillingError("an exception disputes an amount", Details.of("card", cardNumber, "line", line));
        }
    }

    /** An exception with nothing written beside it. */
    public Objection(int cardNumber, int line, Ground ground, Money disputed, LocalDate filedOn) {
        this(cardNumber, line, ground, disputed, filedOn, "");
    }

    /**
     * The last day an exception to a card written on {@code cardOn} is in time. A repair billed in
     * March and disputed in December cannot be investigated by anybody who was there, so an
     * exception filed later is out of time and the owner pays whatever is on the card.
     */
    public static LocalDate mustFileBy(LocalDate cardOn) {
        return cardOn.plusDays(FILING_DAYS);
    }

    /** {@code card 88104 line 0 depreciation not allowed for $200.00}. */
    @Override
    public String toString() {
        return "card " + cardNumber + " line " + line + " " + ground.text() + " " + disputed;
    }
}
