"""Settlement routes.

The close is a post rather than a get because it changes a great deal and
because running it twice has to be refused. Everything else in here reads.

A month arrives in the path as ``2025-01``. That is what everybody writes on
paper and it is unambiguous, unlike a date, which invites somebody to close
the fourteenth and then ask why the answer is odd.
"""

from __future__ import annotations

from datetime import date, datetime

from fastapi import APIRouter, Depends, status
from pydantic import BaseModel, ConfigDict

from drawbarline.api.deps import read_only, unit_of_work
from drawbarline.errors import SettlementError
from drawbarline.marks import ReportingMark
from drawbarline.persistence.uow import UnitOfWork
from drawbarline.service import run_close
from drawbarline.settlement.period import Period
from drawbarline.settlement.statement import Statement, cut

router = APIRouter(tags=["settlement"])


def a_month(text: str) -> Period:
    try:
        found = datetime.strptime(text.strip(), "%Y-%m")
    except ValueError as bad:
        raise SettlementError("a month reads as 2025-01", given=text) from bad
    return Period(found.year, found.month)


class CloseIn(BaseModel):
    """What a close needs beyond the month itself."""

    model_config = ConfigDict(extra="forbid")

    issued: date


class ReopenIn(BaseModel):
    model_config = ConfigDict(extra="forbid")

    on: date
    reason: str


class CloseOut(BaseModel):
    month: str
    car_hire: int
    reclaimed: int
    demurrage: int
    postings: int
    statements: list[str]
    invoices: list[str]


class PeriodOut(BaseModel):
    month: str
    open: bool


class LineOut(BaseModel):
    kind: str
    amount: int


class StatementOut(BaseModel):
    number: int
    road: str
    month: str
    opening: int
    lines: list[LineOut]
    closing: int
    direction: str

    @classmethod
    def of(cls, found: Statement) -> StatementOut:
        return cls(
            number=found.number,
            road=str(found.road),
            month=found.period.label,
            opening=found.opening.cents,
            lines=[LineOut(kind=one.kind.text, amount=one.amount.cents) for one in found.lines],
            closing=found.closing.cents,
            direction=found.direction(),
        )


@router.get("/periods", response_model=list[PeriodOut])
def periods(work: UnitOfWork = Depends(read_only)) -> list[PeriodOut]:
    """Every month that has been closed."""
    return [PeriodOut(month=one.label, open=False) for one in work.periods.closed_periods()]


@router.post("/periods/{month}/close", status_code=status.HTTP_201_CREATED, response_model=CloseOut)
def close(month: str, filing: CloseIn, work: UnitOfWork = Depends(unit_of_work)) -> CloseOut:
    """Work a month through and shut it."""
    period = a_month(month)
    if filing.issued < period.last_day:
        raise SettlementError("a month is closed after it ends", month=period.label)
    result = run_close(work, period, issued=filing.issued)
    return CloseOut(
        month=result.period.label,
        car_hire=result.car_hire.cents,
        reclaimed=result.reclaimed.cents,
        demurrage=result.demurrage.cents,
        postings=result.posted,
        statements=[str(one) for one in result.statements],
        invoices=[str(one) for one in result.invoices],
    )


@router.post("/periods/{month}/reopen", response_model=PeriodOut)
def reopen(month: str, filing: ReopenIn, work: UnitOfWork = Depends(unit_of_work)) -> PeriodOut:
    """Open a closed month again, with the reason on file."""
    period = a_month(month)
    work.periods.reopen(period, filing.on, filing.reason)
    return PeriodOut(month=period.label, open=True)


@router.get("/statements/{road}/{month}", response_model=StatementOut)
def statement(
    road: str, month: str, number: int = 1, work: UnitOfWork = Depends(read_only)
) -> StatementOut:
    """The statement for one road and one month."""
    period = a_month(month)
    found = cut(
        work.ledger.ledger(),
        ReportingMark.parse(road),
        period,
        number=number,
        issued=period.last_day,
    )
    return StatementOut.of(found)


@router.get("/ledger/{road}", response_model=list[dict])
def account(road: str, work: UnitOfWork = Depends(read_only)) -> list[dict]:
    """Every posting against one road, oldest first."""
    postings = work.ledger.postings(ReportingMark.parse(road))
    return [
        {
            "month": one.period.label,
            "kind": one.kind.text,
            "amount": one.amount.cents,
            "on": str(one.on),
            "reference": one.reference,
        }
        for one in postings
    ]
