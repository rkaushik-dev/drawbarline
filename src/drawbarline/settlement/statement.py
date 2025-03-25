"""The monthly statement.

One page a road a month. What was outstanding when the month began, what
happened during it grouped by kind, and what is outstanding now. The other
road cuts the same statement from their own books and the two are compared,
which only works if both sides agree on what the opening balance was. So the
opening balance is computed from everything posted through the previous month
and never carried in a field somebody can edit.

The statement says which way the money runs in words as well as in a sign,
because the sign is a convention this office keeps and the road reading it
keeps the opposite one.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from drawbarline.errors import SettlementError
from drawbarline.marks import ReportingMark
from drawbarline.money import Money, total
from drawbarline.settlement.ledger import Ledger, PostingKind
from drawbarline.settlement.period import Period


@dataclass(frozen=True, slots=True)
class StatementLine:
    """One kind of charge, summed for the month."""

    kind: PostingKind
    amount: Money

    def __str__(self) -> str:
        return f"{self.kind.text} {self.amount}"


@dataclass(frozen=True, slots=True)
class Statement:
    """What one road owes us, or we owe them, for one month."""

    number: int
    road: ReportingMark
    period: Period
    issued: date
    opening: Money
    lines: tuple[StatementLine, ...]

    def __post_init__(self) -> None:
        if self.number < 1:
            raise SettlementError("a statement is numbered from one", number=self.number)
        if self.issued < self.period.last_day:
            raise SettlementError(
                "a statement is cut after the month it covers",
                period=str(self.period),
                issued=str(self.issued),
            )

    @property
    def movement(self) -> Money:
        return total(line.amount for line in self.lines)

    @property
    def closing(self) -> Money:
        return self.opening + self.movement

    @property
    def we_pay(self) -> bool:
        return self.closing.cents > 0

    @property
    def square(self) -> bool:
        return self.closing.is_zero

    def amount_for(self, kind: PostingKind) -> Money:
        for line in self.lines:
            if line.kind is kind:
                return line.amount
        return Money(0)

    def direction(self) -> str:
        if self.square:
            return "square"
        if self.we_pay:
            return f"we pay {self.road}"
        return f"{self.road} pays us"

    def __str__(self) -> str:
        return (
            f"statement {self.number} {self.road} {self.period} "
            f"{abs(self.closing)} {self.direction()}"
        )


def cut(
    ledger: Ledger,
    road: ReportingMark,
    period: Period,
    *,
    number: int,
    issued: date,
) -> Statement:
    """Cut the statement for one road and one month out of the ledger."""
    account = ledger.account(road)
    opening = account.balance_through(period.preceding)
    by_kind = account.by_kind(period)
    lines = tuple(StatementLine(kind, by_kind[kind]) for kind in PostingKind if kind in by_kind)
    return Statement(
        number=number,
        road=road,
        period=period,
        issued=issued,
        opening=opening,
        lines=lines,
    )


def cut_all(ledger: Ledger, period: Period, *, first_number: int, issued: date) -> list[Statement]:
    """A statement for every road with an account, numbered in road order."""
    found = []
    for offset, road in enumerate(ledger.roads()):
        found.append(cut(ledger, road, period, number=first_number + offset, issued=issued))
    return found
