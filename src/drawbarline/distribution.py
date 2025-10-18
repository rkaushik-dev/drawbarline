"""Car orders, and which empties go against them.

A shipper orders four covered hoppers for Tuesday. The office has eleven
empties standing about the railroad and has to decide which four go to them,
which is the whole of car distribution and it is older than the railroad.

The rule this road runs on is oldest order first, and inside a day, lowest
order number first. It is not the rule that moves the fewest cars and nobody
claims it is. It is the rule a shipper can check, and a distribution a shipper
cannot check is a distribution the shipper phones about.

An order is filled with the nearest suitable empty, measured along the track
rather than as the crow flies, because that is the car the crew can actually
get there. Where two are the same distance the lower car number wins, so two
runs of the same morning distribute the same cars.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, datetime

from drawbarline.errors import DrawbarlineError
from drawbarline.events.replay import replay
from drawbarline.events.stream import EventLog
from drawbarline.fleet.aar import CarKind
from drawbarline.fleet.register import FleetRegister
from drawbarline.marks import CarMark
from drawbarline.mileage import tenths_between


class DistributionError(DrawbarlineError):
    """An order cannot be taken or cannot be filled as asked."""

    code = "distribution"


@dataclass(frozen=True, slots=True)
class CarOrder:
    """A shipper asking for cars of a kind on a day."""

    number: int
    shipper: str
    industry: str
    kind: CarKind
    cars: int
    wanted_on: date

    def __post_init__(self) -> None:
        if self.number < 1:
            raise DistributionError("an order is numbered from one", number=self.number)
        if self.cars < 1:
            raise DistributionError("an order asks for at least one car", number=self.number)
        if not self.shipper.strip() or not self.industry.strip():
            raise DistributionError("an order names a shipper and a track", number=self.number)

    @property
    def priority(self) -> tuple[date, int]:
        """Oldest order first, then by number, which is what a shipper can check."""
        return (self.wanted_on, self.number)

    def __str__(self) -> str:
        return (
            f"order {self.number} {self.shipper} {self.cars} "
            f"{self.kind.description} for {self.wanted_on}"
        )


@dataclass(frozen=True, slots=True)
class Empty:
    """A car standing empty somewhere on the railroad."""

    car: CarMark
    kind: CarKind
    station: str

    def __str__(self) -> str:
        return f"{self.car} at {self.station}"


@dataclass(frozen=True, slots=True)
class Assignment:
    """One empty against one order."""

    order: int
    car: CarMark
    from_station: str
    to_station: str

    @property
    def tenths(self) -> int:
        return tenths_between(self.from_station, self.to_station)

    def __str__(self) -> str:
        return f"{self.car} {self.from_station} to {self.to_station} for order {self.order}"


@dataclass(slots=True)
class Distribution:
    """What went where, and what could not be filled."""

    assignments: list[Assignment] = field(default_factory=list)
    short: dict[int, int] = field(default_factory=dict)

    @property
    def filled(self) -> int:
        return len(self.assignments)

    @property
    def unfilled(self) -> int:
        return sum(self.short.values())

    @property
    def complete(self) -> bool:
        return not self.short

    def for_order(self, number: int) -> list[Assignment]:
        return [one for one in self.assignments if one.order == number]

    def empty_tenths(self) -> int:
        """How far the empties have to run, which is what this costs to do."""
        return sum(one.tenths for one in self.assignments)

    def __str__(self) -> str:
        if self.complete:
            return f"{self.filled} cars assigned, every order filled"
        return f"{self.filled} cars assigned, {self.unfilled} short"


def empties_on_hand(register: FleetRegister, log: EventLog, at: datetime) -> list[Empty]:
    """Every car on our rail, off a customer track, with nothing billed to it."""
    found = []
    for car in log.cars():
        if not register.has(car):
            continue
        state = replay(log.stream(car), until=at)
        if not state.on_line or state.placed or not state.movable:
            continue
        if state.waybill is not None:
            continue
        found.append(Empty(car=car, kind=register.get(car).kind, station=state.station or ""))
    return sorted(found, key=lambda one: str(one.car))


def distribute(
    orders: list[CarOrder], empties: list[Empty], stations: dict[str, str]
) -> Distribution:
    """Fill the orders oldest first, each from the nearest suitable empty."""
    left = list(empties)
    found = Distribution()
    for order in sorted(orders, key=lambda one: one.priority):
        wanted = stations.get(order.industry)
        if wanted is None:
            raise DistributionError(
                "no station for that track", order=order.number, industry=order.industry
            )
        for _ in range(order.cars):
            suitable = [one for one in left if one.kind is order.kind]
            if not suitable:
                found.short[order.number] = found.short.get(order.number, 0) + 1
                continue
            chosen = min(
                suitable,
                key=lambda one: (tenths_between(one.station, wanted), str(one.car)),
            )
            left.remove(chosen)
            found.assignments.append(Assignment(order.number, chosen.car, chosen.station, wanted))
    return found
