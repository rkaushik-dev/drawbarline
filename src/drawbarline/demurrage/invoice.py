"""The demurrage invoice.

What goes out to the customer at the end of the month. One line a car, the
days it was held, and what those days came to, with the cars that came back
inside free time counted but not charged. The free ones are on the invoice on
purpose: a customer who cannot see the cars they turned quickly has no way to
tell whether the ones they are being billed for are unusual.

A shipper on an average agreement gets a different document. One line, the
credits, the debits, and the net. Mixing the two on one invoice has been tried
and it produces a total nobody can check, because the per car amounts on an
agreement do not add up to the amount billed and are not supposed to.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from drawbarline.demurrage.average import Settlement
from drawbarline.demurrage.counting import Detention
from drawbarline.demurrage.tariff import Tariff
from drawbarline.errors import TariffError
from drawbarline.marks import CarMark
from drawbarline.money import Money, total


@dataclass(frozen=True, slots=True)
class Line:
    """One car on an invoice, or the whole month on an average one."""

    car: CarMark | None
    industry: str
    placed_on: date
    counted_to: date
    days: int
    amount: Money

    @property
    def charged(self) -> bool:
        return self.days > 0

    def __str__(self) -> str:
        who = str(self.car) if self.car else "all cars"
        return f"{who} {self.industry} {self.days} days {self.amount}"


@dataclass(frozen=True, slots=True)
class Invoice:
    """A month of demurrage for one shipper."""

    number: int
    shipper: str
    month_of: date
    lines: tuple[Line, ...]
    average: bool = False

    def __post_init__(self) -> None:
        if self.number < 1:
            raise TariffError("an invoice is numbered from one", number=self.number)
        if not self.shipper.strip():
            raise TariffError("an invoice names a shipper", number=self.number)
        if self.month_of.day != 1:
            raise TariffError("an invoice covers a whole month", month_of=str(self.month_of))

    @property
    def total(self) -> Money:
        return total(line.amount for line in self.lines)

    @property
    def days(self) -> int:
        return sum(line.days for line in self.lines)

    @property
    def cars(self) -> int:
        return len(self.lines)

    @property
    def charged_cars(self) -> int:
        return len([line for line in self.lines if line.charged])

    @property
    def free_cars(self) -> int:
        return self.cars - self.charged_cars

    @property
    def empty(self) -> bool:
        return self.total.is_zero

    def for_car(self, car: CarMark) -> Line | None:
        for line in self.lines:
            if line.car == car:
                return line
        return None

    def __str__(self) -> str:
        kind = "average" if self.average else "demurrage"
        return f"{kind} invoice {self.number} {self.shipper} {self.month_of:%Y-%m} {self.total}"


def line_for(detention: Detention, tariff: Tariff) -> Line:
    return Line(
        car=detention.car,
        industry=detention.industry,
        placed_on=detention.placed_on,
        counted_to=detention.counted_to,
        days=detention.chargeable,
        amount=detention.charge(tariff),
    )


def assemble(
    number: int,
    shipper: str,
    month_of: date,
    detentions: list[Detention],
    tariff: Tariff,
) -> Invoice:
    """Cut a per car invoice, free cars and all, in car order."""
    lines = tuple(line_for(one, tariff) for one in sorted(detentions, key=lambda one: str(one.car)))
    return Invoice(number=number, shipper=shipper, month_of=month_of, lines=lines)


def assemble_average(number: int, settlement: Settlement, month_of: date) -> Invoice:
    """Cut the one line invoice a shipper on an agreement gets."""
    line = Line(
        car=None,
        industry="average agreement",
        placed_on=month_of,
        counted_to=month_of,
        days=settlement.net_debits,
        amount=settlement.amount,
    )
    return Invoice(
        number=number,
        shipper=settlement.shipper,
        month_of=month_of,
        lines=(line,),
        average=True,
    )
