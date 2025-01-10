"""What we owe, to whom, for the month.

Three rules decide whether a span costs anything at all, and they are worth
stating plainly because every one of them has been got wrong somewhere.

We do not pay ourselves. A car we own, running on our own rail, earns nothing,
and a car we own that is out on lease earns for the lessee instead.

A private car earns mileage and no hours. The owner of a car under a private
mark is not a railroad and is not in the car hire pool, so what they get is an
allowance on the miles the car runs and nothing for the time it stands.

Everything else earns both halves. The hours come off the span, the miles come
off the movements inside the span, and the two are kept apart on the statement
because a connection will dispute one without disputing the other.
"""

from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass

from drawbarline.carhire.accrual import hourly, mileage, settlement_date
from drawbarline.carhire.rates import RateTable
from drawbarline.clock import Interval
from drawbarline.errors import UnknownCar
from drawbarline.events.kinds import EventKind
from drawbarline.events.spans import on_line_spans
from drawbarline.events.stream import CarStream, EventLog
from drawbarline.fleet.register import FleetRegister
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.money import Money, total


@dataclass(frozen=True, slots=True)
class Charge:
    """One car, one span, one party owed."""

    car: CarMark
    owed_to: ReportingMark
    window: Interval
    hours: Money
    miles: Money

    @property
    def amount(self) -> Money:
        return self.hours + self.miles

    @property
    def free(self) -> bool:
        return self.amount.is_zero

    def __str__(self) -> str:
        return f"{self.car} to {self.owed_to} {self.amount}"


def tenths_in(stream: CarStream, window: Interval) -> int:
    """The miles the car ran inside a window, off its movement events."""
    return sum(
        event.tenths or 0 for event in stream.during(window) if event.kind is EventKind.MOVED
    )


def charges_for(
    register: FleetRegister,
    log: EventLog,
    table: RateTable,
    car_mark: CarMark,
    window: Interval,
    home: ReportingMark,
) -> list[Charge]:
    """Every charge one car raises inside a window."""
    car = register.get(car_mark)
    stream = log.stream(car_mark)
    ownership = register.ownership(car_mark)

    found = []
    for span in on_line_spans(stream, window.end):
        cut = span.interval.clip(window)
        if cut.empty:
            continue
        owed_to = ownership.holder_on(settlement_date(cut.start))
        if owed_to == home:
            continue
        miles = mileage(car, table, tenths_in(stream, cut), settlement_date(cut.start))
        hours = Money(0) if car.is_private else hourly(car, table, cut)
        found.append(Charge(car_mark, owed_to, cut, hours, miles))
    return found


class Liability:
    """Every charge for a window, gathered so a statement can be cut from it."""

    def __init__(self, window: Interval) -> None:
        self.window = window
        self._charges: list[Charge] = []

    def add(self, charge: Charge) -> Charge:
        self._charges.append(charge)
        return charge

    @property
    def charges(self) -> list[Charge]:
        return sorted(self._charges, key=lambda one: (str(one.owed_to), str(one.car)))

    def by_road(self) -> dict[ReportingMark, Money]:
        running: dict[ReportingMark, Money] = defaultdict(lambda: Money(0))
        for charge in self._charges:
            running[charge.owed_to] = running[charge.owed_to] + charge.amount
        return dict(running)

    def for_road(self, road: ReportingMark) -> list[Charge]:
        return [charge for charge in self.charges if charge.owed_to == road]

    def total(self) -> Money:
        return total(charge.amount for charge in self._charges)

    def hours_total(self) -> Money:
        return total(charge.hours for charge in self._charges)

    def miles_total(self) -> Money:
        return total(charge.miles for charge in self._charges)

    def cars(self) -> list[CarMark]:
        return sorted({charge.car for charge in self._charges})

    def __len__(self) -> int:
        return len(self._charges)


def liability(
    register: FleetRegister,
    log: EventLog,
    table: RateTable,
    window: Interval,
    home: ReportingMark,
) -> Liability:
    """Run the whole log for a window, skipping cars we have no record of."""
    found = Liability(window)
    for car_mark in log.cars():
        try:
            charges = charges_for(register, log, table, car_mark, window, home)
        except UnknownCar:
            continue
        for charge in charges:
            found.add(charge)
    return found
