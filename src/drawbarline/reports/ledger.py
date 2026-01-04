"""The trial balance, which is the report that proves the others.

Every other report in this package answers a question. This one checks an
identity: the closing balances on the roads, added up, equal the position of
the whole pool. If they do not, something has been posted to a road that is
not on the account list, and every number on every other page is suspect.

It is the cheapest possible check and it is worth running before anything is
sent out. A month that does not balance is a month somebody looks at rather
than a month somebody posts.
"""

from __future__ import annotations

from dataclasses import dataclass

from drawbarline.marks import ReportingMark
from drawbarline.money import Money, total
from drawbarline.reports.render import Align, Column, Table, heading, money
from drawbarline.settlement.ledger import Ledger, PostingKind
from drawbarline.settlement.period import Period


@dataclass(frozen=True, slots=True)
class Line:
    """One road's month."""

    road: ReportingMark
    opening: Money
    movement: Money
    closing: Money
    postings: int

    @property
    def balances(self) -> bool:
        return self.opening + self.movement == self.closing

    def __str__(self) -> str:
        return f"{self.road} {self.opening} + {self.movement} = {self.closing}"


def trial_balance(ledger: Ledger, period: Period) -> list[Line]:
    """One line a road, opening through closing."""
    found = []
    for road in ledger.roads():
        account = ledger.account(road)
        opening = account.balance_through(period.preceding)
        movement = account.balance(period)
        found.append(
            Line(
                road=road,
                opening=opening,
                movement=movement,
                closing=opening + movement,
                postings=len(account.postings(period)),
            )
        )
    return found


def balances(lines: list[Line], ledger: Ledger, period: Period) -> bool:
    """Whether the road closings add up to the whole pool through the month."""
    if not all(one.balances for one in lines):
        return False
    return total(one.closing for one in lines) == ledger.position_through(period)


def trial_balance_report(ledger: Ledger, period: Period) -> list[str]:
    """The trial balance, with the identity checked at the bottom."""
    lines = trial_balance(ledger, period)
    table = Table(
        [
            Column("road"),
            Column("postings", Align.RIGHT),
            Column("opening", Align.RIGHT),
            Column("movement", Align.RIGHT),
            Column("closing", Align.RIGHT),
        ]
    )
    for one in lines:
        table.add(
            str(one.road),
            str(one.postings),
            money(one.opening),
            money(one.movement),
            money(one.closing),
        )
    table.total(
        f"{len(lines)} roads",
        str(sum(one.postings for one in lines)),
        money(total(one.opening for one in lines)),
        money(total(one.movement for one in lines)),
        money(total(one.closing for one in lines)),
    )
    tail = "balances" if balances(lines, ledger, period) else "DOES NOT BALANCE"
    return [*heading("trial balance", period.label), *table.lines(), "", tail]


def by_kind_report(ledger: Ledger, period: Period) -> list[str]:
    """One line a kind of charge, across every road."""
    table = Table([Column("kind"), Column("amount", Align.RIGHT)])
    running: dict[PostingKind, Money] = {}
    for road in ledger.roads():
        for kind, amount in ledger.account(road).by_kind(period).items():
            running[kind] = running.get(kind, Money(0)) + amount
    for kind in PostingKind:
        if kind in running:
            table.add(kind.text, money(running[kind]))
    table.total("all kinds", money(total(running.values())))
    return [*heading("postings by kind", period.label), *table.lines()]
