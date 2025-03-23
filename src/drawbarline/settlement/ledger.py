"""The running account with each connection.

Everything that passes between us and another road for a month ends up as a
posting: car hire we owe them, car hire they owe us, repairs billed either
way, reclaims, adjustments, and payments. The sign convention is stated once
and never bent. A positive amount is money we owe them. A negative amount is
money they owe us. There is no second flag saying which way it runs, because
two representations of a direction is how a statement ends up backwards.

Netting is then just addition. We do not send a road a bill for car hire and
receive one for repairs in the same month, we net the whole account and one of
us writes a cheque. That is the point of the pool and it is why the account is
by road rather than by kind of charge.

A posting into a closed month is refused. The month is the unit that gets
agreed with the other road, and a late posting into an agreed month is a
different document called an adjustment.
"""

from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass
from datetime import date
from enum import Enum

from drawbarline.errors import SettlementError
from drawbarline.marks import ReportingMark
from drawbarline.money import Money, total
from drawbarline.settlement.period import Period, PeriodRegister


class PostingKind(Enum):
    """What a line on the account is for."""

    CAR_HIRE = "car hire"
    MILEAGE = "mileage"
    DEMURRAGE = "demurrage"
    REPAIRS = "repairs"
    RECLAIM = "reclaim"
    ADJUSTMENT = "adjustment"
    PAYMENT = "payment"

    @property
    def text(self) -> str:
        return self.value


@dataclass(frozen=True, slots=True)
class Posting:
    """One line of the account. Positive is money we owe."""

    road: ReportingMark
    period: Period
    kind: PostingKind
    amount: Money
    on: date
    reference: str = ""

    def __post_init__(self) -> None:
        if not self.period.covers(self.on) and self.kind is not PostingKind.ADJUSTMENT:
            raise SettlementError(
                "a posting falls inside the month it is posted to",
                period=str(self.period),
                on=str(self.on),
            )

    @property
    def we_owe(self) -> bool:
        return self.amount.cents > 0

    @property
    def they_owe(self) -> bool:
        return self.amount.cents < 0

    def __str__(self) -> str:
        way = "to" if self.we_owe else "from"
        return f"{self.period} {self.kind.text} {way} {self.road} {abs(self.amount)}"


class Account:
    """Everything posted against one road."""

    def __init__(self, road: ReportingMark) -> None:
        self.road = road
        self._postings: list[Posting] = []

    def post(self, posting: Posting) -> Posting:
        if posting.road != self.road:
            raise SettlementError(
                "that posting belongs to another road",
                account=str(self.road),
                posting=str(posting.road),
            )
        self._postings.append(posting)
        return posting

    def postings(self, period: Period | None = None) -> list[Posting]:
        found = [one for one in self._postings if period is None or one.period == period]
        return sorted(found, key=lambda one: (one.period, one.on, one.kind.name))

    def balance(self, period: Period | None = None) -> Money:
        return total(one.amount for one in self.postings(period))

    def by_kind(self, period: Period | None = None) -> dict[PostingKind, Money]:
        running: dict[PostingKind, Money] = defaultdict(lambda: Money(0))
        for one in self.postings(period):
            running[one.kind] = running[one.kind] + one.amount
        return dict(running)

    def balance_through(self, period: Period) -> Money:
        """Everything up to and including a month, which is a closing balance."""
        return total(one.amount for one in self._postings if not period < one.period)

    def __len__(self) -> int:
        return len(self._postings)


class Ledger:
    """One account for every road we deal with."""

    def __init__(self, periods: PeriodRegister | None = None) -> None:
        self._accounts: dict[ReportingMark, Account] = {}
        self._periods = periods or PeriodRegister()

    def post(self, posting: Posting) -> Posting:
        self._periods.require_open(posting.period)
        account = self._accounts.get(posting.road)
        if account is None:
            account = Account(posting.road)
            self._accounts[posting.road] = account
        return account.post(posting)

    def account(self, road: ReportingMark) -> Account:
        return self._accounts.get(road) or Account(road)

    def roads(self) -> list[ReportingMark]:
        return sorted(self._accounts)

    def net(self, period: Period | None = None) -> dict[ReportingMark, Money]:
        return {road: self.account(road).balance(period) for road in self.roads()}

    def we_owe(self, period: Period | None = None) -> Money:
        return total(amount for amount in self.net(period).values() if amount.cents > 0)

    def owed_to_us(self, period: Period | None = None) -> Money:
        return total(abs(amount) for amount in self.net(period).values() if amount.cents < 0)

    def position(self, period: Period | None = None) -> Money:
        """One number for the whole pool, positive when we are behind."""
        return total(self.net(period).values())

    def __len__(self) -> int:
        return sum(len(account) for account in self._accounts.values())
