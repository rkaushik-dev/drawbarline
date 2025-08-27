"""Report routes.

Every report already knows how to lay itself out for a printer, so each of
these serves the same lines the command line prints, as an array of strings.
That is deliberately unfashionable. The office reads these in a terminal and
pastes them into mail, and a client that wants the numbers rather than the
page has the data routes beside them.

Where a report has a data form as well, both are here and they come off the
same computation, so the page and the numbers cannot disagree.
"""

from __future__ import annotations

from datetime import UTC, date, datetime

from fastapi import APIRouter, Depends

from drawbarline.api.deps import read_only
from drawbarline.api.routes.settlement import a_month
from drawbarline.carhire.liability import liability
from drawbarline.marks import ReportingMark
from drawbarline.persistence.uow import UnitOfWork
from drawbarline.reference import DRAWBAR_LINE
from drawbarline.reports.carhire import detail_report, liability_report, owed_to
from drawbarline.reports.demurrage import detention_report, shipper_report
from drawbarline.reports.fleet import age_profile, age_report, utilisation_report
from drawbarline.reports.interchange import balance_report, crossings, on_hand_report
from drawbarline.service import books_of
from drawbarline.settlement.close import detentions_in

router = APIRouter(prefix="/reports", tags=["reports"])


@router.get("/carhire/{month}", response_model=list[str])
def carhire(
    month: str, road: str | None = None, work: UnitOfWork = Depends(read_only)
) -> list[str]:
    """Car hire liability, by road or car by car."""
    period = a_month(month)
    books = books_of(work)
    owed = liability(books.fleet, books.log, books.rates, period.window, DRAWBAR_LINE.mark)
    if road:
        return detail_report(owed, ReportingMark.parse(road), period)
    return liability_report(owed, period)


@router.get("/carhire/{month}/totals", response_model=dict[str, int])
def carhire_totals(month: str, work: UnitOfWork = Depends(read_only)) -> dict[str, int]:
    """The same run as data, in cents a road."""
    period = a_month(month)
    books = books_of(work)
    owed = liability(books.fleet, books.log, books.rates, period.window, DRAWBAR_LINE.mark)
    return {road: amount.cents for road, amount in owed_to(owed).items()}


@router.get("/demurrage/{month}", response_model=list[str])
def demurrage(
    month: str, by_shipper: bool = False, work: UnitOfWork = Depends(read_only)
) -> list[str]:
    """Detention, car by car or customer by customer."""
    period = a_month(month)
    books = books_of(work)
    found = detentions_in(books, period)
    tariff = books.tariffs.in_effect_on(period.last_day)
    if by_shipper:
        return shipper_report(found, tariff, books.customers, period.label)
    return detention_report(found, tariff, period.label)


@router.get("/utilisation/{month}", response_model=list[str])
def utilisation(month: str, work: UnitOfWork = Depends(read_only)) -> list[str]:
    """Turn time against standing time, car by car."""
    period = a_month(month)
    return utilisation_report(work.events.log(), period.window, period.label)


@router.get("/interchange/{month}", response_model=list[str])
def interchange(month: str, work: UnitOfWork = Depends(read_only)) -> list[str]:
    """What crossed each junction, both ways."""
    period = a_month(month)
    return balance_report(work.events.log(), period.window, period.label)


@router.get("/interchange/{month}/crossings", response_model=list[dict])
def interchange_crossings(month: str, work: UnitOfWork = Depends(read_only)) -> list[dict]:
    """The same counts as data, one row a junction and road."""
    period = a_month(month)
    return [
        {
            "junction": one.junction,
            "road": str(one.road),
            "received": one.received,
            "delivered": one.delivered,
            "net": one.net,
        }
        for one in crossings(work.events.log(), period.window)
    ]


@router.get("/onhand", response_model=list[str])
def onhand(at: datetime | None = None, work: UnitOfWork = Depends(read_only)) -> list[str]:
    """Every car we have taken and not handed back."""
    moment = at.astimezone(UTC) if at else datetime.now(UTC)
    return on_hand_report(work.events.log(), moment)


@router.get("/age", response_model=list[str])
def age(on: date | None = None, work: UnitOfWork = Depends(read_only)) -> list[str]:
    """How old the fleet is and what leaves interchange service soon."""
    return age_report(work.fleet.register(), on or datetime.now(UTC).date())


@router.get("/age/profile", response_model=dict[str, int])
def age_data(on: date | None = None, work: UnitOfWork = Depends(read_only)) -> dict[str, int]:
    """The age profile as data, cars in each decade."""
    return age_profile(work.fleet.register(), on or datetime.now(UTC).date())
