"""Disputes against a statement, and how old they are getting.

A road that disagrees with a statement does not simply not pay it. They pay
the part they agree with and raise a dispute for the rest, and that disputed
amount sits on the account until somebody settles it. The number that matters
to the office is not how many disputes there are, it is how old they are, and
a dispute nobody has touched in four months is a different problem from one
raised last week.

So aging is the report this module exists for. Buckets of thirty days, oldest
last, and the ninety day bucket kept open ended because there is no useful
distinction between a dispute four months old and one nine months old except
how embarrassing it is.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from enum import Enum

from drawbarline.errors import SettlementError
from drawbarline.marks import ReportingMark
from drawbarline.money import Money, total
from drawbarline.settlement.period import Period

BUCKET_DAYS = 30
OLDEST_BUCKET = 90


class Ground(Enum):
    """What the road says is wrong with the statement."""

    CAR_NOT_OURS = "the car was never on their rail"
    HOURS = "the hours do not agree with their record"
    RATE = "the rate applied is not the one in effect"
    MILEAGE = "the miles do not agree"
    REPAIR = "the repair card is under exception"
    DUPLICATE = "billed twice"
    OPENING_BALANCE = "the opening balance does not agree"

    @property
    def text(self) -> str:
        return self.value


class Standing(Enum):
    OPEN = "open"
    SETTLED = "settled"
    WITHDRAWN = "withdrawn"


@dataclass(frozen=True, slots=True)
class Dispute:
    """One road, one statement, one amount they are not paying."""

    reference: int
    road: ReportingMark
    period: Period
    ground: Ground
    amount: Money
    raised_on: date
    note: str = ""

    def __post_init__(self) -> None:
        if self.reference < 1:
            raise SettlementError("a dispute is numbered from one", reference=self.reference)
        if self.amount.cents <= 0:
            raise SettlementError("a dispute is for an amount", reference=self.reference)
        if self.raised_on < self.period.last_day:
            raise SettlementError(
                "a dispute comes after the month it is against",
                reference=self.reference,
                period=str(self.period),
            )

    def days_old(self, as_of: date) -> int:
        if as_of < self.raised_on:
            raise SettlementError("that date is before the dispute", reference=self.reference)
        return (as_of - self.raised_on).days

    def __str__(self) -> str:
        return f"dispute {self.reference} {self.road} {self.period} {self.amount}"


@dataclass(frozen=True, slots=True)
class Aging:
    """Disputed money by how long it has been sitting."""

    buckets: tuple[tuple[str, Money], ...]

    @property
    def total(self) -> Money:
        return total(amount for _, amount in self.buckets)

    def bucket(self, label: str) -> Money:
        for name, amount in self.buckets:
            if name == label:
                return amount
        raise SettlementError("no such bucket", label=label)

    @property
    def oldest(self) -> Money:
        return self.buckets[-1][1]

    def __str__(self) -> str:
        parts = " ".join(f"{name} {amount}" for name, amount in self.buckets)
        return f"{parts} total {self.total}"


def bucket_labels() -> tuple[str, ...]:
    labels = []
    start = 0
    while start < OLDEST_BUCKET:
        labels.append(f"{start + 1} to {start + BUCKET_DAYS}")
        start += BUCKET_DAYS
    labels.append(f"over {OLDEST_BUCKET}")
    return tuple(labels)


def bucket_for(days: int) -> str:
    labels = bucket_labels()
    if days >= OLDEST_BUCKET:
        return labels[-1]
    return labels[max(days - 1, 0) // BUCKET_DAYS]


class DisputeRegister:
    """Every dispute raised against us, open or not."""

    def __init__(self) -> None:
        self._disputes: dict[int, Dispute] = {}
        self._standing: dict[int, Standing] = {}

    def raise_it(self, dispute: Dispute) -> Dispute:
        if dispute.reference in self._disputes:
            raise SettlementError("that dispute is already on file", reference=dispute.reference)
        self._disputes[dispute.reference] = dispute
        self._standing[dispute.reference] = Standing.OPEN
        return dispute

    def _close(self, reference: int, standing: Standing) -> Dispute:
        if reference not in self._disputes:
            raise SettlementError("no such dispute", reference=reference)
        if self._standing[reference] is not Standing.OPEN:
            raise SettlementError("that dispute is already closed", reference=reference)
        self._standing[reference] = standing
        return self._disputes[reference]

    def settle(self, reference: int) -> Dispute:
        return self._close(reference, Standing.SETTLED)

    def withdraw(self, reference: int) -> Dispute:
        return self._close(reference, Standing.WITHDRAWN)

    def standing_of(self, reference: int) -> Standing:
        if reference not in self._standing:
            raise SettlementError("no such dispute", reference=reference)
        return self._standing[reference]

    def open_disputes(self, road: ReportingMark | None = None) -> list[Dispute]:
        found = [
            one
            for reference, one in self._disputes.items()
            if self._standing[reference] is Standing.OPEN and (road is None or one.road == road)
        ]
        return sorted(found, key=lambda one: one.reference)

    def open_amount(self, road: ReportingMark | None = None) -> Money:
        return total(one.amount for one in self.open_disputes(road))

    def aging(self, as_of: date, road: ReportingMark | None = None) -> Aging:
        running = dict.fromkeys(bucket_labels(), Money(0))
        for one in self.open_disputes(road):
            label = bucket_for(one.days_old(as_of))
            running[label] = running[label] + one.amount
        return Aging(tuple(running.items()))

    def __len__(self) -> int:
        return len(self._disputes)
