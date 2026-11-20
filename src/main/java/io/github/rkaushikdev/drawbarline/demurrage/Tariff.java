package io.github.rkaushikdev.drawbarline.demurrage;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.TariffError;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * One published demurrage schedule, in force from a date.
 *
 * <p>A tariff says three things: how many chargeable days a customer gets free, how much each day
 * after that costs, and that the price goes up the longer they keep the car. The last part is the
 * point. Demurrage is not rent, it is a lever, and a flat rate is a lever nobody feels.
 *
 * <p>Free time is quoted in chargeable days, and loads and empties get different free time: an
 * empty is on the customer's track because we put it there for them to load, and the clock an
 * industry needs to turn a car is not the clock a consignee needs to unload one.
 */
public record Tariff(String code, LocalDate effective, int freeDaysLoaded, int freeDaysEmpty, List<Step> steps) {

    public Tariff {
        Objects.requireNonNull(effective, "effective");
        steps = List.copyOf(steps);
        if (Text.isBlank(code)) {
            throw new TariffError("a tariff is published under a number");
        }
        if (freeDaysLoaded < 0 || freeDaysEmpty < 0) {
            throw new TariffError("free time is not negative", Details.of("code", code));
        }
        if (steps.isEmpty()) {
            throw new TariffError("a tariff prices at least one day", Details.of("code", code));
        }
        List<Step> closed = steps.subList(0, steps.size() - 1);
        if (closed.stream().anyMatch(Step::openEnded)) {
            throw new TariffError("only the last step runs to the end", Details.of("code", code));
        }
        if (!steps.get(steps.size() - 1).openEnded()) {
            throw new TariffError("the last step runs to the end", Details.of("code", code));
        }
        for (int at = 1; at < closed.size(); at++) {
            if (closed.get(at).through() <= closed.get(at - 1).through()) {
                throw new TariffError("the steps climb in order", Details.of("code", code));
            }
        }
    }

    /** The free chargeable days a loaded car, or an empty one, gets. */
    public int freeDays(boolean loaded) {
        return loaded ? freeDaysLoaded : freeDaysEmpty;
    }

    /** What the nth chargeable day past free time costs. */
    public Money rateOnDay(int dayNumber) {
        if (dayNumber < 1) {
            throw new TariffError("chargeable days are numbered from one", Details.of("day", dayNumber));
        }
        for (Step step : steps) {
            if (step.openEnded() || dayNumber <= step.through()) {
                return new Money(step.cents());
            }
        }
        throw new TariffError("the schedule ran out", Details.of("code", code, "day", dayNumber));
    }

    /** What a number of chargeable days past free time comes to, each day at its own step. */
    public Money charge(int chargeableDays) {
        if (chargeableDays < 0) {
            throw new TariffError("a car cannot be held for a negative day", Details.of("days", chargeableDays));
        }
        long running = 0;
        for (int dayNumber = 1; dayNumber <= chargeableDays; dayNumber++) {
            running += rateOnDay(dayNumber).cents();
        }
        return new Money(running);
    }

    /** {@code tariff 4200 from 2024-07-01}. */
    @Override
    public String toString() {
        return "tariff " + code + " from " + effective;
    }
}
