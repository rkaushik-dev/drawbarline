"""The car hire rate table, stored.

One row a band. The natural key is the car type, the age range and the date it
took effect, and that is what the unique constraint holds, because filing the
same band twice is the way a fleet quietly starts being billed at double.

Rates are integers in their own units, tenths of a cent an hour and mills a
mile, so there is no floating point anywhere between the filing and the
statement.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import CheckConstraint, Date, Index, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from drawbarline.fleet.aar import CarTypeCode
from drawbarline.persistence.base import Base
from drawbarline.persistence.types import TypeCodeText


class RateBandRow(Base):
    """One line of the car hire schedule."""

    __tablename__ = "car_hire_band"
    __table_args__ = (
        UniqueConstraint("type_code", "from_age", "effective", name="uq_band_not_filed_twice"),
        CheckConstraint("from_age >= 0", name="ck_band_age_from_zero"),
        CheckConstraint("to_age >= from_age", name="ck_band_age_climbs"),
        CheckConstraint("hourly_tenths >= 0", name="ck_band_hourly_not_negative"),
        CheckConstraint("mileage_mills >= 0", name="ck_band_mileage_not_negative"),
        Index("ix_band_lookup", "type_code", "effective"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    type_code: Mapped[CarTypeCode] = mapped_column(TypeCodeText)
    from_age: Mapped[int] = mapped_column()
    to_age: Mapped[int] = mapped_column()
    effective: Mapped[date] = mapped_column(Date)
    hourly_tenths: Mapped[int] = mapped_column()
    mileage_mills: Mapped[int] = mapped_column()

    def __repr__(self) -> str:
        return f"<RateBandRow {self.type_code} {self.from_age}-{self.to_age} {self.effective}>"
