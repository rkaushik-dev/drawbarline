"""The waybill repository.

A waybill is written once and diverted by writing another one, so there is no
update path in here at all. The diversion carries its own number and the
original stays exactly as it was issued, which is what a claim needs three
years later.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from drawbarline.errors import UnknownCar
from drawbarline.lading import Commodity, Lading, Stcc
from drawbarline.marks import CarMark
from drawbarline.persistence.models.fleet import CarRow
from drawbarline.persistence.models.waybill import RouteStopRow, WaybillRow
from drawbarline.waybill import Waybill, WaybillError


def waybill_from_row(row: WaybillRow) -> Waybill:
    lading = None
    if row.lading_stcc is not None and row.lading_pounds is not None:
        lading = Lading(
            Commodity(Stcc(row.lading_stcc), row.lading_name or "", row.lading_bulk),
            row.lading_pounds,
        )
    return Waybill(
        number=row.number,
        dated=row.dated,
        car=row.car_mark,
        shipper=row.shipper,
        consignee=row.consignee,
        route=tuple(stop.station for stop in row.stops),
        lading=lading,
    )


def row_from_waybill(bill: Waybill) -> WaybillRow:
    row = WaybillRow(
        number=bill.number,
        dated=bill.dated,
        car_mark=bill.car,
        shipper=bill.shipper,
        consignee=bill.consignee,
        lading_stcc=bill.lading.commodity.code.digits if bill.lading else None,
        lading_name=bill.lading.commodity.name if bill.lading else None,
        lading_pounds=bill.lading.weight if bill.lading else None,
        lading_bulk=bill.lading.commodity.bulk if bill.lading else False,
    )
    row.stops = [
        RouteStopRow(position=at, station=station) for at, station in enumerate(bill.route)
    ]
    return row


class WaybillRepository:
    """Every waybill ever issued."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def issue(self, bill: Waybill) -> Waybill:
        if self.session.get(WaybillRow, bill.number) is not None:
            raise WaybillError("that waybill is already issued", number=bill.number)
        if self.session.get(CarRow, bill.car) is None:
            raise UnknownCar("no record of that car", car=str(bill.car))
        self.session.add(row_from_waybill(bill))
        self.session.flush()
        return bill

    def get(self, number: int) -> Waybill:
        row = self.session.get(WaybillRow, number)
        if row is None:
            raise WaybillError("no such waybill", number=number)
        return waybill_from_row(row)

    def for_car(self, car: CarMark) -> list[Waybill]:
        rows = self.session.execute(
            select(WaybillRow)
            .where(WaybillRow.car_mark == car)
            .order_by(WaybillRow.dated, WaybillRow.number)
        ).scalars()
        return [waybill_from_row(row) for row in rows]

    def dated_between(self, first: date, last: date) -> list[Waybill]:
        rows = self.session.execute(
            select(WaybillRow)
            .where(WaybillRow.dated >= first, WaybillRow.dated <= last)
            .order_by(WaybillRow.dated, WaybillRow.number)
        ).scalars()
        return [waybill_from_row(row) for row in rows]

    def loaded_only(self) -> list[Waybill]:
        rows = self.session.execute(
            select(WaybillRow)
            .where(WaybillRow.lading_stcc.is_not(None))
            .order_by(WaybillRow.number)
        ).scalars()
        return [waybill_from_row(row) for row in rows]

    def __len__(self) -> int:
        return len(list(self.session.execute(select(WaybillRow.number)).scalars()))
