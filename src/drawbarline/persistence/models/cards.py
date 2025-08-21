"""The repair card tables.

A card and its lines, then the exceptions filed against those lines and how
each was answered. Four tables and they are all append only. A card is never
edited: a card that was wrong is answered with an exception, and an exception
that was wrong is answered by allowing less of it.

The line a card carries is identified by its position, which is what the
exception points at. That means a card written with its lines in a different
order is a different card as far as an exception is concerned, and since a
card is never rewritten that is exactly the property wanted.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import CheckConstraint, Date, ForeignKey, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from drawbarline.marks import CarMark, ReportingMark
from drawbarline.money import Money
from drawbarline.persistence.base import Base
from drawbarline.persistence.types import CarMarkText, MarkText, MoneyCents


class RepairCardRow(Base):
    """What one road did to one car on one day."""

    __tablename__ = "repair_card"
    __table_args__ = (CheckConstraint("number >= 1", name="ck_card_numbered_from_one"),)

    number: Mapped[int] = mapped_column(primary_key=True)
    car_mark: Mapped[CarMark] = mapped_column(
        CarMarkText, ForeignKey("car.mark", ondelete="RESTRICT"), index=True
    )
    performed_by: Mapped[ReportingMark] = mapped_column(MarkText, index=True)
    performed_on: Mapped[date] = mapped_column(Date, index=True)
    station: Mapped[str] = mapped_column(String(4))

    defects: Mapped[list[DefectRow]] = relationship(
        back_populates="card",
        cascade="all, delete-orphan",
        order_by="DefectRow.position",
    )

    def __repr__(self) -> str:
        return f"<RepairCardRow {self.number} {self.car_mark}>"


class DefectRow(Base):
    """One line of a card."""

    __tablename__ = "defect"
    __table_args__ = (
        UniqueConstraint("card_number", "position", name="uq_defect_one_a_position"),
        CheckConstraint("quantity >= 1", name="ck_defect_covers_a_part"),
        CheckConstraint("part_years >= 0", name="ck_defect_part_age"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    card_number: Mapped[int] = mapped_column(
        ForeignKey("repair_card.number", ondelete="CASCADE"), index=True
    )
    position: Mapped[int] = mapped_column()
    job: Mapped[str] = mapped_column(String(4))
    why_made: Mapped[str] = mapped_column(String(2))
    quantity: Mapped[int] = mapped_column(default=1)
    overridden: Mapped[str | None] = mapped_column(String(16), default=None)
    part_years: Mapped[int] = mapped_column(default=0)
    note: Mapped[str] = mapped_column(String(120), default="")

    card: Mapped[RepairCardRow] = relationship(back_populates="defects")

    def __repr__(self) -> str:
        return f"<DefectRow {self.card_number}/{self.position} {self.job}>"


class ObjectionRow(Base):
    """An exception filed against one line of a card."""

    __tablename__ = "objection"
    __table_args__ = (
        UniqueConstraint("card_number", "line", name="uq_objection_one_a_line"),
        CheckConstraint("disputed > 0", name="ck_objection_disputes_something"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    card_number: Mapped[int] = mapped_column(
        ForeignKey("repair_card.number", ondelete="CASCADE"), index=True
    )
    line: Mapped[int] = mapped_column()
    ground: Mapped[str] = mapped_column(String(20))
    disputed: Mapped[Money] = mapped_column(MoneyCents)
    filed_on: Mapped[date] = mapped_column(Date)
    note: Mapped[str] = mapped_column(String(200), default="")

    resolution: Mapped[ResolutionRow | None] = relationship(
        back_populates="objection", cascade="all, delete-orphan", uselist=False
    )

    def __repr__(self) -> str:
        return f"<ObjectionRow {self.card_number}/{self.line}>"


class ResolutionRow(Base):
    """How one exception was answered."""

    __tablename__ = "resolution"
    __table_args__ = (CheckConstraint("allowed >= 0", name="ck_resolution_not_negative"),)

    objection_id: Mapped[int] = mapped_column(
        ForeignKey("objection.id", ondelete="CASCADE"), primary_key=True
    )
    outcome: Mapped[str] = mapped_column(String(16))
    allowed: Mapped[Money] = mapped_column(MoneyCents)

    objection: Mapped[ObjectionRow] = relationship(back_populates="resolution")

    def __repr__(self) -> str:
        return f"<ResolutionRow {self.objection_id} {self.outcome}>"
