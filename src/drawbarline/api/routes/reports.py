"""Report routes.

Every report already knows how to lay itself out for a printer, so each of
these serves the same lines the command line prints, as an array of strings.
That is deliberately unfashionable. The office reads these in a terminal and
pastes them into mail, and a client that wants the numbers rather than the
page has the data routes beside them.

Nothing is computed here. The reporting layer does that and the command line
calls the same functions, so the page and the terminal cannot drift apart.
"""

from __future__ import annotations

from datetime import UTC, date, datetime

from fastapi import APIRouter, Depends

from drawbarline import reporting
from drawbarline.api.deps import read_only
from drawbarline.api.routes.settlement import a_month
from drawbarline.persistence.uow import UnitOfWork

router = APIRouter(prefix="/reports", tags=["reports"])


@router.get("/carhire/{month}", response_model=list[str])
def carhire(
    month: str, road: str | None = None, work: UnitOfWork = Depends(read_only)
) -> list[str]:
    """Car hire liability, by road or car by car."""
    return reporting.car_hire(work, a_month(month), road)


@router.get("/carhire/{month}/totals", response_model=dict[str, int])
def carhire_totals(month: str, work: UnitOfWork = Depends(read_only)) -> dict[str, int]:
    """The same run as data, in cents a road."""
    found = reporting.car_hire_totals(work, a_month(month))
    return {road: amount.cents for road, amount in found.items()}


@router.get("/demurrage/{month}", response_model=list[str])
def demurrage(
    month: str, by_shipper: bool = False, work: UnitOfWork = Depends(read_only)
) -> list[str]:
    """Detention, car by car or customer by customer."""
    return reporting.demurrage(work, a_month(month), by_shipper=by_shipper)


@router.get("/utilisation/{month}", response_model=list[str])
def utilisation(month: str, work: UnitOfWork = Depends(read_only)) -> list[str]:
    """Turn time against standing time, car by car."""
    return reporting.utilisation(work, a_month(month))


@router.get("/interchange/{month}", response_model=list[str])
def interchange(month: str, work: UnitOfWork = Depends(read_only)) -> list[str]:
    """What crossed each junction, both ways."""
    return reporting.interchange(work, a_month(month))


@router.get("/interchange/{month}/crossings", response_model=list[dict])
def interchange_crossings(month: str, work: UnitOfWork = Depends(read_only)) -> list[dict]:
    """The same counts as data, one row a junction and road."""
    return [
        {
            "junction": one.junction,
            "road": str(one.road),
            "received": one.received,
            "delivered": one.delivered,
            "net": one.net,
        }
        for one in reporting.interchange_crossings(work, a_month(month))
    ]


@router.get("/onhand", response_model=list[str])
def onhand(at: datetime | None = None, work: UnitOfWork = Depends(read_only)) -> list[str]:
    """Every car we have taken and not handed back."""
    return reporting.on_hand(work, at.astimezone(UTC) if at else datetime.now(UTC))


@router.get("/age", response_model=list[str])
def age(on: date | None = None, work: UnitOfWork = Depends(read_only)) -> list[str]:
    """How old the fleet is and what leaves interchange service soon."""
    return reporting.fleet_age(work, on or datetime.now(UTC).date())


@router.get("/age/profile", response_model=dict[str, int])
def age_data(on: date | None = None, work: UnitOfWork = Depends(read_only)) -> dict[str, int]:
    """The age profile as data, cars in each decade."""
    return reporting.fleet_age_profile(work, on or datetime.now(UTC).date())


@router.get("/repairs/{month}", response_model=list[str])
def repairs(
    month: str, exceptions: bool = False, work: UnitOfWork = Depends(read_only)
) -> list[str]:
    """Repair billing for a month, or what is still under exception."""
    return reporting.repairs(work, a_month(month), exceptions=exceptions)


@router.get("/shop", response_model=list[str])
def shop(at: datetime | None = None, work: UnitOfWork = Depends(read_only)) -> list[str]:
    """Which cars are bad ordered and how long they have been down."""
    return reporting.shop(work, at.astimezone(UTC) if at else datetime.now(UTC))


@router.get("/forecast", response_model=list[str])
def forecast(
    at: datetime | None = None,
    days: int = 7,
    work: UnitOfWork = Depends(read_only),
) -> list[str]:
    """What the cars standing here cost a day if nothing moves."""
    moment = at.astimezone(UTC) if at else datetime.now(UTC)
    return reporting.forecast(work, moment, days)


@router.get("/balance/{month}", response_model=list[str])
def balance(month: str, by_kind: bool = False, work: UnitOfWork = Depends(read_only)) -> list[str]:
    """The trial balance, which proves the other reports."""
    return reporting.trial_balance(work, a_month(month), by_kind=by_kind)


@router.get("/annual/{year}", response_model=list[str])
def annual(year: int, road: str | None = None, work: UnitOfWork = Depends(read_only)) -> list[str]:
    """The year on one page."""
    return reporting.annual(work, year, road)
