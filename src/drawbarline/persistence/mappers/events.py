"""Events, in both directions.

The kind is stored as its name rather than its number, because a number would
have to stay stable forever and a name is what somebody reading the table with
a query tool needs to see. The cost is four bytes a row and the benefit is
that a database dump is legible.
"""

from __future__ import annotations

from drawbarline.events.kinds import EventKind, kind_named
from drawbarline.events.record import Event
from drawbarline.persistence.models.events import EventRow


def event_from_row(row: EventRow) -> Event:
    kind = row.kind if isinstance(row.kind, EventKind) else kind_named(str(row.kind))
    return Event(
        kind=kind,
        car=row.car_mark,
        when=row.happened,
        station=row.station,
        road=row.road,
        industry=row.industry,
        waybill=row.waybill,
        tenths=row.tenths,
        note=row.note,
    )


def row_from_event(event: Event) -> EventRow:
    return EventRow(
        car_mark=event.car,
        kind=event.kind.name,
        happened=event.when,
        station=event.station,
        road=event.road,
        industry=event.industry,
        waybill=event.waybill,
        tenths=event.tenths,
        note=event.note,
    )
