"""Working out what a span of time on our rail is worth.

A span is not billed at one rate. The schedule changes on filed dates, and the
car has a birthday, and either of those inside a span steps the rate. So the
span is cut at every boundary that falls strictly inside it, each piece is
priced at the rate in force for that piece, and the pieces are added.

Where the cut falls is a decision, not a fact. Rates take effect at midnight,
and midnight is a different instant at Escanaba than at Marinette. The general
office keeps central time and files everything on it, so that is the clock the
cuts are made on, stated here once rather than assumed in five places.

A car past its fifty years earns nothing, and that is checked here rather
than by whoever is reading the accrual. A rate lookup that answers for a
condemned car is a rate lookup that keeps billing for it.

Rounding happens once, on the finished piece, not on each hour. A tenth of a
cent an hour rounded up every hour would add about two dollars a month to a
single car and nobody could reproduce the figure.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, time
from zoneinfo import ZoneInfo

from drawbarline.carhire.deprescription import Elections, payable
from drawbarline.carhire.rates import Rate, RateTable
from drawbarline.clock import Interval, to_local
from drawbarline.errors import RateError
from drawbarline.fleet.car import Car
from drawbarline.money import Money
from drawbarline.network import Station

SETTLEMENT_ZONE = ZoneInfo("America/Chicago")

TENTHS_OF_CENT_PER_CENT = 10
HUNDREDTHS_PER_HOUR = 100
TENTHS_PER_MILE = 10


def settlement_date(moment: datetime) -> date:
    """The accounting day a moment falls in, on the general office clock."""
    return moment.astimezone(SETTLEMENT_ZONE).date()


def midnight_on(day: date) -> datetime:
    """The instant an accounting day begins."""
    return datetime.combine(day, time.min, tzinfo=SETTLEMENT_ZONE)


@dataclass(frozen=True, slots=True)
class Piece:
    """One stretch of a span priced at one rate."""

    interval: Interval
    rate: Rate
    on: date

    @property
    def hundredth_hours(self) -> int:
        return self.interval.hundredth_hours

    @property
    def amount(self) -> Money:
        """Tenths of a cent an hour, over hundredths of an hour, rounded once."""
        tenths = self.rate.hourly_tenths * self.hundredth_hours
        return Money(round(tenths / (HUNDREDTHS_PER_HOUR * TENTHS_OF_CENT_PER_CENT)))

    def __str__(self) -> str:
        return f"{self.on} {self.hundredth_hours / 100:.2f} hours {self.amount}"


def _anniversaries(car: Car, first: date, last: date) -> list[date]:
    """Every build date anniversary strictly inside a span."""
    found = []
    for year in range(first.year, last.year + 1):
        try:
            birthday = car.built.replace(year=year)
        except ValueError:
            birthday = car.built.replace(year=year, day=28)
        if first < birthday <= last:
            found.append(birthday)
    return found


NO_ELECTIONS = Elections()


def cut_points(car: Car, table: RateTable, window: Interval) -> list[date]:
    """The accounting days a span has to be split on, in order."""
    first = settlement_date(window.start)
    last = settlement_date(window.end)
    steps = set(table.changes_between(car.type_code, first, last))
    steps.update(_anniversaries(car, first, last))
    return sorted(day for day in steps if window.contains(midnight_on(day)))


def pieces(
    car: Car,
    table: RateTable,
    window: Interval,
    elections: Elections | None = None,
) -> list[Piece]:
    """Cut a span at every rate step and price each piece.

    A piece the car was not in interchange service for is left out rather than
    priced at zero, so a span that is wholly outside the car's life comes back
    empty and nothing on the statement mentions it.
    """
    if window.empty:
        return []
    chosen = elections or NO_ELECTIONS
    edges = [window.start]
    edges.extend(midnight_on(day) for day in cut_points(car, table, window))
    edges.append(window.end)

    found = []
    for start, end in zip(edges, edges[1:], strict=False):
        if start == end:
            continue
        day = settlement_date(start)
        rate = payable(car, table, chosen, day)
        if rate is None:
            continue
        found.append(Piece(Interval(start, end), rate, day))
    return found


def hourly(
    car: Car,
    table: RateTable,
    window: Interval,
    elections: Elections | None = None,
) -> Money:
    """What the hours in a span come to."""
    total = 0
    for piece in pieces(car, table, window, elections):
        total += piece.amount.cents
    return Money(total)


def mileage(
    car: Car,
    table: RateTable,
    tenths_run: int,
    on: date,
    elections: Elections | None = None,
) -> Money:
    """What the miles come to, at the rate in force on the day they ran."""
    if tenths_run < 0:
        raise RateError("a car does not run backwards", tenths=tenths_run)
    rate = payable(car, table, elections or NO_ELECTIONS, on)
    if rate is None:
        return Money(0)
    mills = rate.mileage_mills * tenths_run
    return Money(round(mills / (TENTHS_PER_MILE * TENTHS_OF_CENT_PER_CENT)))


def as_read_at(window: Interval, station: Station) -> str:
    """The span as the clerk at a station would read it, for a report."""
    start = to_local(station, window.start)
    end = to_local(station, window.end)
    return f"{start:%Y-%m-%d %H:%M} to {end:%Y-%m-%d %H:%M}"
