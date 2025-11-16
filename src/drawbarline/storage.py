"""Storage, which is what demurrage turns into when nobody is coming.

Demurrage is a lever. It climbs so that a customer works their cars, and the
climb only makes sense while somebody is trying to. A car that has stood on a
track for six weeks is not being worked slowly, it is being stored, and
billing storage at the top demurrage step is billing a lever nobody is
pulling.

So the tariff converts. After a stated number of chargeable days the car comes
off the demurrage schedule and onto a flat storage rate, and the storage rate
counts every day rather than only chargeable ones, because a car in storage
occupies the track on a Sunday exactly as much as on a Tuesday.

The conversion day belongs to demurrage. A car on its tenth chargeable day
when storage starts at ten is billed demurrage for that day and storage from
the next, which is the reading both sides of every argument about this have
eventually settled on.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta

from drawbarline.days import is_chargeable
from drawbarline.demurrage.counting import Detention
from drawbarline.demurrage.tariff import Tariff
from drawbarline.errors import TariffError
from drawbarline.money import Money


@dataclass(frozen=True, slots=True)
class StorageTariff:
    """When a car stops being late and starts being stored."""

    code: str
    effective: date
    after_days: int
    daily_cents: int

    def __post_init__(self) -> None:
        if not self.code.strip():
            raise TariffError("storage is published under a number")
        if self.after_days < 1:
            raise TariffError("storage starts after at least one chargeable day", code=self.code)
        if self.daily_cents < 0:
            raise TariffError("storage does not pay the customer", code=self.code)

    @property
    def daily(self) -> Money:
        return Money(self.daily_cents)

    def __str__(self) -> str:
        return f"storage {self.code} at {self.daily} a day after {self.after_days} days"


@dataclass(frozen=True, slots=True)
class Split:
    """One detention divided between the two schedules."""

    car: str
    demurrage_days: int
    storage_days: int
    converted_on: date | None

    @property
    def converted(self) -> bool:
        return self.storage_days > 0

    def demurrage(self, tariff: Tariff) -> Money:
        return tariff.charge(self.demurrage_days)

    def storage(self, tariff: StorageTariff) -> Money:
        return tariff.daily * self.storage_days

    def total(self, demurrage_tariff: Tariff, storage_tariff: StorageTariff) -> Money:
        return self.demurrage(demurrage_tariff) + self.storage(storage_tariff)

    def __str__(self) -> str:
        if not self.converted:
            return f"{self.car} {self.demurrage_days} days demurrage"
        return (
            f"{self.car} {self.demurrage_days} days demurrage then "
            f"{self.storage_days} days storage from {self.converted_on}"
        )


def conversion_day(detention: Detention, storage: StorageTariff) -> date | None:
    """The last day billed as demurrage, if the car got that far."""
    if detention.chargeable < storage.after_days:
        return None
    day = detention.free_until or detention.clock_starts
    counted = 0
    while counted < storage.after_days and day <= detention.counted_to:
        day += timedelta(days=1)
        if is_chargeable(day):
            counted += 1
    return day


def split(detention: Detention, storage: StorageTariff) -> Split:
    """Divide a detention between the demurrage schedule and the storage rate."""
    if detention.chargeable < storage.after_days:
        return Split(
            car=str(detention.car),
            demurrage_days=detention.chargeable,
            storage_days=0,
            converted_on=None,
        )
    converted = conversion_day(detention, storage)
    if converted is None:
        raise TariffError(
            "a detention past the conversion has a conversion day",
            car=str(detention.car),
        )
    stored = (detention.counted_to - converted).days
    return Split(
        car=str(detention.car),
        demurrage_days=storage.after_days,
        storage_days=max(stored, 0),
        converted_on=converted,
    )


def charge(detention: Detention, demurrage_tariff: Tariff, storage_tariff: StorageTariff) -> Money:
    """What a detention comes to once the conversion is taken into account."""
    return split(detention, storage_tariff).total(demurrage_tariff, storage_tariff)
