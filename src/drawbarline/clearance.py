"""What fits, and where it does not.

Ordinary freight cars are built inside a plate, which is a published outline
every railroad in interchange agrees to clear. A car inside plate C goes
anywhere on this railroad without anybody thinking about it, which is the
entire point of having plates.

A dimensional load is what happens when somebody puts a transformer on a flat
car. It is outside the plate, it has to be measured, and every overhead and
side clearance on the route has to be checked against it before the car moves.
Getting that wrong takes out a bridge.

The clearances here are ours, off the valuation maps, and they are the
governing dimension at each place rather than the nominal one. Bark River
carries the low overhead on this railroad and it is why nothing over plate F
goes north of Powers.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from drawbarline.errors import DrawbarlineError
from drawbarline.measure import Length
from drawbarline.mileage import milepost


class ClearanceError(DrawbarlineError):
    """A dimension or a place cannot be checked."""

    code = "clearance"


class Plate(Enum):
    """The published outlines, smallest first."""

    B = "B"
    C = "C"
    E = "E"
    F = "F"
    H = "H"

    @property
    def height(self) -> Length:
        return _PLATE_HEIGHT[self]

    @property
    def width(self) -> Length:
        return _PLATE_WIDTH[self]

    def holds(self, load: Dimensions) -> bool:
        return load.height <= self.height and load.width <= self.width


_PLATE_HEIGHT = {
    Plate.B: Length.of(15, 1),
    Plate.C: Length.of(15, 6),
    Plate.E: Length.of(15, 9),
    Plate.F: Length.of(17, 0),
    Plate.H: Length.of(20, 2),
}

_PLATE_WIDTH = {
    Plate.B: Length.of(10, 8),
    Plate.C: Length.of(10, 8),
    Plate.E: Length.of(10, 8),
    Plate.F: Length.of(10, 8),
    Plate.H: Length.of(10, 8),
}


@dataclass(frozen=True, slots=True)
class Dimensions:
    """How high and how wide, at the extreme point."""

    height: Length
    width: Length

    def __post_init__(self) -> None:
        if self.height.inches == 0 or self.width.inches == 0:
            raise ClearanceError("a load has a height and a width")

    def smallest_plate(self) -> Plate | None:
        """The smallest published outline this fits inside, if any does."""
        for plate in Plate:
            if plate.holds(self):
                return plate
        return None

    @property
    def dimensional(self) -> bool:
        """Whether it is outside every plate and has to be measured over the route."""
        return self.smallest_plate() is None

    def __str__(self) -> str:
        return f"{self.height} high {self.width} wide"


@dataclass(frozen=True, slots=True)
class Clearance:
    """The governing overhead and side clearance at one place."""

    station: str
    overhead: Length
    side: Length
    what: str = ""

    def __post_init__(self) -> None:
        milepost(self.station)

    def clears(self, load: Dimensions) -> bool:
        return load.height <= self.overhead and load.width <= self.side

    def __str__(self) -> str:
        where = f" ({self.what})" if self.what else ""
        return f"{self.station}{where} {self.overhead} over {self.side} wide"


# Off the valuation maps. Bark River is the governing overhead on the main and
# the highway bridge at Wallace is the narrow one.
CHART: dict[str, Clearance] = {
    "BKR": Clearance("BKR", Length.of(17, 4), Length.of(12, 6), "highway overpass"),
    "WAL": Clearance("WAL", Length.of(21, 0), Length.of(11, 2), "highway bridge"),
    "MRN": Clearance("MRN", Length.of(19, 6), Length.of(12, 0), "river bridge"),
    "IMT": Clearance("IMT", Length.of(18, 2), Length.of(12, 6), "mine trestle"),
    "GRB": Clearance("GRB", Length.of(22, 0), Length.of(13, 0), "yard lead"),
}

OPEN = Clearance("ESC", Length.of(23, 0), Length.of(13, 6), "no restriction")


def clearance_at(station: str) -> Clearance:
    """The governing clearance at a station, which is open where nothing is filed."""
    milepost(station)
    found = CHART.get(station)
    if found is None:
        return Clearance(station, OPEN.overhead, OPEN.side, OPEN.what)
    return found


def tightest_on(route: list[str]) -> Clearance:
    """The place on a route that governs, which is the lowest overhead."""
    if not route:
        raise ClearanceError("a route has at least one place")
    return min((clearance_at(one) for one in route), key=lambda one: one.overhead.inches)


def check_route(load: Dimensions, route: list[str]) -> list[str]:
    """Every place on a route the load will not clear."""
    if not route:
        raise ClearanceError("a route has at least one place")
    found = []
    for station in route:
        where = clearance_at(station)
        if load.height > where.overhead:
            found.append(f"{station} clears {where.overhead} and the load is {load.height}")
        if load.width > where.side:
            found.append(f"{station} clears {where.side} wide and the load is {load.width}")
    return found


def clears_route(load: Dimensions, route: list[str]) -> bool:
    return not check_route(load, route)
