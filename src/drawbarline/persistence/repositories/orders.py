"""The car order repository.

Open orders are the ones the distribution runs against, and an order is open
until it is cancelled or until the day it was wanted for has gone by. The
second half of that matters: an order for last Tuesday is not something to
fill this morning, and leaving it open means the oldest first rule keeps
handing cars to an order nobody is waiting on.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from drawbarline.distribution import CarOrder, DistributionError
from drawbarline.fleet.aar import CarKind
from drawbarline.persistence.models.customers import IndustryRow, ShipperRow
from drawbarline.persistence.models.orders import CarOrderRow


def order_from_row(row: CarOrderRow) -> CarOrder:
    return CarOrder(
        number=row.number,
        shipper=row.shipper_code,
        industry=row.industry_code,
        kind=CarKind.from_letter(row.kind),
        cars=row.cars,
        wanted_on=row.wanted_on,
    )


class OrderRepository:
    """Every car order, open or not."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def place(self, order: CarOrder, on: date) -> CarOrder:
        if self.session.get(CarOrderRow, order.number) is not None:
            raise DistributionError("that order is already placed", number=order.number)
        if self.session.get(ShipperRow, order.shipper) is None:
            raise DistributionError("no such shipper", shipper=order.shipper)
        if self.session.get(IndustryRow, order.industry) is None:
            raise DistributionError("no such industry", industry=order.industry)
        if on > order.wanted_on:
            raise DistributionError(
                "an order is placed before the cars are wanted",
                number=order.number,
                placed=str(on),
                wanted=str(order.wanted_on),
            )
        self.session.add(
            CarOrderRow(
                number=order.number,
                shipper_code=order.shipper,
                industry_code=order.industry,
                kind=order.kind.letter,
                cars=order.cars,
                wanted_on=order.wanted_on,
                placed_on=on,
            )
        )
        self.session.flush()
        return order

    def get(self, number: int) -> CarOrder:
        row = self.session.get(CarOrderRow, number)
        if row is None:
            raise DistributionError("no such order", number=number)
        return order_from_row(row)

    def cancel(self, number: int, on: date) -> CarOrder:
        row = self.session.get(CarOrderRow, number)
        if row is None:
            raise DistributionError("no such order", number=number)
        if row.cancelled_on is not None:
            raise DistributionError("that order is already cancelled", number=number)
        if on < row.placed_on:
            raise DistributionError(
                "an order cannot be cancelled before it was placed", number=number
            )
        row.cancelled_on = on
        self.session.flush()
        return order_from_row(row)

    def cancelled_on(self, number: int) -> date | None:
        row = self.session.get(CarOrderRow, number)
        if row is None:
            raise DistributionError("no such order", number=number)
        return row.cancelled_on

    def open_on(self, day: date) -> list[CarOrder]:
        """Orders still wanting cars on a day, oldest first."""
        rows = self.session.execute(
            select(CarOrderRow)
            .where(
                CarOrderRow.cancelled_on.is_(None),
                CarOrderRow.wanted_on >= day,
                CarOrderRow.placed_on <= day,
            )
            .order_by(CarOrderRow.wanted_on, CarOrderRow.number)
        ).scalars()
        return [order_from_row(row) for row in rows]

    def for_shipper(self, shipper: str) -> list[CarOrder]:
        rows = self.session.execute(
            select(CarOrderRow)
            .where(CarOrderRow.shipper_code == shipper)
            .order_by(CarOrderRow.number)
        ).scalars()
        return [order_from_row(row) for row in rows]

    def stations(self) -> dict[str, str]:
        """Which station each industry track is at, which distribution needs."""
        rows = self.session.execute(select(IndustryRow.code, IndustryRow.station))
        return {row.code: row.station for row in rows}

    def __len__(self) -> int:
        return len(list(self.session.execute(select(CarOrderRow.number)).scalars()))
