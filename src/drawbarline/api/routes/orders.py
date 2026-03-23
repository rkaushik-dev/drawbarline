"""Car order and distribution routes.

An order is taken and cancelled and never edited, so there is a post and a
post to cancel and nothing else. A shipper who wants three cars instead of
four cancels and orders again, which is what they do on the telephone, and it
leaves a record of both.

The distribution is a get. It computes which empty goes against which order
and stores none of it, so two people asking during the same morning see the
same answer and neither of them has taken the cars.
"""

from __future__ import annotations

from datetime import date, datetime

from fastapi import APIRouter, Depends, status
from pydantic import BaseModel, ConfigDict

from drawbarline.api.deps import moment_or_now, read_only, unit_of_work
from drawbarline.distribution import CarOrder, distribute, empties_on_hand
from drawbarline.fleet.aar import CarKind
from drawbarline.persistence.uow import UnitOfWork

router = APIRouter(tags=["orders"])


class OrderIn(BaseModel):
    """A car order as it is taken."""

    model_config = ConfigDict(extra="forbid")

    number: int
    shipper: str
    industry: str
    kind: str
    cars: int
    wanted_on: date
    placed_on: date


class CancelIn(BaseModel):
    model_config = ConfigDict(extra="forbid")

    on: date


class OrderOut(BaseModel):
    number: int
    shipper: str
    industry: str
    kind: str
    cars: int
    wanted_on: date
    cancelled_on: date | None = None

    @classmethod
    def of(cls, order: CarOrder, cancelled: date | None = None) -> OrderOut:
        return cls(
            number=order.number,
            shipper=order.shipper,
            industry=order.industry,
            kind=order.kind.description,
            cars=order.cars,
            wanted_on=order.wanted_on,
            cancelled_on=cancelled,
        )


class EmptyOut(BaseModel):
    car: str
    kind: str
    station: str


class AssignmentOut(BaseModel):
    order: int
    car: str
    from_station: str
    to_station: str
    tenths: int


class DistributionOut(BaseModel):
    wanted_on: date
    assignments: list[AssignmentOut]
    short: dict[int, int]
    filled: int
    unfilled: int
    empty_tenths: int


@router.post("/orders", status_code=status.HTTP_201_CREATED, response_model=OrderOut)
def place(filing: OrderIn, work: UnitOfWork = Depends(unit_of_work)) -> OrderOut:
    """Take a car order."""
    order = CarOrder(
        number=filing.number,
        shipper=filing.shipper.strip().upper(),
        industry=filing.industry.strip().upper(),
        kind=CarKind.from_letter(filing.kind.strip().upper()),
        cars=filing.cars,
        wanted_on=filing.wanted_on,
    )
    work.orders.place(order, filing.placed_on)
    return OrderOut.of(order)


@router.get("/orders", response_model=list[OrderOut])
def open_orders(
    on: date, shipper: str | None = None, work: UnitOfWork = Depends(read_only)
) -> list[OrderOut]:
    """Orders still wanting cars on a day, oldest first."""
    found = work.orders.for_shipper(shipper.strip().upper()) if shipper else work.orders.open_on(on)
    return [OrderOut.of(one, work.orders.cancelled_on(one.number)) for one in found]


@router.post("/orders/{number}/cancel", response_model=OrderOut)
def cancel(number: int, filing: CancelIn, work: UnitOfWork = Depends(unit_of_work)) -> OrderOut:
    """Cancel a car order."""
    order = work.orders.cancel(number, filing.on)
    return OrderOut.of(order, filing.on)


@router.get("/empties", response_model=list[EmptyOut])
def empties(at: datetime | None = None, work: UnitOfWork = Depends(read_only)) -> list[EmptyOut]:
    """Every car standing empty on our rail."""
    found = empties_on_hand(work.fleet.register(), work.events.log(), moment_or_now(at))
    return [
        EmptyOut(car=str(one.car), kind=one.kind.description, station=one.station) for one in found
    ]


@router.get("/distribution", response_model=DistributionOut)
def distribution(
    on: date, at: datetime | None = None, work: UnitOfWork = Depends(read_only)
) -> DistributionOut:
    """Match the empties on hand against the open orders."""
    moment = moment_or_now(at)
    orders = work.orders.open_on(on)
    empty = empties_on_hand(work.fleet.register(), work.events.log(), moment)
    found = distribute(orders, empty, work.orders.stations())
    return DistributionOut(
        wanted_on=on,
        assignments=[
            AssignmentOut(
                order=one.order,
                car=str(one.car),
                from_station=one.from_station,
                to_station=one.to_station,
                tenths=one.tenths,
            )
            for one in found.assignments
        ],
        short=found.short,
        filled=found.filled,
        unfilled=found.unfilled,
        empty_tenths=found.empty_tenths(),
    )
