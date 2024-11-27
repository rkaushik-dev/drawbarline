"""The weight stencil and when the car is due back on the scale.

Every car carries its light weight painted on the side with the date and place
it was last weighed. The number matters because a load limit is the gross rail
load less that light weight, and a car picks up weight over its life. Brake
gear is replaced with heavier gear, a hopper gathers a quarter ton of frozen
product in the corners, and after a decade the stencil is a polite fiction.

So a car goes back on the scale on a cycle. Most cars every ten years. Tank
cars are exempt, because their light weight is a certified figure that changes
only when the tank does. A car whose reweigh is overdue is still a car we can
move, but the load limit computed off its stencil is not one we would defend
in a claim.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from drawbarline.errors import DrawbarlineError
from drawbarline.fleet.aar import CarKind
from drawbarline.fleet.car import Car
from drawbarline.weight import Weight

DEFAULT_REWEIGH_YEARS = 10
EXEMPT_KINDS = frozenset({CarKind.TANK, CarKind.MAINTENANCE})

NEW = "NEW"


class StencilError(DrawbarlineError):
    """A weighing cannot be recorded as it stands."""

    code = "stencil"


@dataclass(frozen=True, slots=True)
class Weighing:
    """What the scale said, where, and when."""

    pounds: Weight
    on: date
    where: str = NEW

    def __post_init__(self) -> None:
        place = self.where.strip().upper()
        if not place:
            raise StencilError("a weighing records where it happened", on=str(self.on))
        if place != NEW and not place.isalnum():
            raise StencilError("a scale is named by a station code", where=self.where)
        object.__setattr__(self, "where", place)

    @property
    def as_built(self) -> bool:
        """The light weight the builder certified, which is not a reweigh."""
        return self.where == NEW

    @property
    def stamp(self) -> str:
        """How the date reads on the car, month and two digit year."""
        return f"{self.on.month}-{self.on.year % 100:02d}"

    def __str__(self) -> str:
        return f"{self.where} {self.stamp}"


def exempt(car: Car) -> bool:
    return car.kind in EXEMPT_KINDS


def due_by(car: Car, weighing: Weighing) -> date | None:
    """When the car is next due on the scale, or nothing when it is exempt."""
    if exempt(car):
        return None
    try:
        return weighing.on.replace(year=weighing.on.year + DEFAULT_REWEIGH_YEARS)
    except ValueError:
        return weighing.on.replace(year=weighing.on.year + DEFAULT_REWEIGH_YEARS, day=28)


def overdue_on(car: Car, weighing: Weighing, day: date) -> bool:
    due = due_by(car, weighing)
    return due is not None and day > due


def load_limit_from(car: Car, weighing: Weighing) -> Weight:
    """The load limit the stencil claims, which uses the weighed light weight."""
    if weighing.pounds >= car.gross_rail_load:
        raise StencilError(
            "the scale read at or over the gross rail load",
            car=str(car.mark),
            weighed=weighing.pounds.pounds,
        )
    return car.gross_rail_load - weighing.pounds


def block(car: Car, weighing: Weighing) -> list[str]:
    """The stencil as it is painted, one line per line on the car."""
    return [
        str(car.mark),
        f"{car.designation} {car.type_code}",
        f"CAPY {car.gross_rail_load.pounds}",
        f"LD LMT {load_limit_from(car, weighing).pounds}",
        f"LT WT {weighing.pounds.pounds}",
        str(weighing) if weighing.as_built else f"RWT {weighing}",
    ]
