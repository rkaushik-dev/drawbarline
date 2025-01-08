"""The car hire rate table.

A rate has two halves. The hourly half is what the car earns for every hour it
sits on a foreign road, quoted in tenths of a cent an hour, and the mileage
half is what it earns for every mile it runs, quoted in mills a mile. Both are
keyed on the car type code and on how old the car is, because a new car earns
more than a forty year old one and the steps are written into the schedule.

Rates change on a date and never in the middle of a car cycle, which is the
part that catches people. A car received in December and delivered in January
is billed at December rates for the December hours and at January rates for
the January ones, so the lookup takes a date and the accrual splits the span.

Money at these resolutions does not fit in cents, so both halves are held as
integers in their own unit and only turned into money once, at the end, on a
whole span. Rounding each hour and adding them up gives a different and
indefensible answer.
"""

from __future__ import annotations

import bisect
from dataclasses import dataclass
from datetime import date

from drawbarline.errors import RateError
from drawbarline.fleet.aar import CarTypeCode

TENTHS_OF_CENT_PER_CENT = 10
MILLS_PER_CENT = 10


@dataclass(frozen=True, slots=True)
class Rate:
    """One line of the schedule."""

    hourly_tenths: int
    mileage_mills: int

    def __post_init__(self) -> None:
        if self.hourly_tenths < 0 or self.mileage_mills < 0:
            raise RateError(
                "a rate is not negative",
                hourly=self.hourly_tenths,
                mileage=self.mileage_mills,
            )

    @property
    def hourly_cents(self) -> float:
        return self.hourly_tenths / TENTHS_OF_CENT_PER_CENT

    @property
    def mileage_cents(self) -> float:
        return self.mileage_mills / MILLS_PER_CENT

    def __str__(self) -> str:
        return f"{self.hourly_cents:.1f}c an hour and {self.mileage_cents:.1f}c a mile"


@dataclass(frozen=True, slots=True)
class Band:
    """A rate for one type of car within an age range, from a date."""

    type_code: CarTypeCode
    from_age: int
    to_age: int
    effective: date
    rate: Rate

    def __post_init__(self) -> None:
        if self.from_age < 0:
            raise RateError("an age band starts at zero or later", from_age=self.from_age)
        if self.to_age < self.from_age:
            raise RateError(
                "an age band ends after it starts",
                from_age=self.from_age,
                to_age=self.to_age,
            )

    def covers_age(self, age: int) -> bool:
        return self.from_age <= age <= self.to_age


class RateTable:
    """Every band the road has on file, indexed so a lookup is not a scan."""

    def __init__(self) -> None:
        self._bands: dict[CarTypeCode, list[Band]] = {}

    def add(self, band: Band) -> Band:
        standing = self._bands.setdefault(band.type_code, [])
        for other in standing:
            if other.effective == band.effective and _ages_overlap(other, band):
                raise RateError(
                    "that band overlaps one already filed",
                    type_code=str(band.type_code),
                    effective=str(band.effective),
                )
        standing.append(band)
        standing.sort(key=lambda one: (one.effective, one.from_age))
        return band

    def lookup(self, type_code: CarTypeCode, age: int, on: date) -> Rate:
        """The rate in effect for a car of that type and age on that day."""
        if age < 0:
            raise RateError("a car has no negative age", age=age)
        standing = self._bands.get(type_code)
        if not standing:
            raise RateError("no rate on file for that car type", type_code=str(type_code))
        found = [band for band in standing if band.effective <= on and band.covers_age(age)]
        if not found:
            raise RateError("no rate in effect", type_code=str(type_code), age=age, on=str(on))
        return max(found, key=lambda band: band.effective).rate

    def effective_dates(self, type_code: CarTypeCode) -> list[date]:
        """Every date the rate for a car type changes, in order."""
        standing = self._bands.get(type_code, [])
        return sorted({band.effective for band in standing})

    def changes_between(self, type_code: CarTypeCode, first: date, last: date) -> list[date]:
        """The dates inside a span where the rate steps, which is where a bill splits."""
        if last < first:
            raise RateError("that span runs backwards", first=str(first), last=str(last))
        dates = self.effective_dates(type_code)
        start = bisect.bisect_right(dates, first)
        end = bisect.bisect_right(dates, last)
        return dates[start:end]

    def types(self) -> list[CarTypeCode]:
        return sorted(self._bands, key=str)

    def __len__(self) -> int:
        return sum(len(bands) for bands in self._bands.values())


def _ages_overlap(one: Band, other: Band) -> bool:
    return one.from_age <= other.to_age and other.from_age <= one.to_age
