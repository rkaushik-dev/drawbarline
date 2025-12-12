"""Which block a car goes in, and what order the blocks stand.

A train out of Escanaba is not a random string of cars. It is blocks, and each
block is the cars for one place or one connection, standing in the order they
will be set off. Getting the order wrong does not lose the cars, it loses an
hour at every stop while the crew digs one out of the middle.

A blocking plan is per originating station and it is a filing, not a
computation. Somebody in the operating department decides that Green Bay
blocks ahead of Marinette because the Marinette cut comes off first, and that
decision is not derivable from the mileposts. So the plan is data and this
module only applies it.

A car whose destination is in no block is a car nobody planned for. It is not
quietly put on the end, because the end is somebody else's block and it will
travel to the wrong place. It comes back as unblocked and somebody looks at
it.
"""

from __future__ import annotations

from dataclasses import dataclass, field

from drawbarline.errors import DrawbarlineError
from drawbarline.marks import CarMark
from drawbarline.mileage import milepost


class BlockingError(DrawbarlineError):
    """A block or a plan will not stand."""

    code = "blocking"


@dataclass(frozen=True, slots=True)
class Block:
    """One cut of cars for one place, and where it stands in the train."""

    code: str
    name: str
    position: int
    covers: frozenset[str]

    def __post_init__(self) -> None:
        if not self.code.isupper() or not self.code.isalnum():
            raise BlockingError("a block code is upper case", code=self.code)
        if self.position < 1:
            raise BlockingError("blocks stand from position one", code=self.code)
        if not self.covers:
            raise BlockingError("a block covers at least one place", code=self.code)
        for station in self.covers:
            milepost(station)

    def takes(self, destination: str) -> bool:
        return destination in self.covers

    def __str__(self) -> str:
        return f"{self.code} {self.name} at {self.position}"


@dataclass(frozen=True, slots=True)
class Plan:
    """The blocking for trains out of one station."""

    station: str
    blocks: tuple[Block, ...]

    def __post_init__(self) -> None:
        milepost(self.station)
        if not self.blocks:
            raise BlockingError("a plan has at least one block", station=self.station)
        codes = [one.code for one in self.blocks]
        if len(codes) != len(set(codes)):
            raise BlockingError("a block code appears twice", station=self.station)
        positions = [one.position for one in self.blocks]
        if len(positions) != len(set(positions)):
            raise BlockingError("two blocks stand in one place", station=self.station)
        covered: set[str] = set()
        for one in self.blocks:
            clash = covered & one.covers
            if clash:
                raise BlockingError(
                    "two blocks cover the same place",
                    station=self.station,
                    places=sorted(clash),
                )
            covered |= one.covers

    @property
    def standing(self) -> list[Block]:
        """The blocks head end first."""
        return sorted(self.blocks, key=lambda one: one.position)

    def block_for(self, destination: str) -> Block | None:
        for one in self.blocks:
            if one.takes(destination):
                return one
        return None

    def covers(self) -> set[str]:
        return {station for one in self.blocks for station in one.covers}


@dataclass(slots=True)
class Classification:
    """Every car put in its block, and the ones that had none."""

    blocks: dict[str, list[CarMark]] = field(default_factory=dict)
    unblocked: list[CarMark] = field(default_factory=list)

    @property
    def blocked(self) -> int:
        return sum(len(cars) for cars in self.blocks.values())

    def cars_in(self, code: str) -> list[CarMark]:
        return self.blocks.get(code, [])

    def __str__(self) -> str:
        if not self.unblocked:
            return f"{self.blocked} cars in {len(self.blocks)} blocks"
        return f"{self.blocked} cars in {len(self.blocks)} blocks, {len(self.unblocked)} unblocked"


def classify(cars: list[tuple[CarMark, str]], plan: Plan) -> Classification:
    """Put each car in its block, keeping the standing order of the blocks."""
    found = Classification()
    for one in plan.standing:
        found.blocks[one.code] = []
    for car, destination in cars:
        block = plan.block_for(destination)
        if block is None:
            found.unblocked.append(car)
            continue
        found.blocks[block.code].append(car)
    for cars_in in found.blocks.values():
        cars_in.sort(key=str)
    return found


def order_of(found: Classification, plan: Plan) -> list[CarMark]:
    """The whole train head end first, block by block."""
    ordered: list[CarMark] = []
    for one in plan.standing:
        ordered.extend(found.cars_in(one.code))
    return ordered
