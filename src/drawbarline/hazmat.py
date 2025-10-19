"""Placarding, and where a placarded car may stand in a train.

A car carrying a hazardous commodity is placarded, and a placarded car cannot
stand anywhere in the train it likes. The rule everybody knows is the buffer:
a placarded car has to be separated from the engine, and from an occupied car
at the rear, by at least one car that is not placarded. It is a train handling
rule rather than an accounting one, and it belongs here because the consist is
already built and it is the last chance to catch it.

The commodity decides the placard and the placard decides the handling. That
chain runs one way only. Nothing in here reads a placard off a car and infers
what is in it, because a placard left on an emptied car is the single most
common paperwork error on any railroad and inferring from it would turn one
error into two.

An empty car that last carried a hazardous commodity keeps its placards until
it is cleaned, which is why a residue movement is placarded and is handled as
though it were loaded. That is not an oversight in the rule. It is the rule.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from drawbarline.errors import DrawbarlineError
from drawbarline.lading import HAZARDOUS_GROUP, Commodity

BUFFER_CARS = 1


class HazmatError(DrawbarlineError):
    """A placard or a placement will not stand."""

    code = "hazmat"


class HazardClass(Enum):
    """The nine classes, as the placard names them."""

    EXPLOSIVE = 1
    GAS = 2
    FLAMMABLE_LIQUID = 3
    FLAMMABLE_SOLID = 4
    OXIDIZER = 5
    POISON = 6
    RADIOACTIVE = 7
    CORROSIVE = 8
    MISCELLANEOUS = 9

    @property
    def number(self) -> int:
        return self.value

    @property
    def text(self) -> str:
        return self.name.lower().replace("_", " ")

    @property
    def needs_buffer(self) -> bool:
        """Whether a car of this class has to be kept off the engine."""
        return self not in (HazardClass.MISCELLANEOUS,)


@dataclass(frozen=True, slots=True)
class Placard:
    """What goes on the four sides of the car."""

    hazard: HazardClass
    un_number: int
    name: str

    def __post_init__(self) -> None:
        if not 1 <= self.un_number <= 3999:
            raise HazmatError("a un number is four digits", given=self.un_number)
        if not self.name.strip():
            raise HazmatError("a placard names what is in the car")

    def __str__(self) -> str:
        return f"{self.name} UN{self.un_number:04d} class {self.hazard.number}"


def placard_for(commodity: Commodity, un_number: int, hazard: HazardClass) -> Placard:
    """The placard a hazardous commodity is moved under."""
    if not commodity.hazardous:
        raise HazmatError(
            "that commodity is not hazardous and takes no placard",
            code=str(commodity.code),
        )
    return Placard(hazard, un_number, commodity.name)


def is_hazardous(commodity: Commodity) -> bool:
    return commodity.code.major_group == HAZARDOUS_GROUP


@dataclass(frozen=True, slots=True)
class Standing:
    """One position in a train, and whether it is placarded."""

    position: int
    placarded: bool
    hazard: HazardClass | None = None
    occupied: bool = False

    def __post_init__(self) -> None:
        if self.position < 1:
            raise HazmatError("positions count from one", position=self.position)
        if self.placarded and self.hazard is None:
            raise HazmatError("a placarded car names its class", position=self.position)
        if not self.placarded and self.hazard is not None:
            raise HazmatError("an unplacarded car has no class", position=self.position)


def check_standing(cars: list[Standing]) -> list[str]:
    """Every reason the train may not be handled as it stands."""
    if not cars:
        return []
    ordered = sorted(cars, key=lambda one: one.position)
    if [one.position for one in ordered] != list(range(1, len(ordered) + 1)):
        raise HazmatError("the positions do not run from one without a gap")

    found = []
    for one in ordered[:BUFFER_CARS]:
        if one.placarded and one.hazard is not None and one.hazard.needs_buffer:
            found.append(
                f"position {one.position} is placarded {one.hazard.text} "
                f"and sits against the engine"
            )
    for one in ordered:
        if not one.occupied:
            continue
        for other in ordered:
            if not other.placarded or other.hazard is None:
                continue
            if not other.hazard.needs_buffer:
                continue
            if abs(other.position - one.position) <= BUFFER_CARS:
                found.append(
                    f"position {other.position} is placarded {other.hazard.text} "
                    f"and sits against the occupied car at {one.position}"
                )
    return found


def handles(cars: list[Standing]) -> bool:
    return not check_standing(cars)
