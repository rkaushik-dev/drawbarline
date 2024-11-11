"""The equipment record for one car.

Everything the road knows about a piece of rolling stock that does not change
when the car moves. Where it moves and what it costs are somewhere else. What
lives here is what is stencilled on it and what was filed when it was built.

Two rules in here are worth naming because they refuse cars rather than
describe them. A car whose light weight is not under its gross rail load
cannot carry anything, which means somebody typed a number into the wrong box.
And a car may not go into interchange service more than fifty years after it
was built, or after it was rebuilt if it was, which is the rule that quietly
retires a fleet.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from drawbarline.errors import DrawbarlineError
from drawbarline.fleet.aar import CarKind, CarTypeCode, MechanicalDesignation, agrees
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.measure import Length
from drawbarline.weight import NominalCapacity, Weight, capacity_for, load_limit

INTERCHANGE_LIFE_YEARS = 50


class CarRecordError(DrawbarlineError):
    """An equipment record contradicts itself."""

    code = "car_record"


def _add_years(day: date, years: int) -> date:
    """The same day of the month, years on, holding the end of February."""
    try:
        return day.replace(year=day.year + years)
    except ValueError:
        return day.replace(year=day.year + years, day=28)


@dataclass(frozen=True, slots=True)
class Car:
    """One car, as the equipment register carries it."""

    mark: CarMark
    designation: MechanicalDesignation
    type_code: CarTypeCode
    built: date
    light_weight: Weight
    gross_rail_load: Weight
    outside_length: Length
    rebuilt: date | None = None

    def __post_init__(self) -> None:
        if not agrees(self.designation, self.type_code):
            raise CarRecordError(
                "the designation and the type code name different kinds of car",
                car=str(self.mark),
                designation=str(self.designation),
                type_code=str(self.type_code),
            )
        if self.rebuilt is not None and self.rebuilt < self.built:
            raise CarRecordError(
                "a car cannot be rebuilt before it was built",
                car=str(self.mark),
                built=str(self.built),
                rebuilt=str(self.rebuilt),
            )
        # This raises on its own account when the light weight is not under the
        # gross rail load, which is the check we want and the message we want.
        load_limit(self.gross_rail_load, self.light_weight)

    @property
    def kind(self) -> CarKind:
        return self.designation.kind

    @property
    def owner(self) -> ReportingMark:
        return self.mark.mark

    @property
    def is_private(self) -> bool:
        """A private car earns rent for a lessor, not car hire for a road."""
        return self.mark.is_private

    @property
    def load_limit(self) -> Weight:
        return load_limit(self.gross_rail_load, self.light_weight)

    @property
    def capacity(self) -> NominalCapacity:
        return capacity_for(self.gross_rail_load)

    @property
    def dates_from(self) -> date:
        """The date the fifty year clock runs from, which a rebuild resets."""
        return self.rebuilt or self.built

    @property
    def interchange_expires(self) -> date:
        return _add_years(self.dates_from, INTERCHANGE_LIFE_YEARS)

    def age_on(self, day: date) -> int:
        """Whole years since the car was built, which is what a rate table asks."""
        if day < self.built:
            raise CarRecordError(
                "that date is before the car existed", car=str(self.mark), day=str(day)
            )
        years = day.year - self.built.year
        if (day.month, day.day) < (self.built.month, self.built.day):
            years -= 1
        return years

    def eligible_on(self, day: date) -> bool:
        """Whether the car may be in interchange service on a day."""
        return self.built <= day < self.interchange_expires

    def __str__(self) -> str:
        return f"{self.mark} {self.designation}"
