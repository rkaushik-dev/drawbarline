"""The event table.

One row an event and nothing derived. The table carries a surrogate key
because the natural key would have to be the whole row, and it carries a
unique constraint over the fields that make an event what it is so a duplicate
interchange sheet is refused by the database as well as by the log.

The optional columns are optional because the kinds differ, not because the
data is missing. A road on a placement or a track on an interchange would both
be nonsense, and the domain refuses them on the way in. The table does not try
to express that: a check constraint spelling out which kinds allow which
columns would be thirteen clauses that have to be edited every time a kind is
added, and it would still not be the whole rule.
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import CheckConstraint, ForeignKey, Index, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from drawbarline.events.kinds import EventKind
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.persistence.base import Base
from drawbarline.persistence.types import CarMarkText, MarkText, Utc


class EventRow(Base):
    """One thing that happened to one car."""

    __tablename__ = "car_event"
    __table_args__ = (
        UniqueConstraint("car_mark", "kind", "happened", "station", name="uq_event_not_twice"),
        CheckConstraint("tenths is null or tenths > 0", name="ck_event_movement_covers_ground"),
        CheckConstraint("waybill is null or waybill > 0", name="ck_event_waybill_numbered"),
        Index("ix_event_when", "happened"),
        Index("ix_event_car_when", "car_mark", "happened"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    car_mark: Mapped[CarMark] = mapped_column(
        CarMarkText, ForeignKey("car.mark", ondelete="RESTRICT"), index=True
    )
    kind: Mapped[EventKind] = mapped_column(String(16))
    happened: Mapped[datetime] = mapped_column(Utc)
    station: Mapped[str] = mapped_column(String(4))
    road: Mapped[ReportingMark | None] = mapped_column(MarkText, default=None)
    industry: Mapped[str | None] = mapped_column(String(12), default=None)
    waybill: Mapped[int | None] = mapped_column(default=None)
    tenths: Mapped[int | None] = mapped_column(default=None)
    note: Mapped[str] = mapped_column(String(120), default="")

    def __repr__(self) -> str:
        return f"<EventRow {self.car_mark} {self.kind} {self.happened:%Y-%m-%d}>"
