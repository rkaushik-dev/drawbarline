"""The average agreement.

A shipper who moves cars in volume can ask to be billed on an average instead
of car by car. Every car they turn back inside the first free day earns a
credit. Every chargeable day earns a debit. At the end of the month the two
are set against each other and only the debits left over are billed.

The part that makes it an agreement and not a discount is that the credits
expire. They offset that month and no other. A shipper who spends October
building up credits cannot spend them in November, and a shipper who has a bad
November cannot borrow against December. Carrying a balance forward would turn
a month end into a running account and there would be nothing to settle.

Debits under an agreement are billed at their own flat rate, not up the
tariff steps, because the point of the agreement is that the customer trades
the cheap early days away for protection against the expensive late ones.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from drawbarline.demurrage.counting import Detention
from drawbarline.errors import TariffError
from drawbarline.marks import CarMark
from drawbarline.money import Money


@dataclass(frozen=True, slots=True)
class AverageAgreement:
    """A shipper's standing agreement, and what a net debit costs under it."""

    shipper: str
    daily_cents: int
    effective: date
    expires: date | None = None

    def __post_init__(self) -> None:
        if self.daily_cents < 0:
            raise TariffError("a debit does not pay the customer", cents=self.daily_cents)
        if self.expires is not None and self.expires < self.effective:
            raise TariffError(
                "an agreement cannot expire before it starts",
                shipper=self.shipper,
                effective=str(self.effective),
            )

    def covers(self, day: date) -> bool:
        if day < self.effective:
            return False
        return self.expires is None or day <= self.expires

    @property
    def daily(self) -> Money:
        return Money(self.daily_cents)

    def __str__(self) -> str:
        return f"{self.shipper} at {self.daily} a debit from {self.effective}"


@dataclass(frozen=True, slots=True)
class Entry:
    """What one car did to the month's balance."""

    car: CarMark
    industry: str
    credits: int
    debits: int

    def __post_init__(self) -> None:
        if self.credits and self.debits:
            raise TariffError("a car earns a credit or owes debits, never both", car=str(self.car))

    def __str__(self) -> str:
        if self.credits:
            return f"{self.car} {self.credits} credit"
        return f"{self.car} {self.debits} debits"


def entry_for(detention: Detention) -> Entry:
    """Read one detention as a credit or as debits."""
    if detention.chargeable:
        return Entry(detention.car, detention.industry, 0, detention.chargeable)
    earned = 1 if detention.counted_to <= detention.clock_starts else 0
    return Entry(detention.car, detention.industry, earned, 0)


@dataclass(frozen=True, slots=True)
class Settlement:
    """What a month came to under an agreement."""

    shipper: str
    credits: int
    debits: int
    amount: Money

    @property
    def net_debits(self) -> int:
        return max(self.debits - self.credits, 0)

    @property
    def wasted_credits(self) -> int:
        """Credits the shipper earned and had nothing to spend on."""
        return max(self.credits - self.debits, 0)

    def __str__(self) -> str:
        return f"{self.shipper} {self.credits} credits {self.debits} debits {self.amount}"


class AverageLedger:
    """One shipper, one month, every car that touched the balance."""

    def __init__(self, agreement: AverageAgreement, month_of: date) -> None:
        if not agreement.covers(month_of):
            raise TariffError(
                "no agreement was in force that month",
                shipper=agreement.shipper,
                month=str(month_of),
            )
        self.agreement = agreement
        self.month_of = month_of
        self._entries: list[Entry] = []

    def post(self, detention: Detention) -> Entry:
        entry = entry_for(detention)
        self._entries.append(entry)
        return entry

    @property
    def entries(self) -> list[Entry]:
        return list(self._entries)

    @property
    def credits(self) -> int:
        return sum(entry.credits for entry in self._entries)

    @property
    def debits(self) -> int:
        return sum(entry.debits for entry in self._entries)

    def settle(self) -> Settlement:
        credits = self.credits
        debits = self.debits
        owed = max(debits - credits, 0)
        return Settlement(
            shipper=self.agreement.shipper,
            credits=credits,
            debits=debits,
            amount=self.agreement.daily * owed,
        )

    def __len__(self) -> int:
        return len(self._entries)
