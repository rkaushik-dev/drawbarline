package io.github.rkaushikdev.drawbarline.demurrage;

import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * One car on a demurrage invoice, or the whole month on an average one.
 *
 * @param car the car the line is for, absent on the single line of an average invoice
 * @param placedOn the first day the line was counted over
 * @param countedTo the last day the line was counted over
 * @param days the chargeable days billed, or the net debits on an average invoice
 */
public record Line(
        Optional<CarMark> car, String industry, LocalDate placedOn, LocalDate countedTo, int days, Money amount) {

    public Line {
        Objects.requireNonNull(car, "car");
        Objects.requireNonNull(industry, "industry");
        Objects.requireNonNull(placedOn, "placedOn");
        Objects.requireNonNull(countedTo, "countedTo");
        Objects.requireNonNull(amount, "amount");
    }

    /** A line for one car. */
    public Line(CarMark car, String industry, LocalDate placedOn, LocalDate countedTo, int days, Money amount) {
        this(Optional.of(car), industry, placedOn, countedTo, days, amount);
    }

    /** Whether any day on the line is billed. */
    public boolean charged() {
        return days > 0;
    }

    /** {@code CN 000001 ESCPAP1 3 days $400.00}, or {@code all cars ...} on an average line. */
    @Override
    public String toString() {
        String who = car.map(CarMark::toString).orElse("all cars");
        return who + " " + industry + " " + days + " days " + amount;
    }
}
