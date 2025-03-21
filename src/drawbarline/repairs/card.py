"""The billing repair card.

When a road repairs somebody else's car it writes a card. One line for each
thing it did, with the job code, the why made code, how many of the thing, and
how old the part that came off was. The card goes to the owner and the owner
either pays it or takes exception to it.

Pricing a card and deciding who pays for it are two different questions and
the card answers neither on its own. The price comes out of the master. The
responsibility comes off the why made code, or off an inspector who overrode
it and said why. A priced card carries both, split by party, because the owner
is only ever billed for their share and the road doing the work eats the rest
whether it likes it or not.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from drawbarline.errors import BillingError
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.mileage import milepost
from drawbarline.money import Money, total
from drawbarline.repairs.codes import JobCode, Responsibility, WhyMade, responsibility_for
from drawbarline.repairs.pricing import LaborRates, PriceMaster


@dataclass(frozen=True, slots=True)
class Defect:
    """One thing that was wrong and what was done about it."""

    job: JobCode
    why: WhyMade
    quantity: int = 1
    overridden: Responsibility | None = None
    part_years: int = 0
    note: str = ""

    def __post_init__(self) -> None:
        if self.quantity < 1:
            raise BillingError("a repair line covers at least one part", job=str(self.job))
        if self.part_years < 0:
            raise BillingError("a part has no negative age", job=str(self.job))
        if self.overridden is not None and not self.note.strip():
            raise BillingError("an overridden responsibility carries a reason", job=str(self.job))

    @property
    def answered_by(self) -> Responsibility:
        return responsibility_for(self.why, overridden=self.overridden)

    def __str__(self) -> str:
        return f"{self.job} why {self.why} x{self.quantity} to {self.answered_by.text}"


@dataclass(frozen=True, slots=True)
class RepairCard:
    """What one road did to one car on one day."""

    number: int
    car: CarMark
    performed_by: ReportingMark
    on: date
    station: str
    defects: tuple[Defect, ...]

    def __post_init__(self) -> None:
        if self.number < 1:
            raise BillingError("a card is numbered from one", number=self.number)
        if not self.defects:
            raise BillingError("a card with no repairs on it is not a card", number=self.number)
        milepost(self.station)

    @property
    def jobs(self) -> list[JobCode]:
        return [defect.job for defect in self.defects]

    def __str__(self) -> str:
        return f"card {self.number} {self.car} by {self.performed_by} on {self.on}"


@dataclass(frozen=True, slots=True)
class PricedLine:
    """One defect with the money on it."""

    defect: Defect
    labor: Money
    material: Money

    @property
    def amount(self) -> Money:
        return self.labor + self.material

    @property
    def answered_by(self) -> Responsibility:
        return self.defect.answered_by

    def __str__(self) -> str:
        return f"{self.defect.job} {self.amount}"


@dataclass(frozen=True, slots=True)
class PricedCard:
    """A card with every line priced and split by who answers for it."""

    card: RepairCard
    lines: tuple[PricedLine, ...]

    @property
    def total(self) -> Money:
        return total(line.amount for line in self.lines)

    def owed_by(self, who: Responsibility) -> Money:
        return total(line.amount for line in self.lines if line.answered_by is who)

    @property
    def billed_to_owner(self) -> Money:
        """What actually goes on the owner's bill, which is wear and joint work."""
        return total(line.amount for line in self.lines if line.answered_by.billable_to_owner)

    @property
    def borne_by_the_road(self) -> Money:
        return self.total - self.billed_to_owner

    def __str__(self) -> str:
        return f"{self.card} {self.total} of which {self.billed_to_owner} to the owner"


def price(card: RepairCard, master: PriceMaster, rates: LaborRates) -> PricedCard:
    """Put money on every line of a card."""
    rate = rates.rate_for(card.performed_by, card.on)
    lines = []
    for defect in card.defects:
        line = master.line_for(defect.job, card.on)
        labor = rate.cost_of(line.labor_hundredths * defect.quantity)
        material = master.material_for(defect.job, card.on, defect.part_years)
        lines.append(PricedLine(defect, labor, material * defect.quantity))
    return PricedCard(card, tuple(lines))
