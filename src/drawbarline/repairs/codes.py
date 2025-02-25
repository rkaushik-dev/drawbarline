"""The codes a repair is billed under.

Three of them ride on every line of a repair card. The job code says what was
done, four digits that reach a price in the master. The why made code says why
it was done, two digits, and it is the one that decides who pays. The
responsibility code says who that turned out to be, and it usually falls out
of the why made code but not always, which is why it is recorded separately
rather than derived at reading time.

Wear is the owner's. Damage is whoever was handling the car. That is the whole
principle and everything else is argument about which of the two a particular
crack is. The table below is this road's extract of the office manual, kept as
data because the manual is reissued every year and the codes move.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from enum import Enum

from drawbarline.errors import BillingError

_JOB = re.compile(r"^\d{4}$")
_WHY = re.compile(r"^\d{2}$")


class Responsibility(Enum):
    """Who the bill goes to."""

    OWNER = "owner"
    HANDLING_LINE = "handling line"
    THIRD_PARTY = "third party"
    JOINT = "joint"

    @property
    def text(self) -> str:
        return self.value

    @property
    def billable_to_owner(self) -> bool:
        return self in (Responsibility.OWNER, Responsibility.JOINT)


@dataclass(frozen=True, slots=True)
class JobCode:
    """Four digits saying what was done."""

    digits: str

    def __post_init__(self) -> None:
        if not isinstance(self.digits, str) or not _JOB.match(self.digits):
            raise BillingError("a job code is four digits", digits=self.digits)

    @property
    def family(self) -> str:
        """The first two digits, which group the jobs a pricer looks at together."""
        return self.digits[:2]

    def __str__(self) -> str:
        return self.digits


@dataclass(frozen=True, slots=True)
class WhyMade:
    """Two digits saying why it was done."""

    digits: str

    def __post_init__(self) -> None:
        if not isinstance(self.digits, str) or not _WHY.match(self.digits):
            raise BillingError("a why made code is two digits", digits=self.digits)
        if self.digits not in WHY_MADE:
            raise BillingError("no such why made code", digits=self.digits)

    @property
    def text(self) -> str:
        return WHY_MADE[self.digits][0]

    @property
    def usual_responsibility(self) -> Responsibility:
        """Who normally pays, before anybody argues about this particular car."""
        return WHY_MADE[self.digits][1]

    def __str__(self) -> str:
        return self.digits


# code, what it means, and who normally answers for it
WHY_MADE: dict[str, tuple[str, Responsibility]] = {
    "01": ("worn beyond limit", Responsibility.OWNER),
    "02": ("broken in service", Responsibility.OWNER),
    "03": ("damaged in handling", Responsibility.HANDLING_LINE),
    "04": ("damaged by lading", Responsibility.THIRD_PARTY),
    "05": ("missing", Responsibility.HANDLING_LINE),
    "06": ("loose or shifted", Responsibility.OWNER),
    "07": ("bent", Responsibility.HANDLING_LINE),
    "08": ("cracked", Responsibility.OWNER),
    "09": ("burned", Responsibility.THIRD_PARTY),
    "10": ("derailment damage", Responsibility.HANDLING_LINE),
    "11": ("applied to bring to standard", Responsibility.OWNER),
    "12": ("condemned by rule", Responsibility.OWNER),
    "13": ("vandalised", Responsibility.THIRD_PARTY),
    "14": ("wrong part applied earlier", Responsibility.JOINT),
}


def why_made(digits: str) -> WhyMade:
    return WhyMade(digits.strip())


def codes_answered_by(who: Responsibility) -> list[str]:
    """Every why made code that normally lands on one party."""
    return sorted(code for code, (_, answer) in WHY_MADE.items() if answer is who)


def responsibility_for(
    code: WhyMade, *, overridden: Responsibility | None = None
) -> Responsibility:
    """Who pays, allowing an inspector to say otherwise with a reason on file."""
    return overridden or code.usual_responsibility
