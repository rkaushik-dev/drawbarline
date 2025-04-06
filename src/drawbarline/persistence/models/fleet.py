"""The equipment tables.

A car row is the stencil and the filing, one row a car, keyed on the marks
because that is what every other table and every piece of paper refers to it
by. Leases and retirements hang off it.

The lease table has no exclusion constraint on overlapping terms, because
sqlite cannot express one. The rule that terms may not overlap lives in the
domain, is tested there, and is enforced on the way in. What the table does
carry is the foreign key, so a lease cannot outlive the car it is against.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import CheckConstraint, Date, ForeignKey, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from drawbarline.fleet.aar import CarTypeCode, MechanicalDesignation
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.measure import Length
from drawbarline.persistence.base import Base
from drawbarline.persistence.types import (
    CarMarkText,
    DesignationText,
    LengthInches,
    MarkText,
    TypeCodeText,
    WeightPounds,
)
from drawbarline.weight import Weight


class CarRow(Base):
    """One equipment record."""

    __tablename__ = "car"
    __table_args__ = (
        CheckConstraint("light_weight < gross_rail_load", name="ck_car_light_under_gross"),
        CheckConstraint("outside_length > 0", name="ck_car_has_length"),
    )

    mark: Mapped[CarMark] = mapped_column(CarMarkText, primary_key=True)
    owner: Mapped[ReportingMark] = mapped_column(MarkText, index=True)
    designation: Mapped[MechanicalDesignation] = mapped_column(DesignationText)
    type_code: Mapped[CarTypeCode] = mapped_column(TypeCodeText, index=True)
    built: Mapped[date] = mapped_column(Date)
    rebuilt: Mapped[date | None] = mapped_column(Date, default=None)
    light_weight: Mapped[Weight] = mapped_column(WeightPounds)
    gross_rail_load: Mapped[Weight] = mapped_column(WeightPounds)
    outside_length: Mapped[Length] = mapped_column(LengthInches)

    leases: Mapped[list[LeaseRow]] = relationship(
        back_populates="car", cascade="all, delete-orphan", order_by="LeaseRow.began"
    )
    retirement: Mapped[RetirementRow | None] = relationship(
        back_populates="car", cascade="all, delete-orphan", uselist=False
    )

    def __repr__(self) -> str:
        return f"<CarRow {self.mark}>"


class LeaseRow(Base):
    """A term somebody other than the owner collected for."""

    __tablename__ = "lease"
    __table_args__ = (
        CheckConstraint("ended is null or ended >= began", name="ck_lease_ends_after"),
        UniqueConstraint("car_mark", "began", name="uq_lease_one_start_a_day"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    car_mark: Mapped[CarMark] = mapped_column(
        CarMarkText, ForeignKey("car.mark", ondelete="CASCADE"), index=True
    )
    lessee: Mapped[ReportingMark] = mapped_column(MarkText, index=True)
    began: Mapped[date] = mapped_column(Date)
    ended: Mapped[date | None] = mapped_column(Date, default=None)

    car: Mapped[CarRow] = relationship(back_populates="leases")

    def __repr__(self) -> str:
        return f"<LeaseRow {self.car_mark} to {self.lessee}>"


class RetirementRow(Base):
    """The day a car came off the roster, kept so settlement can still find it."""

    __tablename__ = "retirement"

    car_mark: Mapped[CarMark] = mapped_column(
        CarMarkText, ForeignKey("car.mark", ondelete="CASCADE"), primary_key=True
    )
    on: Mapped[date] = mapped_column(Date)
    reason: Mapped[str] = mapped_column(String(120), default="")

    car: Mapped[CarRow] = relationship(back_populates="retirement")

    def __repr__(self) -> str:
        return f"<RetirementRow {self.car_mark} {self.on}>"
