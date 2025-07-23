"""The waybill tables.

A waybill and the route it is authorised over. The route is its own table with
a position column rather than a delimited string, because unlike the switching
days on an industry a route is walked, joined against and reported on, and a
sixteen station route in a text column is a report nobody can write.

A waybill points at a car and the foreign key says so. It does not point at a
shipper or a consignee, because half the parties on a waybill are on somebody
else's railroad and we have no record of them beyond the name on the paper.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import CheckConstraint, Date, ForeignKey, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from drawbarline.marks import CarMark
from drawbarline.persistence.base import Base
from drawbarline.persistence.types import CarMarkText, WeightPounds
from drawbarline.weight import Weight


class WaybillRow(Base):
    """One movement of one car, as the billing copy carries it."""

    __tablename__ = "waybill"
    __table_args__ = (
        CheckConstraint("number >= 1", name="ck_waybill_numbered_from_one"),
        CheckConstraint(
            "lading_stcc is null or lading_pounds > 0", name="ck_waybill_lading_weighs"
        ),
    )

    number: Mapped[int] = mapped_column(primary_key=True)
    dated: Mapped[date] = mapped_column(Date, index=True)
    car_mark: Mapped[CarMark] = mapped_column(
        CarMarkText, ForeignKey("car.mark", ondelete="RESTRICT"), index=True
    )
    shipper: Mapped[str] = mapped_column(String(60))
    consignee: Mapped[str] = mapped_column(String(60))
    lading_stcc: Mapped[str | None] = mapped_column(String(7), default=None)
    lading_name: Mapped[str | None] = mapped_column(String(60), default=None)
    lading_pounds: Mapped[Weight | None] = mapped_column(WeightPounds, default=None)
    lading_bulk: Mapped[bool] = mapped_column(default=False)

    stops: Mapped[list[RouteStopRow]] = relationship(
        back_populates="waybill",
        cascade="all, delete-orphan",
        order_by="RouteStopRow.position",
    )

    def __repr__(self) -> str:
        return f"<WaybillRow {self.number} {self.car_mark}>"


class RouteStopRow(Base):
    """One station on a waybill route, in the order the car passes it."""

    __tablename__ = "route_stop"
    __table_args__ = (
        UniqueConstraint("waybill_number", "position", name="uq_stop_one_a_position"),
        CheckConstraint("position >= 0", name="ck_stop_position_not_negative"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    waybill_number: Mapped[int] = mapped_column(
        ForeignKey("waybill.number", ondelete="CASCADE"), index=True
    )
    position: Mapped[int] = mapped_column()
    station: Mapped[str] = mapped_column(String(4))

    waybill: Mapped[WaybillRow] = relationship(back_populates="stops")

    def __repr__(self) -> str:
        return f"<RouteStopRow {self.waybill_number} {self.position} {self.station}>"
