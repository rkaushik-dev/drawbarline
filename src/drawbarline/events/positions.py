"""Where every car is at one moment.

Three separate places were each walking the whole log and replaying every
car's stream to answer a question about one moment: which cars are on hand,
which are standing empty, and which are on a particular industry track. Three
walks of the same data, and each of them quietly O of cars times events.

This does the walk once and hands back a mapping, and the three questions are
readings of it. On a railroad this size the difference is a second, and the
reason to do it is not the second. It is that the three answers now come from
one replay and cannot disagree about whether a car is placed.
"""

from __future__ import annotations

from datetime import datetime

from drawbarline.events.replay import CarState, replay
from drawbarline.events.stream import EventLog
from drawbarline.marks import CarMark

Positions = dict[CarMark, CarState]


def positions(log: EventLog, at: datetime) -> Positions:
    """Replay every car's stream once, up to a moment."""
    return {car: replay(log.stream(car), until=at) for car in log.cars()}


def on_line(found: Positions) -> list[CarMark]:
    """Every car we have taken and not handed back, in mark order."""
    return sorted(car for car, state in found.items() if state.on_line)


def on_track(found: Positions, industry: str) -> list[CarMark]:
    """Every car standing on one industry track."""
    return sorted(car for car, state in found.items() if state.industry == industry)


def placed(found: Positions) -> list[CarMark]:
    """Every car standing on somebody's private track."""
    return sorted(car for car, state in found.items() if state.placed)


def unplaced(found: Positions) -> list[CarMark]:
    """Every car on our rail that is not sitting on a customer track."""
    return sorted(car for car, state in found.items() if state.on_line and not state.placed)


def movable(found: Positions) -> list[CarMark]:
    """Every car that is not bad ordered or held."""
    return sorted(car for car, state in found.items() if state.movable)


def state_of(found: Positions, car: CarMark) -> CarState:
    """One car's state, empty rather than missing when nothing has happened."""
    return found.get(car) or CarState(car)
