"""Building the switch list for a station.

The local works one station at a time. It pulls the cars the customers have
released, then it spots the cars the yard has for them, and the order matters:
a track with eleven spots and eleven cars on it has room for a twelfth only
because the crew took one off first. Planning the placements before the pulls
gives a list with constructive placements on it that would not have happened,
and the customer is charged for them.

Three things stop a car and each of them reads differently on the list. A car
that is bad ordered or held does not move at all. A track we do not switch
today is not worked. A track that is genuinely full takes a constructive
placement, which starts the customer's clock without the car ever being spotted.

Nothing in here records anything. It produces a list, the crew works it, and
what actually happened comes back as events. A plan that wrote events would be
a plan that had already happened.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime
from enum import Enum

from drawbarline.customers import CustomerRegister
from drawbarline.errors import DrawbarlineError
from drawbarline.events.replay import CarState, replay
from drawbarline.events.stream import EventLog
from drawbarline.marks import CarMark


class SwitchError(DrawbarlineError):
    """A switch list cannot be built as asked."""

    code = "switching"


class MoveKind(Enum):
    """What the crew does with one car."""

    PULL = "pull"
    PLACE = "place"
    CONSTRUCTIVE = "constructive"
    NOT_SWITCHED = "not switched today"
    STOPPED = "stopped"

    @property
    def text(self) -> str:
        return self.value

    @property
    def worked(self) -> bool:
        """Whether the crew actually touches the car."""
        return self in (MoveKind.PULL, MoveKind.PLACE)


@dataclass(frozen=True, slots=True)
class Move:
    """One line of a switch list."""

    car: CarMark
    kind: MoveKind
    industry: str
    note: str = ""

    def __str__(self) -> str:
        tail = f" ({self.note})" if self.note else ""
        return f"{self.kind.text} {self.car} {self.industry}{tail}"


@dataclass(frozen=True, slots=True)
class SwitchList:
    """Everything the local does at one station on one day."""

    station: str
    day: date
    moves: tuple[Move, ...]

    @property
    def pulls(self) -> list[Move]:
        return [one for one in self.moves if one.kind is MoveKind.PULL]

    @property
    def placements(self) -> list[Move]:
        return [one for one in self.moves if one.kind is MoveKind.PLACE]

    @property
    def constructive(self) -> list[Move]:
        return [one for one in self.moves if one.kind is MoveKind.CONSTRUCTIVE]

    @property
    def stopped(self) -> list[Move]:
        return [one for one in self.moves if not one.kind.worked]

    @property
    def worked(self) -> int:
        return len([one for one in self.moves if one.kind.worked])

    def __len__(self) -> int:
        return len(self.moves)


def cars_on_track(log: EventLog, industry: str, at: datetime) -> list[CarMark]:
    """Every car standing on one industry track at a moment."""
    found = []
    for car in log.cars():
        state = replay(log.stream(car), until=at)
        if state.industry == industry:
            found.append(car)
    return sorted(found)


def _why(state: CarState) -> str:
    """How a stopped car reads on the list."""
    reasons = []
    if state.bad_ordered:
        reasons.append("bad order")
    if state.held:
        reasons.append("held")
    return " and ".join(reasons)


def plan(
    log: EventLog,
    customers: CustomerRegister,
    station: str,
    day: date,
    at: datetime,
    orders: list[tuple[CarMark, str]],
) -> SwitchList:
    """Work out the list, pulls first so the placements see the room they make."""
    moves: list[Move] = []
    on_track: dict[str, list[CarMark]] = {}

    for track in customers.at_station(station):
        standing = cars_on_track(log, track.code, at)
        on_track[track.code] = list(standing)
        if not track.switched_on(day):
            continue
        for car in standing:
            state = replay(log.stream(car), until=at)
            if state.released_at is None:
                continue
            if state.bad_ordered or state.held:
                moves.append(Move(car, MoveKind.STOPPED, track.code, _why(state)))
                continue
            moves.append(Move(car, MoveKind.PULL, track.code))
            on_track[track.code].remove(car)

    for car, industry in orders:
        track = customers.industry(industry)
        if track.station != station:
            raise SwitchError(
                "that track is at another station",
                industry=industry,
                station=station,
                track_station=track.station,
            )
        state = replay(log.stream(car), until=at)
        if not state.on_line:
            raise SwitchError("that car is not on our rail", car=str(car))
        if state.bad_ordered or state.held:
            moves.append(Move(car, MoveKind.STOPPED, industry, _why(state)))
            continue
        if not track.switched_on(day):
            moves.append(Move(car, MoveKind.NOT_SWITCHED, industry))
            continue
        standing = on_track.setdefault(industry, cars_on_track(log, industry, at))
        if track.full(len(standing)):
            moves.append(Move(car, MoveKind.CONSTRUCTIVE, industry, f"{len(standing)} on hand"))
            continue
        moves.append(Move(car, MoveKind.PLACE, industry))
        standing.append(car)

    return SwitchList(station=station, day=day, moves=tuple(moves))
