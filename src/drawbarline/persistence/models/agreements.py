"""The interchange agreement table.

One row an agreement, and the kinds of car it takes are a string of the kind
letters rather than a join table. There are ten kinds, an agreement takes a
handful, and a five row join table per agreement would be correct and unread.

Hours are stored as times rather than as a number of minutes, because the
agreement says seven in the morning to seven at night and storing four hundred
and twenty means somebody has to divide before they can check it against the
paper.
"""

from __future__ import annotations

from datetime import date, time

from sqlalchemy import CheckConstraint, Date, Index, String, Time, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from drawbarline.limits import STATION_CODE
from drawbarline.marks import ReportingMark
from drawbarline.persistence.base import Base
from drawbarline.persistence.types import MarkText


class AgreementRow(Base):
    """What one road takes at one junction."""

    __tablename__ = "interchange_agreement"
    __table_args__ = (
        UniqueConstraint("road", "junction", "effective", name="uq_agreement_one_a_day"),
        CheckConstraint("daily_cap >= 1", name="ck_agreement_takes_a_car"),
        CheckConstraint("length(kinds) > 0", name="ck_agreement_takes_a_kind"),
        CheckConstraint("opens < closes", name="ck_agreement_opens_first"),
        CheckConstraint("expires is null or expires >= effective", name="ck_agreement_ends_after"),
        Index("ix_agreement_junction", "junction", "effective"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    road: Mapped[ReportingMark] = mapped_column(MarkText, index=True)
    junction: Mapped[str] = mapped_column(String(STATION_CODE))
    effective: Mapped[date] = mapped_column(Date)
    kinds: Mapped[str] = mapped_column(String(16))
    daily_cap: Mapped[int] = mapped_column()
    opens: Mapped[time] = mapped_column(Time)
    closes: Mapped[time] = mapped_column(Time)
    hazardous: Mapped[bool] = mapped_column(default=False)
    expires: Mapped[date | None] = mapped_column(Date, default=None)

    def __repr__(self) -> str:
        return f"<AgreementRow {self.junction}/{self.road} from {self.effective}>"
