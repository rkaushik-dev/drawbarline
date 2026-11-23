package io.github.rkaushikdev.drawbarline.demurrage;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.TariffError;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * A month of demurrage for one shipper: what goes out to the customer at the end of the month.
 *
 * <p>One line a car, the days it was held, and what those days came to, with the cars that came
 * back inside free time counted but not charged. The free ones are on the invoice on purpose: a
 * customer who cannot see the cars they turned quickly has no way to tell whether the ones they
 * are billed for are unusual.
 *
 * <p>A shipper on an average agreement gets a different document: one line with the net debits
 * and the net amount, and a second for the days cars ran past the offset, priced at the tariff.
 * Mixing per car lines into it produces a total nobody can check, because the per car amounts
 * under an agreement do not add up to the amount billed and are not supposed to.
 *
 * @param monthOf the first day of the month the invoice covers
 * @param average whether this is the invoice of a shipper on an average agreement
 */
public record Invoice(int number, String shipper, LocalDate monthOf, List<Line> lines, boolean average) {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("uuuu-MM", Locale.ROOT);

    public Invoice {
        Objects.requireNonNull(shipper, "shipper");
        Objects.requireNonNull(monthOf, "monthOf");
        lines = List.copyOf(lines);
        if (number < 1) {
            throw new TariffError("an invoice is numbered from one", Details.of("number", number));
        }
        if (Text.isBlank(shipper)) {
            throw new TariffError("an invoice names a shipper", Details.of("number", number));
        }
        if (monthOf.getDayOfMonth() != 1) {
            throw new TariffError("an invoice covers a whole month", Details.of("month_of", monthOf.toString()));
        }
    }

    /** A per car invoice. */
    public Invoice(int number, String shipper, LocalDate monthOf, List<Line> lines) {
        this(number, shipper, monthOf, lines, false);
    }

    public Money total() {
        return Money.total(lines.stream().map(Line::amount).toList());
    }

    /** Every day billed, which on an average invoice is the net debits. */
    public int days() {
        return lines.stream().mapToInt(Line::days).sum();
    }

    /** How many lines, which on a per car invoice is how many cars. */
    public int cars() {
        return lines.size();
    }

    public int chargedCars() {
        return (int) lines.stream().filter(Line::charged).count();
    }

    /** Cars counted on the invoice that came back inside free time. */
    public int freeCars() {
        return cars() - chargedCars();
    }

    /** Whether nothing is owed on it. */
    public boolean isEmpty() {
        return total().isZero();
    }

    /** The line for one car, if the car is on the invoice. */
    public Optional<Line> forCar(CarMark car) {
        return lines.stream()
                .filter(line -> line.car().equals(Optional.of(car)))
                .findFirst();
    }

    /** {@code demurrage invoice 4101 ESCPAP 2025-01 $400.00}, or {@code average invoice ...}. */
    @Override
    public String toString() {
        String kind = average ? "average" : "demurrage";
        return kind + " invoice " + number + " " + shipper + " " + MONTH.format(monthOf) + " " + total();
    }

    /** The line one detention makes on a per car invoice, priced up the tariff steps. */
    public static Line lineFor(Detention detention, Tariff tariff) {
        return new Line(
                detention.car(),
                detention.industry(),
                detention.placedOn(),
                detention.countedTo(),
                detention.chargeable(),
                detention.charge(tariff));
    }

    /** Cut a per car invoice, free cars and all, in car order. */
    public static Invoice assemble(
            int number, String shipper, LocalDate monthOf, List<Detention> detentions, Tariff tariff) {
        List<Line> lines = detentions.stream()
                .sorted(Comparator.comparing(one -> one.car().toString()))
                .map(one -> lineFor(one, tariff))
                .toList();
        return new Invoice(number, shipper, monthOf, lines);
    }

    /**
     * Cut the invoice a shipper on an average agreement gets: one line for the net debits, and a
     * second for the late days at the tariff when any car ran past the offset.
     */
    public static Invoice assembleAverage(int number, Settlement settlement, LocalDate monthOf) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(
                Optional.empty(), "average agreement", monthOf, monthOf, settlement.netDebits(), settlement.amount()));
        if (settlement.late() != 0) {
            lines.add(new Line(
                    Optional.empty(), "past the offset", monthOf, monthOf, settlement.late(), settlement.lateAmount()));
        }
        return new Invoice(number, settlement.shipper(), monthOf, lines, true);
    }
}
