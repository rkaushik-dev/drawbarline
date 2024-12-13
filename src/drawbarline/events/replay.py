"""Rebuilding what a car is doing from what has happened to it.

Nothing stores a car's status. It is replayed out of the stream every time it
is asked for, which means a correction filed for last Tuesday changes today's
answer without anybody having to remember to go and update a row.

Replay is also where the sequence is judged. A stream will hold any event it
is handed, because a clerk keying an interchange sheet should not be stopped
by an argument about a movement three weeks ago. Replay is where a car
delivered twice, or placed while it is sitting on somebody else's railroad,
turns into a refusal with the offending event named.
"""

from __future__ import annotations

from dataclasses import dataclass, replace
from datetime import datetime

from drawbarline.errors import EventError
from drawbarline.events.kinds import EventKind
from drawbarline.events.record import Event
from drawbarline.events.stream import CarStream
from drawbarline.marks import CarMark


@dataclass(frozen=True, slots=True)
class CarState:
    """Where a car is and what it is doing, as of the last event applied."""

    car: CarMark
    as_of: datetime | None = None
    on_line: bool = False
    station: str | None = None
    industry: str | None = None
    waybill: int | None = None
    bad_ordered: bool = False
    held: bool = False
    received_at: datetime | None = None
    placed_at: datetime | None = None
    released_at: datetime | None = None
    tenths_run: int = 0
    applied: int = 0

    @property
    def placed(self) -> bool:
        return self.industry is not None

    @property
    def awaiting_pull(self) -> bool:
        """Released by the shipper and still sitting on their track."""
        return self.placed and self.released_at is not None

    @property
    def movable(self) -> bool:
        return self.on_line and not self.bad_ordered and not self.held

    def __str__(self) -> str:
        if not self.on_line:
            return f"{self.car} off line"
        where = self.industry or self.station or "somewhere"
        return f"{self.car} at {where}"


def _refuse(event: Event, why: str) -> EventError:
    return EventError(why, event=str(event))


def _apply(state: CarState, event: Event) -> CarState:
    kind = event.kind
    if kind is EventKind.RECEIVED:
        if state.on_line:
            raise _refuse(event, "the car is already on our rail")
        return replace(
            state,
            on_line=True,
            station=event.station,
            industry=None,
            waybill=event.waybill,
            received_at=event.when,
            placed_at=None,
            released_at=None,
            as_of=event.when,
            applied=state.applied + 1,
        )

    if not state.on_line:
        raise _refuse(event, "the car is not on our rail")

    if kind is EventKind.DELIVERED:
        if state.placed:
            raise _refuse(event, "the car has to be pulled before it goes across")
        if state.bad_ordered:
            raise _refuse(event, "a bad ordered car does not go across")
        return replace(
            state,
            on_line=False,
            station=event.station,
            industry=None,
            waybill=None,
            placed_at=None,
            released_at=None,
            as_of=event.when,
            applied=state.applied + 1,
        )

    if kind is EventKind.MOVED:
        if state.placed:
            raise _refuse(event, "the car has to be pulled before it moves")
        return replace(
            state,
            station=event.station,
            tenths_run=state.tenths_run + (event.tenths or 0),
            as_of=event.when,
            applied=state.applied + 1,
        )

    if kind.is_placement:
        if state.placed:
            raise _refuse(event, "the car is already placed")
        return replace(
            state,
            station=event.station,
            industry=event.industry,
            placed_at=event.when,
            released_at=None,
            as_of=event.when,
            applied=state.applied + 1,
        )

    if kind is EventKind.RELEASED:
        if not state.placed:
            raise _refuse(event, "a car has to be placed before it can be released")
        if state.released_at is not None:
            raise _refuse(event, "the car is already released")
        return replace(state, released_at=event.when, as_of=event.when, applied=state.applied + 1)

    if kind is EventKind.PULLED:
        if not state.placed:
            raise _refuse(event, "there is nothing to pull")
        return replace(
            state,
            industry=None,
            placed_at=None,
            released_at=None,
            as_of=event.when,
            applied=state.applied + 1,
        )

    if kind is EventKind.BAD_ORDERED:
        if state.bad_ordered:
            raise _refuse(event, "the car is already bad ordered")
        return replace(
            state,
            bad_ordered=True,
            station=event.station,
            as_of=event.when,
            applied=state.applied + 1,
        )

    if kind is EventKind.REPAIRED:
        if not state.bad_ordered:
            raise _refuse(event, "the car was not bad ordered")
        return replace(state, bad_ordered=False, as_of=event.when, applied=state.applied + 1)

    if kind is EventKind.HELD:
        if state.held:
            raise _refuse(event, "the car is already held")
        return replace(state, held=True, as_of=event.when, applied=state.applied + 1)

    if kind is EventKind.FREED:
        if not state.held:
            raise _refuse(event, "the car was not held")
        return replace(state, held=False, as_of=event.when, applied=state.applied + 1)

    return replace(state, station=event.station, as_of=event.when, applied=state.applied + 1)


def replay(stream: CarStream, until: datetime | None = None) -> CarState:
    """Walk a stream and hand back what the car is doing at the end of it."""
    state = CarState(stream.car)
    for event in stream:
        if until is not None and event.when > until:
            break
        state = _apply(state, event)
    return state


def state_of(stream: CarStream, car: CarMark) -> CarState:
    """Replay a stream after checking it is the car being asked about."""
    if stream.car != car:
        raise EventError(
            "that stream belongs to another car", stream=str(stream.car), asked=str(car)
        )
    return replay(stream)
