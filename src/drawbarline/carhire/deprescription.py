"""Rates an owner has chosen to take less than the schedule for.

The published schedule is what a car earns by default. An owner may file to
take less, and they do it for the same reason anybody discounts anything: a
car nobody wants at the schedule rate is a car standing on their own rail
earning nothing at all. The filing is per car type and per age band, it takes
effect on a date, and it can only go down. An election that raised the rate
would be a rate rise with extra steps and the pool does not allow it.

The other rule in here is harder and quieter. A car past its fifty years is
not in interchange service, and a car not in interchange service earns no car
hire at all, whatever the schedule says. Roads have gone on billing for
condemned cars for months because nobody wired that rule into the rate lookup,
so it is wired in here rather than left to whoever is reading a report.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from drawbarline.carhire.rates import Rate, RateError, RateTable
from drawbarline.fleet.aar import CarTypeCode
from drawbarline.fleet.car import Car


@dataclass(frozen=True, slots=True)
class Election:
    """One owner taking less than the schedule for one band."""

    type_code: CarTypeCode
    from_age: int
    to_age: int
    effective: date
    hourly_tenths: int

    def __post_init__(self) -> None:
        if self.from_age < 0:
            raise RateError("an age band starts at zero or later", from_age=self.from_age)
        if self.to_age < self.from_age:
            raise RateError(
                "an age band ends after it starts",
                from_age=self.from_age,
                to_age=self.to_age,
            )
        if self.hourly_tenths < 0:
            raise RateError("an elected rate is not negative", elected=self.hourly_tenths)

    def covers_age(self, age: int) -> bool:
        return self.from_age <= age <= self.to_age

    def __str__(self) -> str:
        cents = self.hourly_tenths / 10
        band = f"{self.from_age}-{self.to_age}"
        return f"{self.type_code} {band} at {cents:.1f}c from {self.effective}"


class Elections:
    """Every filing an owner has made to take less."""

    def __init__(self) -> None:
        self._filed: list[Election] = []

    def file(self, election: Election, schedule: RateTable) -> Election:
        """Take a filing, refusing one that asks for more than the schedule."""
        published = schedule.lookup(election.type_code, election.from_age, election.effective)
        if election.hourly_tenths > published.hourly_tenths:
            raise RateError(
                "an election takes less than the schedule, never more",
                type_code=str(election.type_code),
                elected=election.hourly_tenths,
                published=published.hourly_tenths,
            )
        self._filed.append(election)
        self._filed.sort(key=lambda one: (one.effective, one.from_age))
        return election

    def elected(self, type_code: CarTypeCode, age: int, on: date) -> int | None:
        """The elected rate in force, or nothing where none was filed."""
        found = [
            one
            for one in self._filed
            if one.type_code == type_code and one.effective <= on and one.covers_age(age)
        ]
        if not found:
            return None
        return max(found, key=lambda one: one.effective).hourly_tenths

    def filings(self) -> list[Election]:
        return list(self._filed)

    def __len__(self) -> int:
        return len(self._filed)


def payable(car: Car, schedule: RateTable, elections: Elections, on: date) -> Rate | None:
    """What the car earns an hour, or nothing at all when it is out of service."""
    if not car.eligible_on(on):
        return None
    age = car.age_on(on)
    published = schedule.lookup(car.type_code, age, on)
    chosen = elections.elected(car.type_code, age, on)
    if chosen is None:
        return published
    return Rate(chosen, published.mileage_mills)


def earns_car_hire(car: Car, on: date) -> bool:
    """Whether the car is in interchange service at all on that day."""
    return car.eligible_on(on)
