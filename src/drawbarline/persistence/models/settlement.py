"""The settlement tables.

Postings are the ledger and they are never updated. A posting that turns out
to be wrong is answered with another posting, because the month it was in has
been agreed with another railroad and quietly editing a row means our copy and
theirs stop matching with nothing to show why.

The period is stored as a year and a month rather than a date, because a month
is what is closed, and a date column invites somebody to close the fourteenth.
Closed months are rows, so a month nobody has closed is simply absent, and the
reopenings are their own table with the reason on them.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import CheckConstraint, Date, Index, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from drawbarline.marks import ReportingMark
from drawbarline.money import Money
from drawbarline.persistence.base import Base
from drawbarline.persistence.types import MarkText, MoneyCents

MONTH_CHECK = "period_month between 1 and 12"


class PostingRow(Base):
    """One line of the account with one road."""

    __tablename__ = "posting"
    __table_args__ = (
        CheckConstraint(MONTH_CHECK, name="ck_posting_month"),
        Index("ix_posting_road_period", "road", "period_year", "period_month"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    road: Mapped[ReportingMark] = mapped_column(MarkText, index=True)
    period_year: Mapped[int] = mapped_column()
    period_month: Mapped[int] = mapped_column()
    kind: Mapped[str] = mapped_column(String(12))
    amount: Mapped[Money] = mapped_column(MoneyCents)
    posted_on: Mapped[date] = mapped_column(Date)
    reference: Mapped[str] = mapped_column(String(60), default="")

    def __repr__(self) -> str:
        month = f"{self.period_year}-{self.period_month:02d}"
        return f"<PostingRow {self.road} {month} {self.kind}>"


class ClosedPeriodRow(Base):
    """A month that is shut to posting."""

    __tablename__ = "closed_period"
    __table_args__ = (
        UniqueConstraint("period_year", "period_month", name="uq_closed_once"),
        CheckConstraint(MONTH_CHECK, name="ck_closed_month"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    period_year: Mapped[int] = mapped_column()
    period_month: Mapped[int] = mapped_column()
    closed_on: Mapped[date] = mapped_column(Date)

    def __repr__(self) -> str:
        return f"<ClosedPeriodRow {self.period_year}-{self.period_month:02d}>"


class ReopeningRow(Base):
    """A month opened again, and the reason it had to be."""

    __tablename__ = "reopening"
    __table_args__ = (CheckConstraint(MONTH_CHECK, name="ck_reopening_month"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    period_year: Mapped[int] = mapped_column()
    period_month: Mapped[int] = mapped_column()
    reopened_on: Mapped[date] = mapped_column(Date)
    reason: Mapped[str] = mapped_column(String(200))

    def __repr__(self) -> str:
        return f"<ReopeningRow {self.period_year}-{self.period_month:02d}>"
