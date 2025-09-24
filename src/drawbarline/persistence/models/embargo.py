"""The embargo table.

One row an embargo. Lifting one is an update in place rather than a second
row, which is the only place in this schema besides the counters where that
happens. It earns it: an embargo is a live instrument with an end date that is
not known when it is issued, and a second row saying the same embargo with a
different end would leave two rows that both look current.

Every restriction column is optional and at least one of them has to be set.
The check constraint says so, because an embargo with all three empty stops
the entire railroad and it is the mistake anybody makes once.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import CheckConstraint, Date, Index, String
from sqlalchemy.orm import Mapped, mapped_column

from drawbarline.marks import ReportingMark
from drawbarline.persistence.base import Base
from drawbarline.persistence.types import MarkText

RESTRICTS_SOMETHING = "station is not null or commodity_family is not null or road is not null"


class EmbargoRow(Base):
    """One restriction, from a date to a date."""

    __tablename__ = "embargo"
    __table_args__ = (
        CheckConstraint("number >= 1", name="ck_embargo_numbered_from_one"),
        CheckConstraint(RESTRICTS_SOMETHING, name="ck_embargo_restricts_something"),
        CheckConstraint("expires is null or expires >= effective", name="ck_embargo_ends_after"),
        Index("ix_embargo_effective", "effective"),
    )

    number: Mapped[int] = mapped_column(primary_key=True)
    effective: Mapped[date] = mapped_column(Date)
    reason: Mapped[str] = mapped_column(String(200))
    station: Mapped[str | None] = mapped_column(String(4), default=None)
    commodity_family: Mapped[str | None] = mapped_column(String(7), default=None)
    road: Mapped[ReportingMark | None] = mapped_column(MarkText, default=None)
    expires: Mapped[date | None] = mapped_column(Date, default=None)
    permits_allowed: Mapped[bool] = mapped_column(default=False)

    def __repr__(self) -> str:
        return f"<EmbargoRow {self.number} from {self.effective}>"
