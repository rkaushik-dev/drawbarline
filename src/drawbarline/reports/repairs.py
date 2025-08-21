"""The repair billing reports.

Two questions get asked every month. What did each road bill us, and how much
of it is ours to pay rather than theirs to eat. Those are different numbers and
the report shows both, because a road whose cards are mostly handling damage
is a road doing damage, and that is a conversation to have before the bills
get any bigger.

The exception report is the other side. Anything filed and not answered is
money in the air, and the filing clock means an exception nobody answers
eventually stops being ours to argue.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from drawbarline.marks import ReportingMark
from drawbarline.money import Money, total
from drawbarline.repairs.card import PricedCard
from drawbarline.repairs.codes import Responsibility
from drawbarline.repairs.objection import Objection, must_file_by
from drawbarline.reports.render import Align, Column, Table, heading, money


@dataclass(frozen=True, slots=True)
class RoadTotals:
    """What one road billed and how it split."""

    road: ReportingMark
    cards: int
    lines: int
    billed: Money
    to_owner: Money

    @property
    def borne_by_the_road(self) -> Money:
        return self.billed - self.to_owner


def by_road(cards: list[PricedCard]) -> list[RoadTotals]:
    """Gather priced cards by the road that wrote them."""
    gathered: dict[ReportingMark, list[PricedCard]] = {}
    for one in cards:
        gathered.setdefault(one.card.performed_by, []).append(one)
    return [
        RoadTotals(
            road=road,
            cards=len(found),
            lines=sum(len(one.lines) for one in found),
            billed=total(one.total for one in found),
            to_owner=total(one.billed_to_owner for one in found),
        )
        for road, found in sorted(gathered.items(), key=lambda pair: str(pair[0]))
    ]


def billing_report(cards: list[PricedCard], title: str) -> list[str]:
    """One line a road, with the split that matters."""
    table = Table(
        [
            Column("road"),
            Column("cards", Align.RIGHT),
            Column("lines", Align.RIGHT),
            Column("billed", Align.RIGHT),
            Column("to owner", Align.RIGHT),
            Column("their cost", Align.RIGHT),
        ]
    )
    found = by_road(cards)
    for one in found:
        table.add(
            str(one.road),
            str(one.cards),
            str(one.lines),
            money(one.billed),
            money(one.to_owner),
            money(one.borne_by_the_road),
        )
    table.total(
        f"{len(found)} roads",
        str(sum(one.cards for one in found)),
        str(sum(one.lines for one in found)),
        money(total(one.billed for one in found)),
        money(total(one.to_owner for one in found)),
        money(total(one.borne_by_the_road for one in found)),
    )
    return [*heading("repair billing", title), *table.lines()]


def responsibility_split(cards: list[PricedCard]) -> dict[Responsibility, Money]:
    """What each party answers for across a set of cards."""
    return {who: total(one.owed_by(who) for one in cards) for who in Responsibility}


def exception_report(
    objections: list[Objection], cards: dict[int, PricedCard], as_of: date
) -> list[str]:
    """Everything filed and not answered, with the days left to answer it."""
    table = Table(
        [
            Column("card"),
            Column("line", Align.RIGHT),
            Column("ground"),
            Column("disputed", Align.RIGHT),
            Column("filed"),
            Column("days left", Align.RIGHT),
        ]
    )
    ordered = sorted(objections, key=lambda one: (one.card_number, one.line))
    for one in ordered:
        priced = cards.get(one.card_number)
        due = must_file_by(priced.card.on) if priced else one.filed_on
        table.add(
            str(one.card_number),
            str(one.line),
            one.ground.text,
            money(one.disputed),
            str(one.filed_on),
            str((due - as_of).days),
        )
    table.total(
        f"{len(ordered)} open",
        "",
        "",
        money(total(one.disputed for one in ordered)),
        "",
        "",
    )
    return [*heading("open repair exceptions", str(as_of)), *table.lines()]
