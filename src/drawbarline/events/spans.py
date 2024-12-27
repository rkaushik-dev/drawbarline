"""Turning a stream of events into the spans somebody is charged for.

Two spans matter and they are not the same shape. A car is on our rail from
the moment we take it at a junction until the moment we hand it back, and we
owe the owner for every hour of that. A car is on a customer track from the
moment it is placed until the moment they release it, and they owe us for the
days of that.

Both can be open at the end of a month, which is the whole reason a month
closes at all. An open span is closed at the window end for the purpose of the
bill and reopened at the window start of the next one, and the two halves have
to add up to the same total as one uninterrupted span would. That is the
property the tests hold onto.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime

from drawbarline.clock import Interval
from drawbarline.errors import EventError
from drawbarline.events.kinds import EventKind
from drawbarline.events.record import Event
from drawbarline.events.stream import CarStream
from drawbarline.marks import CarMark, ReportingMark


@dataclass(frozen=True, slots=True)
class Span:
    """A stretch of time somebody is accountable for."""

    car: CarMark
    interval: Interval
    opened_by: Event
    closed_by: Event | None = None

    @property
    def open(self) -> bool:
        return self.closed_by is None

    @property
    def minutes(self) -> int:
        return self.interval.minutes

    def clipped(self, window: Interval) -> Span:
        """The part inside a window, still remembering what opened it."""
        return Span(self.car, self.interval.clip(window), self.opened_by, self.closed_by)

    def __str__(self) -> str:
        tail = "open" if self.open else "closed"
        return f"{self.car} {self.interval} {tail}"


@dataclass(frozen=True, slots=True)
class OnLine(Span):
    """A span on our rail, which is what car hire is computed over."""

    received_from: ReportingMark | None = None
    delivered_to: ReportingMark | None = None


@dataclass(frozen=True, slots=True)
class OnTrack(Span):
    """A span on a customer track, which is what demurrage is counted over."""

    industry: str = ""
    constructive: bool = False
    released_at: datetime | None = None


def on_line_spans(stream: CarStream, until: datetime) -> list[OnLine]:
    """Every stretch the car sat on our rail, up to a cut off."""
    spans: list[OnLine] = []
    opened: Event | None = None
    for event in stream:
        if event.when > until:
            break
        if event.kind is EventKind.RECEIVED:
            if opened is not None:
                raise EventError("the car was received twice running", event=str(event))
            opened = event
        elif event.kind is EventKind.DELIVERED:
            if opened is None:
                raise EventError("the car was delivered without a receipt", event=str(event))
            spans.append(
                OnLine(
                    car=stream.car,
                    interval=Interval(opened.when, event.when),
                    opened_by=opened,
                    closed_by=event,
                    received_from=opened.road,
                    delivered_to=event.road,
                )
            )
            opened = None
    if opened is not None:
        spans.append(
            OnLine(
                car=stream.car,
                interval=Interval(opened.when, until),
                opened_by=opened,
                received_from=opened.road,
            )
        )
    return spans


def on_track_spans(stream: CarStream, until: datetime) -> list[OnTrack]:
    """Every stretch the car sat on a customer track, up to a cut off."""
    spans: list[OnTrack] = []
    opened: Event | None = None
    released: datetime | None = None
    for event in stream:
        if event.when > until:
            break
        if event.kind.is_placement:
            if opened is not None:
                raise EventError("the car was placed twice running", event=str(event))
            opened = event
            released = None
        elif event.kind is EventKind.RELEASED and opened is not None:
            released = event.when
        elif event.kind is EventKind.PULLED and opened is not None:
            spans.append(
                OnTrack(
                    car=stream.car,
                    interval=Interval(opened.when, event.when),
                    opened_by=opened,
                    closed_by=event,
                    industry=opened.industry or "",
                    constructive=opened.kind is EventKind.CONSTRUCTIVE,
                    released_at=released,
                )
            )
            opened = None
            released = None
    if opened is not None:
        spans.append(
            OnTrack(
                car=stream.car,
                interval=Interval(opened.when, until),
                opened_by=opened,
                industry=opened.industry or "",
                constructive=opened.kind is EventKind.CONSTRUCTIVE,
                released_at=released,
            )
        )
    return spans


def within(spans: list[Span], window: Interval) -> list[Span]:
    """Only the spans that touch a window, each cut down to it."""
    return [
        span.clipped(window)
        for span in spans
        if span.interval.overlaps(window) or span.interval.empty
    ]
