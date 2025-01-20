"""Counting the days a car was held.

The clock does not start when the car is placed. It starts the first
chargeable day after that, because a car set out at four in the afternoon is
not a day of anybody's time. From there the customer has their free days, all
of them chargeable days, and demurrage runs from the day after the last free
one until the day they release it.

Every date in here is local at the station the car is standing at, not utc and
not the general office clock. A car placed at half past eleven on a Monday
night at Escanaba was placed on Monday, and if the same events had happened at
Marinette it would still have been Monday, but the two are an hour apart and
sooner or later one of them lands the wrong side of midnight. Reading the
local date at the station is the only thing that gets both right.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from drawbarline.clock import to_local
from drawbarline.days import add_chargeable_days, chargeable_days, next_chargeable
from drawbarline.demurrage.tariff import Tariff
from drawbarline.errors import TariffError
from drawbarline.events.spans import OnTrack
from drawbarline.marks import CarMark
from drawbarline.money import Money
from drawbarline.network import Station


@dataclass(frozen=True, slots=True)
class Detention:
    """One car on one track, counted out into free days and chargeable ones."""

    car: CarMark
    industry: str
    placed_on: date
    clock_starts: date
    free_until: date | None
    counted_to: date
    chargeable: int
    loaded: bool
    constructive: bool

    @property
    def still_free(self) -> bool:
        return self.chargeable == 0

    def charge(self, tariff: Tariff) -> Money:
        return tariff.charge(self.chargeable)

    def __str__(self) -> str:
        if self.still_free:
            return f"{self.car} at {self.industry} inside free time"
        return f"{self.car} at {self.industry} {self.chargeable} chargeable days"


def clock_starts_on(placed_on: date) -> date:
    """The first chargeable day after placement, which is where free time begins."""
    return next_chargeable(placed_on)


def free_time_ends(clock_starts: date, free_days: int) -> date | None:
    """The last free day, or nothing at all when the tariff allows none."""
    if free_days < 0:
        raise TariffError("free time is not negative", free_days=free_days)
    if free_days == 0:
        return None
    return add_chargeable_days(clock_starts, free_days - 1)


def count(
    span: OnTrack,
    tariff: Tariff,
    station: Station,
    *,
    loaded: bool,
) -> Detention:
    """Work a placement span out into the days it is charged for."""
    placed_on = to_local(station, span.interval.start).date()
    ends_at = span.released_at or span.interval.end
    counted_to = to_local(station, ends_at).date()

    clock_starts = clock_starts_on(placed_on)
    free_until = free_time_ends(clock_starts, tariff.free_days(loaded=loaded))
    first_charged = clock_starts if free_until is None else next_chargeable(free_until)

    charged = 0 if counted_to < first_charged else chargeable_days(first_charged, counted_to)
    return Detention(
        car=span.car,
        industry=span.industry,
        placed_on=placed_on,
        clock_starts=clock_starts,
        free_until=free_until,
        counted_to=counted_to,
        chargeable=charged,
        loaded=loaded,
        constructive=span.constructive,
    )
