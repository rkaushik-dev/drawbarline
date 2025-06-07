"""Event routes.

A moment arrives either as a local clock reading at a station, which is what a
clerk has, or as an instant, which is what another system has. Both are
accepted and exactly one of them is required, because a body carrying both is
a body where the two disagree and nobody notices.

The state route replays the stream rather than reading a status column, so a
correction filed against last Tuesday changes the answer immediately and there
is nothing to rebuild.
"""

from __future__ import annotations

from datetime import datetime

from fastapi import APIRouter, Depends, status
from pydantic import BaseModel, ConfigDict, model_validator

from drawbarline.api.deps import read_only, unit_of_work
from drawbarline.clock import require_aware, to_utc
from drawbarline.errors import EventError
from drawbarline.events.kinds import kind_named
from drawbarline.events.record import Event
from drawbarline.events.replay import CarState, replay
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.persistence.uow import UnitOfWork
from drawbarline.reference import drawbar_line

router = APIRouter(prefix="/events", tags=["events"])


class EventIn(BaseModel):
    """One event as it is recorded."""

    model_config = ConfigDict(extra="forbid")

    car: str
    kind: str
    station: str
    local: datetime | None = None
    moment: datetime | None = None
    second_pass: bool = False
    road: str | None = None
    industry: str | None = None
    waybill: int | None = None
    tenths: int | None = None
    note: str = ""

    @model_validator(mode="after")
    def one_clock_or_the_other(self) -> EventIn:
        if (self.local is None) == (self.moment is None):
            raise ValueError("give either a local reading or a moment, not both")
        return self


class EventOut(BaseModel):
    car: str
    kind: str
    station: str
    moment: datetime
    road: str | None
    industry: str | None
    waybill: int | None
    tenths: int | None
    note: str

    @classmethod
    def of(cls, event: Event) -> EventOut:
        return cls(
            car=str(event.car),
            kind=str(event.kind),
            station=event.station,
            moment=event.when,
            road=str(event.road) if event.road else None,
            industry=event.industry,
            waybill=event.waybill,
            tenths=event.tenths,
            note=event.note,
        )


class StateOut(BaseModel):
    car: str
    on_line: bool
    station: str | None
    industry: str | None
    waybill: int | None
    bad_ordered: bool
    held: bool
    movable: bool
    tenths_run: int
    events: int

    @classmethod
    def of(cls, state: CarState) -> StateOut:
        return cls(
            car=str(state.car),
            on_line=state.on_line,
            station=state.station,
            industry=state.industry,
            waybill=state.waybill,
            bad_ordered=state.bad_ordered,
            held=state.held,
            movable=state.movable,
            tenths_run=state.tenths_run,
            events=state.applied,
        )


def event_of(filing: EventIn) -> Event:
    station = filing.station.strip().upper()
    if filing.local is not None:
        if filing.local.tzinfo is not None:
            raise EventError("a local reading carries no zone", station=station)
        when = to_utc(drawbar_line().station(station), filing.local, second_pass=filing.second_pass)
    else:
        assert filing.moment is not None
        when = require_aware(filing.moment, "moment")
    return Event(
        kind=kind_named(filing.kind),
        car=CarMark.parse(filing.car),
        when=when,
        station=station,
        road=ReportingMark.parse(filing.road) if filing.road else None,
        industry=filing.industry.strip().upper() if filing.industry else None,
        waybill=filing.waybill,
        tenths=filing.tenths,
        note=filing.note,
    )


@router.post("", status_code=status.HTTP_201_CREATED, response_model=EventOut)
def record(filing: EventIn, work: UnitOfWork = Depends(unit_of_work)) -> EventOut:
    """Record one event against one car."""
    return EventOut.of(work.events.record(event_of(filing)))


@router.post("/batch", status_code=status.HTTP_201_CREATED)
def record_batch(
    filings: list[EventIn], work: UnitOfWork = Depends(unit_of_work)
) -> dict[str, int]:
    """A day of interchange in one transaction, all of it or none of it."""
    for filing in filings:
        work.events.record(event_of(filing))
    return {"recorded": len(filings)}


@router.get("/cars/{mark}", response_model=list[EventOut])
def stream_of(mark: str, work: UnitOfWork = Depends(read_only)) -> list[EventOut]:
    """One car's stream, in the order it happened."""
    return [EventOut.of(event) for event in work.events.stream(CarMark.parse(mark))]


@router.get("/cars/{mark}/state", response_model=StateOut)
def state_of(mark: str, work: UnitOfWork = Depends(read_only)) -> StateOut:
    """What the car is doing, replayed from its stream."""
    return StateOut.of(replay(work.events.stream(CarMark.parse(mark))))
