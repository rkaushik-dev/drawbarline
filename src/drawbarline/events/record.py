"""One recorded event.

The record is deliberately narrow. It says what happened, to which car, where
and when, and it carries the two or three extra fields the particular kind
needs. It does not carry a status, a balance or anything else derived, because
the moment a record carries a derived field somebody updates the record
instead of adding an event and the history stops being a history.

Events recorded at the same minute have to sort the same way every time or two
runs of the same month disagree. A clerk keying a morning of work enters the
placement and the release with the same time stamp more often than you would
think, so the order inside a minute is fixed here by kind rather than left to
whatever order the rows came back in.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime

from drawbarline.clock import require_aware
from drawbarline.errors import EventError
from drawbarline.events.kinds import EventKind
from drawbarline.limits import NOTE
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.mileage import milepost

# Lower sorts earlier. A car is received before it moves, moved before it is
# placed, and released before it is pulled.
WITHIN_A_MINUTE = {
    EventKind.RECEIVED: 0,
    EventKind.MOVED: 1,
    EventKind.NOTIFIED: 2,
    EventKind.CONSTRUCTIVE: 3,
    EventKind.PLACED: 4,
    EventKind.WEIGHED: 5,
    EventKind.BAD_ORDERED: 6,
    EventKind.REPAIRED: 7,
    EventKind.HELD: 8,
    EventKind.FREED: 9,
    EventKind.RELEASED: 10,
    EventKind.PULLED: 11,
    EventKind.DELIVERED: 12,
}


@dataclass(frozen=True, slots=True)
class Event:
    """Something that happened to one car at one place at one moment."""

    kind: EventKind
    car: CarMark
    when: datetime
    station: str
    road: ReportingMark | None = None
    industry: str | None = None
    waybill: int | None = None
    tenths: int | None = None
    note: str = ""

    def __post_init__(self) -> None:
        object.__setattr__(self, "when", require_aware(self.when, "when"))
        milepost(self.station)

        if self.kind.needs_road and self.road is None:
            raise EventError("that event names the road on the other side", kind=str(self.kind))
        if not self.kind.needs_road and self.road is not None:
            raise EventError("that event has no road to name", kind=str(self.kind))

        if self.kind.needs_industry and self.industry is None:
            raise EventError("that event names the track", kind=str(self.kind))
        if not self.kind.needs_industry and self.industry is not None:
            raise EventError("that event has no track to name", kind=str(self.kind))

        if self.kind.needs_distance:
            if self.tenths is None:
                raise EventError("a movement carries the distance", kind=str(self.kind))
            if self.tenths <= 0:
                raise EventError("a movement covers ground", tenths=self.tenths)
        elif self.tenths is not None:
            raise EventError("that event covers no distance", kind=str(self.kind))

        if self.waybill is not None and self.waybill < 1:
            raise EventError("a waybill is numbered from one", waybill=self.waybill)
        if len(self.note) > NOTE:
            raise EventError("that note is too long to carry", length=len(self.note))

    @property
    def order(self) -> tuple[datetime, int, str]:
        """The sort key, which two runs of the same month have to agree on."""
        return (self.when, WITHIN_A_MINUTE[self.kind], str(self.car))

    def with_note(self, note: str) -> Event:
        """A copy carrying a note, which is how a correction is explained."""
        return Event(
            kind=self.kind,
            car=self.car,
            when=self.when,
            station=self.station,
            road=self.road,
            industry=self.industry,
            waybill=self.waybill,
            tenths=self.tenths,
            note=note,
        )

    def __str__(self) -> str:
        where = self.industry or self.station
        who = f" {self.road}" if self.road else ""
        return f"{self.when:%Y-%m-%d %H:%M}Z {self.car} {self.kind} at {where}{who}"
