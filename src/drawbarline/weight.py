"""Weights, kept in whole pounds.

Everything a railroad weighs is stencilled in pounds. Light weight, load limit
and gross rail load all appear on the car, and the difference between two of
them decides whether a load is legal. Pounds are the honest unit, so the class
holds an int of them and tons only exist where somebody says a car is a
hundred ton car.

A hundred ton car does not weigh a hundred tons. It is a nominal capacity
class, and what it really names is a gross rail load of 263,000 pounds. That
mismatch has cost more than one clerk an afternoon, so the class is an
enumeration with the real number attached rather than a bare integer nobody
can check.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from functools import total_ordering

from drawbarline.errors import DrawbarlineError

POUNDS_PER_TON = 2000


class WeightError(DrawbarlineError):
    """A weight cannot exist, or two of them do not agree."""

    code = "weight"


@total_ordering
@dataclass(frozen=True, slots=True)
class Weight:
    """A weight in whole pounds."""

    pounds: int

    def __post_init__(self) -> None:
        if not isinstance(self.pounds, int) or isinstance(self.pounds, bool):
            raise WeightError("a weight is a whole number of pounds", pounds=self.pounds)
        if self.pounds < 0:
            raise WeightError("a weight is not negative", pounds=self.pounds)

    @classmethod
    def tons(cls, tons: int) -> Weight:
        """Short tons, which is the only ton a North American railroad means."""
        if not isinstance(tons, int) or isinstance(tons, bool):
            raise WeightError("tons come in whole numbers here", tons=tons)
        return cls(tons * POUNDS_PER_TON)

    def in_tons(self) -> float:
        """Decimal tons, for a report. Never for a comparison."""
        return self.pounds / POUNDS_PER_TON

    def __str__(self) -> str:
        return f"{self.pounds:,} lb"

    def __add__(self, other: Weight) -> Weight:
        if not isinstance(other, Weight):
            return NotImplemented
        return Weight(self.pounds + other.pounds)

    def __sub__(self, other: Weight) -> Weight:
        if not isinstance(other, Weight):
            return NotImplemented
        if other.pounds > self.pounds:
            raise WeightError("that subtraction runs past zero", left=str(self), right=str(other))
        return Weight(self.pounds - other.pounds)

    def __mul__(self, count: int) -> Weight:
        if not isinstance(count, int) or isinstance(count, bool):
            return NotImplemented
        if count < 0:
            raise WeightError("a weight does not scale by a negative", count=count)
        return Weight(self.pounds * count)

    __rmul__ = __mul__

    def __lt__(self, other: object) -> bool:
        if not isinstance(other, Weight):
            return NotImplemented
        return self.pounds < other.pounds


NOTHING = Weight(0)


class NominalCapacity(Enum):
    """What the trade means by a seventy or a hundred ton car."""

    SEVENTY = 70
    HUNDRED = 100
    ONE_TEN = 110
    ONE_TWENTY_FIVE = 125

    @property
    def gross_rail_load(self) -> Weight:
        """The real number the class stands for."""
        return Weight(_GROSS_RAIL_LOAD[self])

    @property
    def label(self) -> str:
        return f"{self.value} ton"


_GROSS_RAIL_LOAD = {
    NominalCapacity.SEVENTY: 220000,
    NominalCapacity.HUNDRED: 263000,
    NominalCapacity.ONE_TEN: 286000,
    NominalCapacity.ONE_TWENTY_FIVE: 315000,
}


def capacity_for(gross_rail_load: Weight) -> NominalCapacity:
    """Read the class back off a gross rail load, refusing an odd number."""
    for capacity, pounds in _GROSS_RAIL_LOAD.items():
        if pounds == gross_rail_load.pounds:
            return capacity
    raise WeightError("no capacity class has that gross rail load", pounds=gross_rail_load.pounds)


def load_limit(gross_rail_load: Weight, light_weight: Weight) -> Weight:
    """What the car may carry, which is the gross rail load less its own weight."""
    if light_weight >= gross_rail_load:
        raise WeightError(
            "a car cannot weigh its own gross rail load empty",
            light_weight=light_weight.pounds,
            gross_rail_load=gross_rail_load.pounds,
        )
    return gross_rail_load - light_weight


def overload(lading: Weight, limit: Weight) -> Weight:
    """How far past the load limit a lading sits. Zero when it is inside."""
    if lading <= limit:
        return NOTHING
    return lading - limit
