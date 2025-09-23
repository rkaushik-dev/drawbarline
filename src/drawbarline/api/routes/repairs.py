"""Repair billing routes.

Filing a card and pricing it are separate here for the same reason they are
separate on the command line: the price master moves and a card filed today
may be priced against a filing made last week. So the post returns the card as
it prices right now and the get returns it priced as of whenever it is asked
for.

The exception routes carry the ninety day clock with them. A late exception is
a conflict rather than a bad request, because the body was fine and the world
had moved on.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends, status
from pydantic import BaseModel, ConfigDict

from drawbarline.api.deps import read_only, unit_of_work
from drawbarline.errors import BillingError
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.money import Money
from drawbarline.persistence.uow import UnitOfWork
from drawbarline.repairs.card import Defect, PricedCard, RepairCard, price
from drawbarline.repairs.codes import JobCode, Responsibility, WhyMade
from drawbarline.repairs.objection import Ground, Objection, Outcome, Resolution

router = APIRouter(prefix="/repairs", tags=["repairs"])


class DefectIn(BaseModel):
    """One line of a card as it is filed."""

    model_config = ConfigDict(extra="forbid")

    job: str
    why_made: str
    quantity: int = 1
    part_years: int = 0
    overridden: str | None = None
    note: str = ""


class CardIn(BaseModel):
    """A repair card as it is filed."""

    model_config = ConfigDict(extra="forbid")

    number: int
    car: str
    performed_by: str
    performed_on: date
    station: str
    defects: list[DefectIn]


class LineOut(BaseModel):
    job: str
    why_made: str
    quantity: int
    labor: int
    material: int
    amount: int
    answered_by: str


class ObjectionIn(BaseModel):
    """An exception against one line."""

    model_config = ConfigDict(extra="forbid")

    line: int
    ground: str
    disputed: int
    filed_on: date
    note: str = ""


class ObjectionOut(BaseModel):
    card_number: int
    line: int
    ground: str
    disputed: int
    filed_on: date
    outcome: str | None = None


class AnswerIn(BaseModel):
    model_config = ConfigDict(extra="forbid")

    allowed: int


class CardOut(BaseModel):
    """A card with the money on it."""

    number: int
    car: str
    performed_by: str
    performed_on: date
    station: str
    lines: list[LineOut]
    total: int
    to_owner: int
    borne_by_the_road: int

    @classmethod
    def of(cls, priced: PricedCard) -> CardOut:
        card = priced.card
        return cls(
            number=card.number,
            car=str(card.car),
            performed_by=str(card.performed_by),
            performed_on=card.on,
            station=card.station,
            lines=[
                LineOut(
                    job=str(one.defect.job),
                    why_made=str(one.defect.why),
                    quantity=one.defect.quantity,
                    labor=one.labor.cents,
                    material=one.material.cents,
                    amount=one.amount.cents,
                    answered_by=one.answered_by.text,
                )
                for one in priced.lines
            ],
            total=priced.total.cents,
            to_owner=priced.billed_to_owner.cents,
            borne_by_the_road=priced.borne_by_the_road.cents,
        )


def defect_of(filing: DefectIn) -> Defect:
    who = None
    if filing.overridden:
        try:
            who = Responsibility[filing.overridden.strip().upper()]
        except KeyError as bad:
            raise BillingError("no such responsibility", given=filing.overridden) from bad
    return Defect(
        job=JobCode(filing.job.strip()),
        why=WhyMade(filing.why_made.strip()),
        quantity=filing.quantity,
        overridden=who,
        part_years=filing.part_years,
        note=filing.note,
    )


def ground_of(text: str) -> Ground:
    try:
        return Ground[text.strip().upper()]
    except KeyError as bad:
        raise BillingError("no such ground", given=text) from bad


def priced_card(work: UnitOfWork, number: int) -> PricedCard:
    card = work.cards.get(number)
    return price(card, work.repair_pricing.master(), work.repair_pricing.labor_rates())


@router.post("/cards", status_code=status.HTTP_201_CREATED, response_model=CardOut)
def file_card(filing: CardIn, work: UnitOfWork = Depends(unit_of_work)) -> CardOut:
    """File a repair card."""
    card = RepairCard(
        number=filing.number,
        car=CarMark.parse(filing.car),
        performed_by=ReportingMark.parse(filing.performed_by),
        on=filing.performed_on,
        station=filing.station.strip().upper(),
        defects=tuple(defect_of(one) for one in filing.defects),
    )
    work.cards.file(card)
    return CardOut.of(price(card, work.repair_pricing.master(), work.repair_pricing.labor_rates()))


@router.get("/cards", response_model=list[CardOut])
def list_cards(
    car: str | None = None,
    road: str | None = None,
    work: UnitOfWork = Depends(read_only),
) -> list[CardOut]:
    """Cards for one car or one road."""
    if car:
        cards = work.cards.for_car(CarMark.parse(car))
    elif road:
        cards = work.cards.by_road(ReportingMark.parse(road))
    else:
        raise BillingError("ask for one car or one road")
    master = work.repair_pricing.master()
    rates = work.repair_pricing.labor_rates()
    return [CardOut.of(price(one, master, rates)) for one in cards]


@router.get("/exceptions", response_model=list[ObjectionOut])
def open_exceptions(work: UnitOfWork = Depends(read_only)) -> list[ObjectionOut]:
    """Everything filed and not answered."""
    return [
        ObjectionOut(
            card_number=one.card_number,
            line=one.line,
            ground=one.ground.text,
            disputed=one.disputed.cents,
            filed_on=one.filed_on,
        )
        for one in work.cards.open_objections()
    ]


@router.get("/cards/{number}", response_model=CardOut)
def show_card(number: int, work: UnitOfWork = Depends(read_only)) -> CardOut:
    """One card, priced as of now."""
    return CardOut.of(priced_card(work, number))


@router.post(
    "/cards/{number}/exceptions",
    status_code=status.HTTP_201_CREATED,
    response_model=ObjectionOut,
)
def take_exception(
    number: int, filing: ObjectionIn, work: UnitOfWork = Depends(unit_of_work)
) -> ObjectionOut:
    """Take exception to one line of a card."""
    objection = Objection(
        card_number=number,
        line=filing.line,
        ground=ground_of(filing.ground),
        disputed=Money(filing.disputed),
        filed_on=filing.filed_on,
        note=filing.note,
    )
    work.cards.take_exception(objection)
    return ObjectionOut(
        card_number=number,
        line=objection.line,
        ground=objection.ground.text,
        disputed=objection.disputed.cents,
        filed_on=objection.filed_on,
    )


@router.post("/cards/{number}/exceptions/{line}/answer", response_model=ObjectionOut)
def answer(
    number: int,
    line: int,
    filing: AnswerIn,
    work: UnitOfWork = Depends(unit_of_work),
) -> ObjectionOut:
    """Answer an exception by allowing some or none of it."""
    standing = [
        one
        for one in work.cards.open_objections()
        if one.card_number == number and one.line == line
    ]
    if not standing:
        raise BillingError("no open exception on that line", card=number, line=line)
    objection = standing[0]
    allowed = Money(filing.allowed)
    if allowed.is_zero:
        outcome = Outcome.DECLINED
    elif allowed == objection.disputed:
        outcome = Outcome.ALLOWED
    else:
        outcome = Outcome.PART_ALLOWED
    work.cards.answer(objection, Resolution(objection, outcome, allowed))
    return ObjectionOut(
        card_number=number,
        line=line,
        ground=objection.ground.text,
        disputed=objection.disputed.cents,
        filed_on=objection.filed_on,
        outcome=outcome.value,
    )
