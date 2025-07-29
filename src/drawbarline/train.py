"""The consist, and whether the train can actually leave.

A wheel report is the list of cars in a train in the order they stand, head end
first, and it is the document everything else about the train is read off. Two
numbers come out of it and both stop trains. The tonnage is what the engines
have to drag up the ruling grade and it is a hard limit for a given district.
The length is limited by the shortest siding the train has to fit into, which
on this railroad is at Wallace and is why nothing longer than sixty cars goes
south of Powers.

The length of a train is not the sum of the car lengths. Every coupling adds
the slack between two drawbars, and over sixty cars that is most of a car
length. It is small, it is real, and leaving it out is how a train ends up
four feet too long for the siding it was told to take.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from drawbarline.errors import DrawbarlineError
from drawbarline.fleet.car import Car
from drawbarline.lading import Lading
from drawbarline.measure import Length
from drawbarline.mileage import BRANCH, MAIN
from drawbarline.reports.render import Align, Column, Table, heading
from drawbarline.weight import POUNDS_PER_TON, Weight

SLACK_PER_COUPLING = Length(6)


class ConsistError(DrawbarlineError):
    """A train cannot be made up as asked."""

    code = "consist"


@dataclass(frozen=True, slots=True)
class Position:
    """One car in a train, in the place it stands."""

    car: Car
    lading: Lading | None = None

    @property
    def loaded(self) -> bool:
        return self.lading is not None

    @property
    def weight(self) -> Weight:
        """What the car weighs on the rail, which is its own weight plus the load."""
        if self.lading is None:
            return self.car.light_weight
        return self.car.light_weight + self.lading.weight

    def __str__(self) -> str:
        what = self.lading.commodity.name if self.lading else "empty"
        return f"{self.car.mark} {what}"


@dataclass(frozen=True, slots=True)
class Consist:
    """A train, head end first."""

    symbol: str
    day: date
    positions: tuple[Position, ...]

    def __post_init__(self) -> None:
        if not self.symbol.strip():
            raise ConsistError("a train runs under a symbol")
        marks = [one.car.mark for one in self.positions]
        if len(marks) != len(set(marks)):
            raise ConsistError("a car appears twice in the consist", symbol=self.symbol)

    @property
    def cars(self) -> int:
        return len(self.positions)

    @property
    def loads(self) -> int:
        return len([one for one in self.positions if one.loaded])

    @property
    def empties(self) -> int:
        return self.cars - self.loads

    @property
    def weight(self) -> Weight:
        running = Weight(0)
        for one in self.positions:
            running = running + one.weight
        return running

    @property
    def tons(self) -> int:
        """Trailing tons, rounded the way a tonnage rating is read."""
        return self.weight.pounds // POUNDS_PER_TON

    @property
    def length(self) -> Length:
        """Over the pulling faces, with the slack at every coupling counted."""
        if not self.positions:
            return Length(0)
        over_cars = Length(sum(one.car.outside_length.inches for one in self.positions))
        return over_cars + SLACK_PER_COUPLING * (self.cars - 1)

    def carries(self, mark: object) -> bool:
        return any(one.car.mark == mark for one in self.positions)

    def __str__(self) -> str:
        return f"{self.symbol} {self.day} {self.cars} cars {self.tons} tons"


@dataclass(frozen=True, slots=True)
class District:
    """What a stretch of railroad will take."""

    line: str
    name: str
    max_tons: int
    max_length: Length

    def __post_init__(self) -> None:
        if self.line not in (MAIN, BRANCH):
            raise ConsistError("this road has a main and a branch", line=self.line)
        if self.max_tons < 1:
            raise ConsistError("a district takes at least a ton", name=self.name)


# The ruling grade south of Powers is light and the siding at Wallace is what
# governs. The branch climbs to Iron Mountain and takes half the tonnage.
DISTRICTS = (
    District(MAIN, "escanaba to green bay", 6800, Length.of(4200)),
    District(BRANCH, "powers to niagara", 3400, Length.of(2600)),
)


def district(line: str) -> District:
    for one in DISTRICTS:
        if one.line == line:
            return one
    raise ConsistError("no such district", line=line)


def check(consist: Consist, over: District) -> list[str]:
    """Every reason the train cannot leave as it stands."""
    found = []
    if consist.tons > over.max_tons:
        found.append(f"{consist.tons} tons is over the {over.max_tons} the {over.name} takes")
    if consist.length > over.max_length:
        found.append(f"{consist.length} is longer than the {over.max_length} siding allows")
    return found


def clears(consist: Consist, over: District) -> bool:
    return not check(consist, over)


def wheel_report(consist: Consist) -> list[str]:
    """The list the conductor carries, head end first."""
    table = Table(
        [
            Column("pos", Align.RIGHT),
            Column("car"),
            Column("lading"),
            Column("tons", Align.RIGHT),
            Column("length"),
        ]
    )
    for at, one in enumerate(consist.positions, start=1):
        table.add(
            str(at),
            str(one.car.mark),
            one.lading.commodity.name if one.lading else "empty",
            str(one.weight.pounds // POUNDS_PER_TON),
            str(one.car.outside_length),
        )
    table.total(
        f"{consist.cars} cars",
        f"{consist.loads} loads {consist.empties} empties",
        "",
        str(consist.tons),
        str(consist.length),
    )
    return [*heading(f"wheel report {consist.symbol}", str(consist.day)), *table.lines()]
