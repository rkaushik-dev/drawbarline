"""Taking exception to a repair card.

An owner who thinks a card is wrong files an exception against it. Not against
the whole card, against a line, because half a card being right is the normal
case. The exception says which line, what is wrong with it, and how much of
the amount the owner is refusing to pay.

There is a clock on it. An exception filed more than the allowed days after
the card is out of time and the owner pays whatever is on the card, right or
wrong. That rule exists because a repair billed in March and disputed in
December cannot be investigated by anybody who was there, and it is enforced
here rather than left to whoever is reading the mail.

A road that disagrees with the exception counters it. After that the two
either settle on an amount or it goes to arbitration, which is somebody else's
problem and simply an outcome as far as this is concerned.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta
from enum import Enum

from drawbarline.errors import BillingError
from drawbarline.money import Money
from drawbarline.repairs.card import PricedCard

FILING_DAYS = 90


class Ground(Enum):
    """What is being said to be wrong with the line."""

    PRICE = "priced against the wrong master line"
    RESPONSIBILITY = "billed to the wrong party"
    DEPRECIATION = "depreciation not allowed for"
    DUPLICATE = "already billed on another card"
    NOT_OUR_CAR = "the car is not ours to answer for"
    NO_AUTHORITY = "the work was never authorised"

    @property
    def text(self) -> str:
        return self.value


class Outcome(Enum):
    """Where an exception ended up."""

    OPEN = "open"
    ALLOWED = "allowed in full"
    PART_ALLOWED = "allowed in part"
    DECLINED = "declined"
    ARBITRATION = "gone to arbitration"


def must_file_by(card_on: date) -> date:
    return card_on + timedelta(days=FILING_DAYS)


@dataclass(frozen=True, slots=True)
class Objection:
    """One owner, one line of one card, one reason."""

    card_number: int
    line: int
    ground: Ground
    disputed: Money
    filed_on: date
    note: str = ""

    def __post_init__(self) -> None:
        if self.line < 0:
            raise BillingError("a line is numbered from zero", line=self.line)
        if self.disputed.cents <= 0:
            raise BillingError(
                "an exception disputes an amount", card=self.card_number, line=self.line
            )

    def __str__(self) -> str:
        return f"card {self.card_number} line {self.line} {self.ground.text} {self.disputed}"


@dataclass(frozen=True, slots=True)
class Resolution:
    """How an exception was answered."""

    objection: Objection
    outcome: Outcome
    allowed: Money

    def __post_init__(self) -> None:
        if self.allowed.cents < 0:
            raise BillingError("an allowance is not negative")
        if self.allowed > self.objection.disputed:
            raise BillingError(
                "more was allowed than was ever disputed",
                card=self.objection.card_number,
            )

    @property
    def still_owed(self) -> Money:
        return self.objection.disputed - self.allowed

    def __str__(self) -> str:
        return f"{self.objection} {self.outcome.value} {self.allowed}"


class ExceptionRegister:
    """Every exception filed against the cards one road sent us."""

    def __init__(self) -> None:
        self._filed: dict[tuple[int, int], Objection] = {}
        self._settled: dict[tuple[int, int], Resolution] = {}

    def file(self, card: PricedCard, objection: Objection) -> Objection:
        if objection.card_number != card.card.number:
            raise BillingError(
                "that exception is against a different card",
                card=card.card.number,
                against=objection.card_number,
            )
        if objection.line >= len(card.lines):
            raise BillingError(
                "that card has no such line", card=card.card.number, line=objection.line
            )
        if objection.filed_on > must_file_by(card.card.on):
            raise BillingError(
                "that exception is out of time",
                card=card.card.number,
                by=str(must_file_by(card.card.on)),
            )
        line = card.lines[objection.line]
        if objection.disputed > line.amount:
            raise BillingError(
                "more is disputed than the line was billed for",
                card=card.card.number,
                line=objection.line,
            )
        key = (objection.card_number, objection.line)
        if key in self._filed:
            raise BillingError("that line is already under exception", card=key[0], line=key[1])
        self._filed[key] = objection
        return objection

    def settle(self, objection: Objection, allowed: Money) -> Resolution:
        key = (objection.card_number, objection.line)
        if key not in self._filed:
            raise BillingError("no exception is on file for that line", card=key[0], line=key[1])
        if key in self._settled:
            raise BillingError("that exception is already settled", card=key[0], line=key[1])
        if allowed.is_zero:
            outcome = Outcome.DECLINED
        elif allowed == objection.disputed:
            outcome = Outcome.ALLOWED
        else:
            outcome = Outcome.PART_ALLOWED
        found = Resolution(objection, outcome, allowed)
        self._settled[key] = found
        return found

    def refer(self, objection: Objection) -> Resolution:
        """Send it to arbitration, which settles nothing but closes our file."""
        key = (objection.card_number, objection.line)
        if key not in self._filed:
            raise BillingError("no exception is on file for that line", card=key[0], line=key[1])
        found = Resolution(objection, Outcome.ARBITRATION, Money(0))
        self._settled[key] = found
        return found

    def open_objections(self) -> list[Objection]:
        return sorted(
            (one for key, one in self._filed.items() if key not in self._settled),
            key=lambda one: (one.card_number, one.line),
        )

    def resolutions(self) -> list[Resolution]:
        return sorted(
            self._settled.values(), key=lambda one: (one.objection.card_number, one.objection.line)
        )

    def allowed_against(self, card_number: int) -> Money:
        found = [
            one.allowed
            for one in self._settled.values()
            if one.objection.card_number == card_number
        ]
        return Money(sum(one.cents for one in found))

    def __len__(self) -> int:
        return len(self._filed)
