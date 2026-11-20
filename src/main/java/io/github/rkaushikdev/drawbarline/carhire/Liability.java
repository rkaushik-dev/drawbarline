package io.github.rkaushikdev.drawbarline.carhire;

import io.github.rkaushikdev.drawbarline.clock.Interval;
import io.github.rkaushikdev.drawbarline.events.CarStream;
import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.events.EventKind;
import io.github.rkaushikdev.drawbarline.events.EventLog;
import io.github.rkaushikdev.drawbarline.events.OnLine;
import io.github.rkaushikdev.drawbarline.events.Spans;
import io.github.rkaushikdev.drawbarline.fleet.Car;
import io.github.rkaushikdev.drawbarline.fleet.FleetRegister;
import io.github.rkaushikdev.drawbarline.fleet.Ownership;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What we owe, to whom, for a window: every charge gathered so a statement can be cut from it.
 *
 * <p>Three rules decide whether a span costs anything at all, and every one of them has been got
 * wrong somewhere. We do not pay ourselves: a car we own, running on our own rail, earns nothing,
 * and a car we own that is out on lease earns for the lessee instead. A private car earns mileage
 * and no hours, because its owner is not a railroad and not in the car hire pool. Everything else
 * earns both halves: the hours off the span, the miles off the movements inside it.
 */
public final class Liability {

    private static final Comparator<Charge> STATEMENT_ORDER = Comparator.comparing(
                    (Charge one) -> one.owedTo().toString())
            .thenComparing(one -> one.car().toString());

    private final Interval window;
    private final List<Charge> charges = new ArrayList<>();

    public Liability(Interval window) {
        this.window = Objects.requireNonNull(window, "window");
    }

    /** The window the charges were gathered for. */
    public Interval window() {
        return window;
    }

    public Charge add(Charge charge) {
        charges.add(Objects.requireNonNull(charge, "charge"));
        return charge;
    }

    /** Every charge, by the road owed and then the car. */
    public List<Charge> charges() {
        return charges.stream().sorted(STATEMENT_ORDER).toList();
    }

    /** What each road is owed, in the order the roads first appear. */
    public Map<ReportingMark, Money> byRoad() {
        Map<ReportingMark, Money> running = new LinkedHashMap<>();
        for (Charge charge : charges) {
            running.merge(charge.owedTo(), charge.amount(), Money::plus);
        }
        return Collections.unmodifiableMap(running);
    }

    /** The charges owed to one road, in statement order. */
    public List<Charge> forRoad(ReportingMark road) {
        return charges().stream().filter(charge -> charge.owedTo().equals(road)).toList();
    }

    public Money total() {
        return Money.total(charges.stream().map(Charge::amount).toList());
    }

    public Money hoursTotal() {
        return Money.total(charges.stream().map(Charge::hours).toList());
    }

    public Money milesTotal() {
        return Money.total(charges.stream().map(Charge::miles).toList());
    }

    /** Every car charged for, in mark order. */
    public List<CarMark> cars() {
        return charges.stream().map(Charge::car).distinct().sorted().toList();
    }

    public int size() {
        return charges.size();
    }

    /** The miles the car ran inside a window, in tenths, off its movement events. */
    public static long tenthsIn(CarStream stream, Interval window) {
        long tenths = 0;
        for (Event event : stream.during(window)) {
            if (event.kind() == EventKind.MOVED) {
                tenths += event.tenths().orElse(0);
            }
        }
        return tenths;
    }

    /** Every charge one car raises inside a window. */
    public static List<Charge> chargesFor(
            FleetRegister register,
            EventLog log,
            RateTable table,
            CarMark carMark,
            Interval window,
            ReportingMark home) {
        Car car = register.get(carMark);
        CarStream stream = log.stream(carMark);
        Ownership ownership = register.ownership(carMark);

        List<Charge> found = new ArrayList<>();
        for (OnLine span : Spans.onLineSpans(stream, window.end())) {
            Interval cut = span.interval().clip(window);
            if (cut.isEmpty()) {
                continue;
            }
            ReportingMark owedTo = ownership.holderOn(Accrual.settlementDate(cut.start()));
            if (owedTo.equals(home)) {
                continue;
            }
            Money miles = Accrual.mileage(car, table, tenthsIn(stream, cut), Accrual.settlementDate(cut.start()));
            Money hours = car.isPrivate() ? Money.ZERO : Accrual.hourly(car, table, cut);
            found.add(new Charge(carMark, owedTo, cut, hours, miles));
        }
        return List.copyOf(found);
    }

    /** Run the whole log for a window, passing over cars the register has no record of. */
    public static Liability run(
            FleetRegister register, EventLog log, RateTable table, Interval window, ReportingMark home) {
        Liability found = new Liability(window);
        for (CarMark carMark : log.cars()) {
            if (!register.has(carMark)) {
                continue;
            }
            for (Charge charge : chargesFor(register, log, table, carMark, window, home)) {
                found.add(charge);
            }
        }
        return found;
    }
}
