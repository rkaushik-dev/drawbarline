"""The event repository.

Reading is by car and by window, because those are the two questions asked.
Every read comes back as domain events in the log's own order, so a caller
cannot tell whether the events came out of a database or out of a test.

Writing refuses a duplicate before the database does, so the message says what
happened rather than naming a constraint. The constraint is still there, and
it is what catches two clerks keying the same sheet at the same moment. The
same goes for an event against a car with no equipment record: the foreign key
would catch it, but a foreign key violation reaches a caller as a five hundred
and tells them nothing.
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from drawbarline.clock import Interval
from drawbarline.errors import EventError, UnknownCar
from drawbarline.events.kinds import EventKind
from drawbarline.events.record import Event
from drawbarline.events.stream import CarStream, EventLog
from drawbarline.marks import CarMark
from drawbarline.persistence.mappers.events import event_from_row, row_from_event
from drawbarline.persistence.models.events import EventRow
from drawbarline.persistence.models.fleet import CarRow


class EventRepository:
    """Every event, on the other side of a session."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def record(self, event: Event) -> Event:
        if self.session.get(CarRow, event.car) is None:
            raise UnknownCar("no record of that car", car=str(event.car))
        standing = self.session.execute(
            select(EventRow).where(
                EventRow.car_mark == event.car,
                EventRow.kind == event.kind.name,
                EventRow.happened == event.when,
                EventRow.station == event.station,
            )
        ).first()
        if standing is not None:
            raise EventError("that event is already on the stream", event=str(event))
        self.session.add(row_from_event(event))
        self.session.flush()
        return event

    def record_all(self, events: list[Event]) -> int:
        for event in events:
            self.record(event)
        return len(events)

    def _rows_for(self, car: CarMark) -> list[EventRow]:
        found = self.session.execute(
            select(EventRow).where(EventRow.car_mark == car).order_by(EventRow.happened)
        ).scalars()
        return list(found)

    def stream(self, car: CarMark) -> CarStream:
        stream = CarStream(car)
        for row in self._rows_for(car):
            stream.record(event_from_row(row))
        return stream

    def during(self, window: Interval) -> list[Event]:
        rows = self.session.execute(
            select(EventRow)
            .where(EventRow.happened >= window.start, EventRow.happened < window.end)
            .order_by(EventRow.happened)
        ).scalars()
        return [event_from_row(row) for row in rows]

    def of_kind(self, kind: EventKind) -> list[Event]:
        rows = self.session.execute(
            select(EventRow).where(EventRow.kind == kind.name).order_by(EventRow.happened)
        ).scalars()
        return [event_from_row(row) for row in rows]

    def cars(self) -> list[CarMark]:
        found = self.session.execute(
            select(EventRow.car_mark).distinct().order_by(EventRow.car_mark)
        ).scalars()
        return list(found)

    def last_moment(self) -> datetime | None:
        return self.session.execute(select(func.max(EventRow.happened))).scalar()

    def log(self) -> EventLog:
        """Build the in memory log the close and the reports work against.

        One query. This used to ask for the distinct cars and then read each
        car's stream, which is a query a car, and a close reads the whole log
        four times. On a month with three hundred cars that was twelve hundred
        round trips to answer a question one select answers.
        """
        found = EventLog()
        rows = self.session.execute(
            select(EventRow).order_by(EventRow.car_mark, EventRow.happened)
        ).scalars()
        for row in rows:
            found.record(event_from_row(row))
        return found

    def __len__(self) -> int:
        return int(self.session.execute(select(func.count(EventRow.id))).scalar() or 0)
