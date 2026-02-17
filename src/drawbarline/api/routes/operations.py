"""Operating routes: waybills, embargoes and the switch list.

These are the ones the yard uses rather than the ones the general office uses,
and they read differently for it. A switch list is a get that computes
something and stores nothing, so a crew asking for one twice in five minutes
gets the same answer both times.

An embargo is issued and lifted and never edited, which is why there is a post
to lift one rather than a patch on the embargo itself.
"""

from __future__ import annotations

from datetime import date, datetime

from fastapi import APIRouter, Depends, Query, status
from pydantic import BaseModel, ConfigDict

from drawbarline.api.deps import moment_or_now, read_only, unit_of_work
from drawbarline.embargo import Embargo
from drawbarline.lading import Commodity, Lading, Stcc
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.persistence.uow import UnitOfWork
from drawbarline.switching import SwitchError, SwitchList, plan
from drawbarline.waybill import Waybill
from drawbarline.weight import Weight

router = APIRouter(tags=["operating"])


class LadingIn(BaseModel):
    model_config = ConfigDict(extra="forbid")

    stcc: str
    name: str
    pounds: int
    bulk: bool = False


class WaybillIn(BaseModel):
    """A waybill as it is issued."""

    model_config = ConfigDict(extra="forbid")

    number: int
    dated: date
    car: str
    shipper: str
    consignee: str
    route: list[str]
    lading: LadingIn | None = None


class WaybillOut(BaseModel):
    number: int
    dated: date
    car: str
    shipper: str
    consignee: str
    route: list[str]
    origin: str
    destination: str
    loaded: bool
    hazardous: bool
    tenths: int
    commodity: str | None

    @classmethod
    def of(cls, bill: Waybill) -> WaybillOut:
        return cls(
            number=bill.number,
            dated=bill.dated,
            car=str(bill.car),
            shipper=bill.shipper,
            consignee=bill.consignee,
            route=list(bill.route),
            origin=bill.origin,
            destination=bill.destination,
            loaded=bill.loaded,
            hazardous=bill.hazardous,
            tenths=bill.tenths,
            commodity=bill.lading.commodity.name if bill.lading else None,
        )


class EmbargoIn(BaseModel):
    """An embargo as it is issued."""

    model_config = ConfigDict(extra="forbid")

    number: int
    effective: date
    reason: str
    station: str | None = None
    commodity_family: str | None = None
    road: str | None = None
    expires: date | None = None
    permits_allowed: bool = False


class EmbargoOut(BaseModel):
    number: int
    effective: date
    expires: date | None
    reason: str
    station: str | None
    commodity_family: str | None
    road: str | None
    permits_allowed: bool

    @classmethod
    def of(cls, one: Embargo) -> EmbargoOut:
        return cls(
            number=one.number,
            effective=one.effective,
            expires=one.expires,
            reason=one.reason,
            station=one.station,
            commodity_family=one.commodity_family,
            road=str(one.road) if one.road else None,
            permits_allowed=one.permits_allowed,
        )


class LiftIn(BaseModel):
    model_config = ConfigDict(extra="forbid")

    on: date


class MoveOut(BaseModel):
    car: str
    kind: str
    industry: str
    note: str


class SwitchListOut(BaseModel):
    station: str
    day: date
    moves: list[MoveOut]
    worked: int

    @classmethod
    def of(cls, found: SwitchList) -> SwitchListOut:
        return cls(
            station=found.station,
            day=found.day,
            moves=[
                MoveOut(
                    car=str(one.car),
                    kind=one.kind.text,
                    industry=one.industry,
                    note=one.note,
                )
                for one in found.moves
            ],
            worked=found.worked,
        )


def waybill_of(filing: WaybillIn) -> Waybill:
    lading = None
    if filing.lading is not None:
        lading = Lading(
            Commodity(Stcc.parse(filing.lading.stcc), filing.lading.name, filing.lading.bulk),
            Weight(filing.lading.pounds),
        )
    return Waybill(
        number=filing.number,
        dated=filing.dated,
        car=CarMark.parse(filing.car),
        shipper=filing.shipper,
        consignee=filing.consignee,
        route=tuple(one.strip().upper() for one in filing.route),
        lading=lading,
    )


@router.post("/waybills", status_code=status.HTTP_201_CREATED, response_model=WaybillOut)
def issue_waybill(filing: WaybillIn, work: UnitOfWork = Depends(unit_of_work)) -> WaybillOut:
    """Issue a waybill."""
    return WaybillOut.of(work.waybills.issue(waybill_of(filing)))


@router.get("/waybills/{number}", response_model=WaybillOut)
def show_waybill(number: int, work: UnitOfWork = Depends(read_only)) -> WaybillOut:
    """One waybill."""
    return WaybillOut.of(work.waybills.get(number))


@router.get("/waybills", response_model=list[WaybillOut])
def list_waybills(
    car: str | None = None,
    first: date | None = None,
    last: date | None = None,
    work: UnitOfWork = Depends(read_only),
) -> list[WaybillOut]:
    """Waybills for one car, over a span of days, or every loaded one."""
    if car:
        found = work.waybills.for_car(CarMark.parse(car))
    elif first and last:
        found = work.waybills.dated_between(first, last)
    else:
        found = work.waybills.loaded_only()
    return [WaybillOut.of(one) for one in found]


@router.post("/embargoes", status_code=status.HTTP_201_CREATED, response_model=EmbargoOut)
def issue_embargo(filing: EmbargoIn, work: UnitOfWork = Depends(unit_of_work)) -> EmbargoOut:
    """Issue an embargo."""
    embargo = Embargo(
        number=filing.number,
        effective=filing.effective,
        reason=filing.reason,
        station=filing.station.strip().upper() if filing.station else None,
        commodity_family=filing.commodity_family,
        road=ReportingMark.parse(filing.road) if filing.road else None,
        expires=filing.expires,
        permits_allowed=filing.permits_allowed,
    )
    return EmbargoOut.of(work.embargoes.issue(embargo))


@router.get("/embargoes", response_model=list[EmbargoOut])
def list_embargoes(
    on: date | None = None, work: UnitOfWork = Depends(read_only)
) -> list[EmbargoOut]:
    """Every embargo, or only the ones live on a day."""
    found = work.embargoes.in_force_on(on) if on else work.embargoes.all()
    return [EmbargoOut.of(one) for one in found]


@router.post("/embargoes/{number}/lift", response_model=EmbargoOut)
def lift_embargo(
    number: int, filing: LiftIn, work: UnitOfWork = Depends(unit_of_work)
) -> EmbargoOut:
    """Lift an embargo early."""
    return EmbargoOut.of(work.embargoes.lift(number, filing.on))


@router.get("/switchlist/{station}", response_model=SwitchListOut)
def switch_list(
    station: str,
    day: date,
    at: datetime | None = None,
    order: list[str] = Query(default=[]),
    work: UnitOfWork = Depends(read_only),
) -> SwitchListOut:
    """The list for one station on one day, pulls first.

    Each order is written car=industry, because a query parameter that is two
    things needs a separator and an equals sign is the one nobody looks up.
    """
    orders = []
    for one in order:
        car, _, industry = one.partition("=")
        if not industry:
            raise SwitchError("an order is written as car=industry", given=one)
        orders.append((CarMark.parse(car), industry.strip().upper()))
    moment = moment_or_now(at)
    found = plan(
        work.events.log(),
        work.customers.register(),
        station.strip().upper(),
        day,
        moment,
        orders,
    )
    return SwitchListOut.of(found)
