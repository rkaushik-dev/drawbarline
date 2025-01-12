"""Turning a stream of events into the spans somebody is charged for.

Two spans matter and they are not the same shape. A car is on our rail from
the moment we take it at a junction until the moment we hand it back, and we
owe the owner for every hour of that. A car is on a customer track from the
moment it is placed until the moment we pull it, and they owe us for the days
of that.

Underneath they are the same walk. Something opens, something else closes it,
and whatever is still open when the month ends is cut at the month end. That
walk is :func:`scan_pairs` and everything here is a reading of it. Writing it
three times, which is what this file used to do, meant three places to fix
when a stream turned out to hold an opening event with no close.

Both kinds can be open at the end of a month, which is the whole reason a
month closes at all. An open span is closed at the window end for the purpose
of the bill and reopened at the window start of the next one, and the two
halves have to add up to the same total as one uninterrupted span would.
"""

from __future__ import annotations

from collections.abc import Iterator
from dataclasses import dataclass
from datetime import datetime

from drawbarline.clock import Interval
from drawbarline.errors import EventError
from drawbarline.events.kinds import EventKind
from drawbarline.events.record import Event
from drawbarline.events.stream import CarStream
from drawbarline.marks import CarMark, ReportingMark


@dataclass(frozen=True, slots=True)
class Pair:
    """An opening event, whatever closed it, and the time between."""

    opened_by: Event
    closed_by: Event | None
    interval: Interval
    inside: tuple[Event, ...] = ()

    @property
    def open(self) -> bool:
        return self.closed_by is None

    def first_inside(self, kind: EventKind) -> Event | None:
        for event in self.inside:
            if event.kind is kind:
                return event
        return None


def scan_pairs(
    stream: CarStream,
    opens: frozenset[EventKind],
    closes: frozenset[EventKind],
    until: datetime,
    *,
    watch: frozenset[EventKind] = frozenset(),
    what: str = "that state",
) -> Iterator[Pair]:
    """Walk a stream pairing openings with closings, cutting the last at a cut off."""
    opened: Event | None = None
    seen: list[Event] = []
    for event in stream:
        if event.when > until:
            break
        if event.kind in opens:
            if opened is not None:
                raise EventError(f"{what} was opened twice running", event=str(event))
            opened = event
            seen = []
        elif event.kind in closes:
            if opened is None:
                raise EventError(f"{what} was closed without opening", event=str(event))
            yield Pair(opened, event, Interval(opened.when, event.when), tuple(seen))
            opened = None
            seen = []
        elif opened is not None and event.kind in watch:
            seen.append(event)
    if opened is not None:
        yield Pair(opened, None, Interval(opened.when, until), tuple(seen))


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
    pairs = scan_pairs(
        stream,
        frozenset({EventKind.RECEIVED}),
        frozenset({EventKind.DELIVERED}),
        until,
        what="the car",
    )
    return [
        OnLine(
            car=stream.car,
            interval=pair.interval,
            opened_by=pair.opened_by,
            closed_by=pair.closed_by,
            received_from=pair.opened_by.road,
            delivered_to=pair.closed_by.road if pair.closed_by else None,
        )
        for pair in pairs
    ]


def on_track_spans(stream: CarStream, until: datetime) -> list[OnTrack]:
    """Every stretch the car sat on a customer track, up to a cut off."""
    pairs = scan_pairs(
        stream,
        frozenset({EventKind.PLACED, EventKind.CONSTRUCTIVE}),
        frozenset({EventKind.PULLED}),
        until,
        watch=frozenset({EventKind.RELEASED}),
        what="the placement",
    )
    found = []
    for pair in pairs:
        released = pair.first_inside(EventKind.RELEASED)
        found.append(
            OnTrack(
                car=stream.car,
                interval=pair.interval,
                opened_by=pair.opened_by,
                closed_by=pair.closed_by,
                industry=pair.opened_by.industry or "",
                constructive=pair.opened_by.kind is EventKind.CONSTRUCTIVE,
                released_at=released.when if released else None,
            )
        )
    return found


def within(spans: list[Span], window: Interval) -> list[Span]:
    """Only the spans that touch a window, each cut down to it."""
    return [
        span.clipped(window)
        for span in spans
        if span.interval.overlaps(window) or span.interval.empty
    ]
