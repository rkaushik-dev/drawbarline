"""What the cars standing here cost us a day.

The car hire report says what last month came to. This says what today is
costing, which is the number the superintendent can still do something about.
It is the hourly rate of every foreign car on hand, added up, times twenty
four, and it assumes nothing moves, which is exactly the assumption worth
pricing.

It is a forecast and it is honest about being one. Nothing here is posted,
nothing is billed off it, and the mileage half is left out entirely because a
car that does not move runs no miles. A forecast that quietly included
mileage would be a forecast that could not be checked against tomorrow's
accrual.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta

from drawbarline.carhire.accrual import settlement_date
from drawbarline.carhire.rates import RateError, RateTable
from drawbarline.events.positions import on_line, positions
from drawbarline.events.stream import EventLog
from drawbarline.fleet.register import FleetRegister
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.money import Money, total
from drawbarline.reports.render import Align, Column, Table, heading, money

HOURS_A_DAY = 24
TENTHS_OF_CENT_PER_CENT = 10


@dataclass(frozen=True, slots=True)
class Standing:
    """One car standing, and what it costs a day where it is."""

    car: CarMark
    owed_to: ReportingMark
    station: str
    hourly_tenths: int

    @property
    def a_day(self) -> Money:
        return Money(round(self.hourly_tenths * HOURS_A_DAY / TENTHS_OF_CENT_PER_CENT))

    def __str__(self) -> str:
        return f"{self.car} at {self.station} {self.a_day} a day"


def standing_cars(
    register: FleetRegister,
    log: EventLog,
    rates: RateTable,
    at: datetime,
    home: ReportingMark,
) -> list[Standing]:
    """Every foreign car on hand, priced at the hourly rate in force."""
    where = positions(log, at)
    day = settlement_date(at)
    found = []
    for car in on_line(where):
        if not register.has(car):
            continue
        owed_to = register.ownership(car).holder_on(day)
        if owed_to == home:
            continue
        record = register.get(car)
        if record.is_private:
            continue
        try:
            rate = rates.lookup(record.type_code, record.age_on(day), day)
        except RateError:
            continue
        found.append(
            Standing(
                car=car,
                owed_to=owed_to,
                station=where[car].station or "",
                hourly_tenths=rate.hourly_tenths,
            )
        )
    return found


def a_day(cars: list[Standing]) -> Money:
    """What the whole railroad is costing a day as it stands."""
    return total(one.a_day for one in cars)


def by_road(cars: list[Standing]) -> dict[ReportingMark, Money]:
    running: dict[ReportingMark, Money] = {}
    for one in cars:
        running[one.owed_to] = running.get(one.owed_to, Money(0)) + one.a_day
    return dict(sorted(running.items(), key=lambda pair: str(pair[0])))


def over(cars: list[Standing], days: int) -> Money:
    """What it comes to over a run of days, if nothing moves."""
    if days < 0:
        raise RateError("a forecast does not run backwards", days=days)
    return a_day(cars) * days


def forecast_report(cars: list[Standing], at: datetime, days: int = 7) -> list[str]:
    """One line a road, with the run out on the bottom."""
    table = Table(
        [
            Column("road"),
            Column("cars", Align.RIGHT),
            Column("a day", Align.RIGHT),
            Column(f"{days} days", Align.RIGHT),
        ]
    )
    counts: dict[ReportingMark, int] = {}
    for one in cars:
        counts[one.owed_to] = counts.get(one.owed_to, 0) + 1
    for road, amount in by_road(cars).items():
        table.add(str(road), str(counts[road]), money(amount), money(amount * days))
    table.total("all roads", str(len(cars)), money(a_day(cars)), money(over(cars, days)))
    subtitle = f"{at:%Y-%m-%d %H:%M}Z, nothing moves"
    return [*heading("car hire forecast", subtitle), *table.lines()]


def days_until(cars: list[Standing], amount: Money) -> int:
    """How many days of standing still it takes to reach an amount."""
    burn = a_day(cars)
    if burn.is_zero:
        raise RateError("nothing is accruing, so it never gets there")
    running = Money(0)
    days = 0
    while running < amount:
        running = running + burn
        days += 1
    return days


def tomorrow(at: datetime) -> datetime:
    return at + timedelta(days=1)
