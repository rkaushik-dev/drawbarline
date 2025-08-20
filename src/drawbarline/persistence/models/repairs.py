"""The repair billing tables.

The price master is one row a job a date, the same shape as the car hire
schedule and for the same reason: a price that changed on the first of July
has to still be readable in December when somebody disputes a card written in
June.

Depreciation is by family rather than by job, so it is its own small table
keyed on the two digit family. There are about a dozen rows in it and they
change when the office manual is reissued.

Labor rates are per road because every road charges its own, and a card is
priced at the rate of the road that did the work, not ours.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import CheckConstraint, Date, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from drawbarline.marks import ReportingMark
from drawbarline.persistence.base import Base
from drawbarline.persistence.types import MarkText


class PriceLineRow(Base):
    """One priced job, from a date."""

    __tablename__ = "price_line"
    __table_args__ = (
        UniqueConstraint("job", "effective", name="uq_price_one_a_day"),
        CheckConstraint("labor_hundredths >= 0", name="ck_price_labor_not_negative"),
        CheckConstraint("material_cents >= 0", name="ck_price_material_not_negative"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    job: Mapped[str] = mapped_column(String(4), index=True)
    effective: Mapped[date] = mapped_column(Date)
    labor_hundredths: Mapped[int] = mapped_column()
    material_cents: Mapped[int] = mapped_column()

    def __repr__(self) -> str:
        return f"<PriceLineRow {self.job} from {self.effective}>"


class DepreciationRow(Base):
    """How fast one family of parts is used up."""

    __tablename__ = "depreciation"
    __table_args__ = (
        CheckConstraint("percent_a_year between 0 and 100", name="ck_depreciation_is_a_percentage"),
        CheckConstraint("floor_percent between 0 and 100", name="ck_depreciation_floor"),
    )

    family: Mapped[str] = mapped_column(String(2), primary_key=True)
    percent_a_year: Mapped[int] = mapped_column()
    floor_percent: Mapped[int] = mapped_column()

    def __repr__(self) -> str:
        return f"<DepreciationRow {self.family}>"


class LaborRateRow(Base):
    """What one road charges an hour, from a date."""

    __tablename__ = "labor_rate"
    __table_args__ = (
        UniqueConstraint("road", "effective", name="uq_labor_one_a_day"),
        CheckConstraint("cents_an_hour >= 0", name="ck_labor_not_negative"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    road: Mapped[ReportingMark] = mapped_column(MarkText, index=True)
    effective: Mapped[date] = mapped_column(Date)
    cents_an_hour: Mapped[int] = mapped_column()

    def __repr__(self) -> str:
        return f"<LaborRateRow {self.road} from {self.effective}>"
