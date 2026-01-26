"""The year on one page.

Twelve rows, one a month, and the columns are the kinds of charge. It is the
only report in the package that reads more than one month, and it is what goes
to the board once a year and to the auditors once a year after that.

Nothing is recomputed. Every figure is the ledger read back, which means the
year summary and the twelve statements that made it cannot disagree, and a
month that was reopened and closed again shows the figure it ended at rather
than the one it was first agreed at.
"""

from __future__ import annotations

from dataclasses import dataclass

from drawbarline.errors import SettlementError
from drawbarline.marks import ReportingMark
from drawbarline.money import Money, total
from drawbarline.reports.render import Align, Column, Table, heading, money
from drawbarline.settlement.ledger import Ledger, PostingKind
from drawbarline.settlement.period import MONTHS_IN_A_YEAR, Period

SHOWN = (
    PostingKind.CAR_HIRE,
    PostingKind.MILEAGE,
    PostingKind.RECLAIM,
    PostingKind.REPAIRS,
    PostingKind.ADJUSTMENT,
    PostingKind.PAYMENT,
)


@dataclass(frozen=True, slots=True)
class MonthLine:
    """One month of the year, by kind."""

    period: Period
    amounts: dict[PostingKind, Money]
    postings: int

    def of(self, kind: PostingKind) -> Money:
        return self.amounts.get(kind, Money(0))

    @property
    def movement(self) -> Money:
        return total(self.amounts.values())

    def __str__(self) -> str:
        return f"{self.period} {self.movement}"


def months_of(year: int) -> list[Period]:
    if not 1900 <= year <= 2200:
        raise SettlementError("that is not a year we settle", year=year)
    return [Period(year, month) for month in range(1, MONTHS_IN_A_YEAR + 1)]


def annual(ledger: Ledger, year: int, road: ReportingMark | None = None) -> list[MonthLine]:
    """One line a month, for the whole pool or for one road."""
    roads = [road] if road is not None else ledger.roads()
    found = []
    for period in months_of(year):
        amounts: dict[PostingKind, Money] = {}
        postings = 0
        for one in roads:
            account = ledger.account(one)
            postings += len(account.postings(period))
            for kind, amount in account.by_kind(period).items():
                amounts[kind] = amounts.get(kind, Money(0)) + amount
        found.append(MonthLine(period, amounts, postings))
    return found


def annual_report(ledger: Ledger, year: int, road: ReportingMark | None = None) -> list[str]:
    """The year on one page, a month a row."""
    lines = annual(ledger, year, road)
    table = Table(
        [
            Column("month"),
            Column("postings", Align.RIGHT),
            *[Column(kind.text, Align.RIGHT) for kind in SHOWN],
            Column("movement", Align.RIGHT),
        ]
    )
    for one in lines:
        table.add(
            one.period.label,
            str(one.postings),
            *[money(one.of(kind)) for kind in SHOWN],
            money(one.movement),
        )
    table.total(
        str(year),
        str(sum(one.postings for one in lines)),
        *[money(total(one.of(kind) for one in lines)) for kind in SHOWN],
        money(total(one.movement for one in lines)),
    )
    who = str(road) if road is not None else "all roads"
    return [*heading("annual summary", f"{year} {who}"), *table.lines()]


def busiest_month(lines: list[MonthLine]) -> MonthLine | None:
    """The month that moved the most money, which is where an auditor starts."""
    moved = [one for one in lines if not one.movement.is_zero]
    if not moved:
        return None
    return max(moved, key=lambda one: abs(one.movement).cents)
