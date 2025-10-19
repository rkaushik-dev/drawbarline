"""The car order table.

One row an order, with the day it was placed as well as the day the cars are
wanted for. Both are needed: the wanted day is what the distribution sorts on,
and the placed day is what a shipper points at when they say they asked first.

An order is never edited. A shipper who wants fewer cars cancels and orders
again, which is what they do on the telephone anyway, and it leaves a record
of both.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import CheckConstraint, Date, ForeignKey, Index, String
from sqlalchemy.orm import Mapped, mapped_column

from drawbarline.persistence.base import Base


class CarOrderRow(Base):
    """A shipper asking for cars of a kind on a day."""

    __tablename__ = "car_order"
    __table_args__ = (
        CheckConstraint("number >= 1", name="ck_order_numbered_from_one"),
        CheckConstraint("cars >= 1", name="ck_order_asks_for_a_car"),
        Index("ix_order_wanted", "wanted_on"),
    )

    number: Mapped[int] = mapped_column(primary_key=True)
    shipper_code: Mapped[str] = mapped_column(
        String(8), ForeignKey("shipper.code", ondelete="RESTRICT"), index=True
    )
    industry_code: Mapped[str] = mapped_column(
        String(12), ForeignKey("industry.code", ondelete="RESTRICT"), index=True
    )
    kind: Mapped[str] = mapped_column(String(1))
    cars: Mapped[int] = mapped_column()
    wanted_on: Mapped[date] = mapped_column(Date)
    placed_on: Mapped[date] = mapped_column(Date)
    cancelled_on: Mapped[date | None] = mapped_column(Date, default=None)

    def __repr__(self) -> str:
        return f"<CarOrderRow {self.number} {self.shipper_code} {self.cars}>"
