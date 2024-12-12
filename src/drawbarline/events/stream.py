"""The event log, and one car's slice of it.

Events arrive out of order. A junction sends its interchange sheet the next
morning, a conductor turns in a wheel report two days late, and a correction
for last Tuesday lands on Friday. So the log is not an append only list that
trusts arrival order. It is a set that keeps itself sorted by the event's own
moment, and reading it back always gives the same sequence whatever order the
paper came in.

The one thing the log refuses is the same event twice. Duplicate interchange
sheets are the single most common thing a connection sends us, and a second
copy of a receipt would open a second car hire liability against the same car.
"""

from __future__ import annotations

import bisect
from collections.abc import Iterator

from drawbarline.clock import Interval
from drawbarline.errors import EventError
from drawbarline.events.kinds import EventKind
from drawbarline.events.record import Event
from drawbarline.marks import CarMark


class CarStream:
    """Every event recorded against one car, in the order they happened."""

    def __init__(self, car: CarMark) -> None:
        self.car = car
        self._events: list[Event] = []
        self._keys: list[tuple] = []
        self._seen: set[Event] = set()

    def record(self, event: Event) -> Event:
        if event.car != self.car:
            raise EventError(
                "that event belongs to another car",
                stream=str(self.car),
                event=str(event.car),
            )
        if event in self._seen:
            raise EventError("that event is already on the stream", event=str(event))
        where = bisect.bisect_right(self._keys, event.order)
        self._keys.insert(where, event.order)
        self._events.insert(where, event)
        self._seen.add(event)
        return event

    def of_kind(self, kind: EventKind) -> list[Event]:
        return [event for event in self._events if event.kind is kind]

    def last_of(self, kind: EventKind) -> Event | None:
        for event in reversed(self._events):
            if event.kind is kind:
                return event
        return None

    def first_of(self, kind: EventKind) -> Event | None:
        for event in self._events:
            if event.kind is kind:
                return event
        return None

    def during(self, window: Interval) -> list[Event]:
        return [event for event in self._events if window.contains(event.when)]

    def up_to(self, window_end: Interval) -> list[Event]:
        """Everything at or before the end of a window, which is how a state is rebuilt."""
        return [event for event in self._events if event.when < window_end.end]

    @property
    def first(self) -> Event | None:
        return self._events[0] if self._events else None

    @property
    def last(self) -> Event | None:
        return self._events[-1] if self._events else None

    def __iter__(self) -> Iterator[Event]:
        return iter(list(self._events))

    def __len__(self) -> int:
        return len(self._events)


class EventLog:
    """Every event for every car."""

    def __init__(self) -> None:
        self._streams: dict[CarMark, CarStream] = {}

    def record(self, event: Event) -> Event:
        stream = self._streams.get(event.car)
        if stream is None:
            stream = CarStream(event.car)
            self._streams[event.car] = stream
        return stream.record(event)

    def record_all(self, events: list[Event]) -> int:
        for event in events:
            self.record(event)
        return len(events)

    def stream(self, car: CarMark) -> CarStream:
        """One car's stream, empty rather than missing when nothing has happened."""
        return self._streams.get(car) or CarStream(car)

    def has(self, car: CarMark) -> bool:
        return car in self._streams

    def cars(self) -> list[CarMark]:
        return sorted(self._streams)

    def during(self, window: Interval) -> list[Event]:
        found = [event for stream in self._streams.values() for event in stream.during(window)]
        return sorted(found, key=lambda event: event.order)

    def at_station(self, station: str) -> list[Event]:
        found = [
            event
            for stream in self._streams.values()
            for event in stream
            if event.station == station
        ]
        return sorted(found, key=lambda event: event.order)

    def of_kind(self, kind: EventKind) -> list[Event]:
        found = [event for stream in self._streams.values() for event in stream.of_kind(kind)]
        return sorted(found, key=lambda event: event.order)

    def __len__(self) -> int:
        return sum(len(stream) for stream in self._streams.values())
