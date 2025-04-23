"""The settlement repositories.

Two of them, because they answer to different rules. The ledger writes
postings and never touches one again. The period register writes closings and
reopenings, and it is what the ledger asks before it accepts anything.

Both load their domain object whole rather than answering questions one at a
time. A month has a few hundred postings at most on a railroad this size, and
the close reads all of them anyway. Loading goes through the ledger's restore
path rather than its post path, because a year of history is mostly months
that are closed and there is nothing wrong with that.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from drawbarline.errors import SettlementError
from drawbarline.marks import ReportingMark
from drawbarline.persistence.models.settlement import (
    ClosedPeriodRow,
    PostingRow,
    ReopeningRow,
)
from drawbarline.settlement.ledger import Ledger, Posting, PostingKind
from drawbarline.settlement.period import Period, PeriodRegister


def posting_from_row(row: PostingRow) -> Posting:
    return Posting(
        road=row.road,
        period=Period(row.period_year, row.period_month),
        kind=PostingKind[row.kind],
        amount=row.amount,
        on=row.posted_on,
        reference=row.reference,
    )


def row_from_posting(posting: Posting) -> PostingRow:
    return PostingRow(
        road=posting.road,
        period_year=posting.period.year,
        period_month=posting.period.month,
        kind=posting.kind.name,
        amount=posting.amount,
        posted_on=posting.on,
        reference=posting.reference,
    )


class PeriodRepository:
    """Which months are shut, and every time one was opened again."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def _closed_row(self, period: Period) -> ClosedPeriodRow | None:
        return self.session.execute(
            select(ClosedPeriodRow).where(
                ClosedPeriodRow.period_year == period.year,
                ClosedPeriodRow.period_month == period.month,
            )
        ).scalar_one_or_none()

    def close(self, period: Period, on: date) -> Period:
        if not self.is_open(period):
            raise SettlementError("that month is already closed", period=str(period))
        self.session.add(
            ClosedPeriodRow(period_year=period.year, period_month=period.month, closed_on=on)
        )
        self.session.flush()
        return period

    def reopen(self, period: Period, on: date, reason: str) -> Period:
        if not reason.strip():
            raise SettlementError("reopening a month needs a reason", period=str(period))
        row = self._closed_row(period)
        if row is None:
            raise SettlementError("that month is not closed", period=str(period))
        self.session.delete(row)
        self.session.add(
            ReopeningRow(
                period_year=period.year,
                period_month=period.month,
                reopened_on=on,
                reason=reason,
            )
        )
        self.session.flush()
        return period

    def is_open(self, period: Period) -> bool:
        return self._closed_row(period) is None

    def closed_periods(self) -> list[Period]:
        rows = self.session.execute(
            select(ClosedPeriodRow).order_by(
                ClosedPeriodRow.period_year, ClosedPeriodRow.period_month
            )
        ).scalars()
        return [Period(row.period_year, row.period_month) for row in rows]

    def reopenings(self) -> list[tuple[Period, date, str]]:
        rows = self.session.execute(
            select(ReopeningRow).order_by(ReopeningRow.reopened_on)
        ).scalars()
        return [
            (Period(row.period_year, row.period_month), row.reopened_on, row.reason) for row in rows
        ]

    def register(self) -> PeriodRegister:
        register = PeriodRegister()
        for period in self.closed_periods():
            register.close(period)
        return register


class LedgerRepository:
    """Every posting against every road."""

    def __init__(self, session: Session) -> None:
        self.session = session

    @property
    def periods(self) -> PeriodRepository:
        return PeriodRepository(self.session)

    def post(self, posting: Posting) -> Posting:
        if not self.periods.is_open(posting.period):
            raise SettlementError("that month is closed to posting", period=str(posting.period))
        self.session.add(row_from_posting(posting))
        self.session.flush()
        return posting

    def postings(self, road: ReportingMark | None = None) -> list[Posting]:
        query = select(PostingRow).order_by(
            PostingRow.period_year, PostingRow.period_month, PostingRow.posted_on
        )
        if road is not None:
            query = query.where(PostingRow.road == road)
        return [posting_from_row(row) for row in self.session.execute(query).scalars()]

    def roads(self) -> list[ReportingMark]:
        rows = self.session.execute(
            select(PostingRow.road).distinct().order_by(PostingRow.road)
        ).scalars()
        return list(rows)

    def ledger(self) -> Ledger:
        """Load the whole account, which is what a statement is cut from."""
        found = Ledger(self.periods.register())
        for posting in self.postings():
            found.restore(posting)
        return found

    def __len__(self) -> int:
        return len(self.postings())
