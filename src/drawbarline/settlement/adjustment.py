"""Correcting a month that has already been agreed, and taking payment.

A closed month is closed. When something in it turns out to be wrong, and
something always does, the answer is not to open it: it is to post an
adjustment into the current month that says which closed month it corrects and
why. Both roads then see the same two numbers, the month as it was agreed and
the correction, and nobody has to explain why last February changed.

A remittance is the other direction. A road pays a statement, the payment goes
in against the month it settles, and the balance comes down. Payments are
never netted against charges in the same posting, because a road that pays
part of a statement is disputing the rest and the ledger has to show which
part.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from drawbarline.errors import SettlementError
from drawbarline.limits import REFERENCE
from drawbarline.marks import ReportingMark
from drawbarline.money import Money
from drawbarline.settlement.ledger import Ledger, Posting, PostingKind
from drawbarline.settlement.period import Period, PeriodRegister


@dataclass(frozen=True, slots=True)
class Adjustment:
    """A correction to a month that has been agreed."""

    road: ReportingMark
    corrects: Period
    posted_in: Period
    amount: Money
    on: date
    reason: str

    def __post_init__(self) -> None:
        if self.amount.is_zero:
            raise SettlementError("an adjustment moves something", road=str(self.road))
        if not self.reason.strip():
            raise SettlementError("an adjustment carries a reason", road=str(self.road))
        if not self.corrects < self.posted_in:
            raise SettlementError(
                "an adjustment is posted after the month it corrects",
                corrects=str(self.corrects),
                posted_in=str(self.posted_in),
            )

    @property
    def reference(self) -> str:
        """What the other road reads on the statement line."""
        text = f"adjusts {self.corrects.label}: {self.reason}"
        return text[:REFERENCE]

    def __str__(self) -> str:
        way = "to" if self.amount.cents > 0 else "from"
        return f"{abs(self.amount)} {way} {self.road} adjusting {self.corrects}"


@dataclass(frozen=True, slots=True)
class Remittance:
    """A payment against a month."""

    number: int
    road: ReportingMark
    settles: Period
    amount: Money
    received_on: date
    reference: str = ""

    def __post_init__(self) -> None:
        if self.number < 1:
            raise SettlementError("a remittance is numbered from one", number=self.number)
        if self.amount.cents <= 0:
            raise SettlementError("a remittance is an amount", number=self.number)
        if self.received_on < self.settles.last_day:
            raise SettlementError(
                "a payment comes after the month it settles",
                number=self.number,
                settles=str(self.settles),
            )

    def __str__(self) -> str:
        return f"remittance {self.number} {self.road} {self.amount} for {self.settles}"


def post_adjustment(ledger: Ledger, periods: PeriodRegister, adjustment: Adjustment) -> Posting:
    """Put an adjustment into the open month, naming the closed one it corrects."""
    if periods.is_open(adjustment.corrects):
        raise SettlementError(
            "that month is still open, so correct it there",
            corrects=str(adjustment.corrects),
        )
    return ledger.post(
        Posting(
            road=adjustment.road,
            period=adjustment.posted_in,
            kind=PostingKind.ADJUSTMENT,
            amount=adjustment.amount,
            on=adjustment.on,
            reference=adjustment.reference,
        )
    )


def apply_remittance(ledger: Ledger, remittance: Remittance) -> Posting:
    """Put a payment in against the month it settles."""
    reference = remittance.reference or f"remittance {remittance.number}"
    return ledger.post(
        Posting(
            road=remittance.road,
            period=remittance.settles,
            kind=PostingKind.PAYMENT,
            amount=-remittance.amount,
            on=remittance.settles.last_day,
            reference=reference[:REFERENCE],
        )
    )


def outstanding(ledger: Ledger, road: ReportingMark, through: Period) -> Money:
    """What is still owed on one road through a month, payments taken off."""
    return ledger.account(road).balance_through(through)


def overpaid(ledger: Ledger, road: ReportingMark, through: Period) -> bool:
    """Whether a road has paid us more than we ever billed them."""
    return outstanding(ledger, road, through).cents < 0
