"""The shipper and industry tables.

An industry belongs to a station and to a shipper, and only one of those is a
foreign key. The station is a code out of the reference data, which lives in
the source rather than the database because it changes when track is built and
not when somebody signs a contract, so it is a plain column with a matching
length.

The days we switch a track are stored as a comma separated list of three
letter days. A join table of one row a weekday an industry would be more
correct, and there are eleven industries on this railroad.
"""

from __future__ import annotations

from sqlalchemy import CheckConstraint, ForeignKey, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from drawbarline.persistence.base import Base


class ShipperRow(Base):
    """A party we bill demurrage to."""

    __tablename__ = "shipper"

    code: Mapped[str] = mapped_column(String(8), primary_key=True)
    name: Mapped[str] = mapped_column(String(60))
    average_agreement: Mapped[bool] = mapped_column(default=False)

    industries: Mapped[list[IndustryRow]] = relationship(
        back_populates="shipper",
        cascade="all, delete-orphan",
        order_by="IndustryRow.code",
    )

    def __repr__(self) -> str:
        return f"<ShipperRow {self.code}>"


class IndustryRow(Base):
    """A private track we place cars on."""

    __tablename__ = "industry"
    __table_args__ = (
        CheckConstraint("spots >= 1", name="ck_industry_holds_a_car"),
        CheckConstraint("length(switched) > 0", name="ck_industry_is_switched"),
    )

    code: Mapped[str] = mapped_column(String(12), primary_key=True)
    name: Mapped[str] = mapped_column(String(60))
    station: Mapped[str] = mapped_column(String(4), index=True)
    shipper_code: Mapped[str] = mapped_column(
        String(8), ForeignKey("shipper.code", ondelete="CASCADE"), index=True
    )
    spots: Mapped[int] = mapped_column()
    switched: Mapped[str] = mapped_column(String(32))

    shipper: Mapped[ShipperRow] = relationship(back_populates="industries")

    def __repr__(self) -> str:
        return f"<IndustryRow {self.code} at {self.station}>"
