"""Reporting marks and car numbers.

A freight car is named by two things stencilled on its side: a reporting mark
of two to four letters that says who is answerable for it, and a number of up
to six digits. Together they are unique across North America, which is what
lets three railroads bill each other about the same piece of steel.

A mark ending in X belongs to somebody who is not a railroad. That single
letter decides whether car hire is owed to a road or rent is owed to a lessor,
so it is worth its own predicate rather than a string test at each call site.

Numbers are often stencilled with a check digit after a dash. It is a modulus
ten digit over the six digit number, doubling from the right, and it exists to
catch a clerk transposing two digits off a waybill. We accept a number with or
without it and refuse one where it disagrees.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from functools import total_ordering

from drawbarline.errors import MarkError

_MARK = re.compile(r"^[A-Z]{2,4}$")
_SPLIT = re.compile(r"^([A-Z]{2,4})[\s.]*([0-9]{1,6})(?:-([0-9]))?$")

MAX_CAR_NUMBER = 999999


def check_digit(number: int) -> int:
    """The modulus ten check digit for a car number.

    Digits are taken from the right, every second one doubled, the digits of
    each product summed, and the total completed to the next multiple of ten.
    """
    if not 0 <= number <= MAX_CAR_NUMBER:
        raise MarkError("car number is out of range", number=number)
    total = 0
    for position, digit in enumerate(reversed(f"{number:06d}")):
        value = int(digit)
        if position % 2 == 0:
            value *= 2
            if value > 9:
                value -= 9
        total += value
    return (10 - total % 10) % 10


@total_ordering
@dataclass(frozen=True, slots=True)
class ReportingMark:
    """Two to four letters saying who answers for a car."""

    letters: str

    def __post_init__(self) -> None:
        if not isinstance(self.letters, str):
            raise MarkError("a reporting mark is letters", letters=self.letters)
        if not _MARK.match(self.letters):
            raise MarkError("a reporting mark is two to four capitals", letters=self.letters)

    @classmethod
    def parse(cls, text: str) -> ReportingMark:
        return cls(text.strip().upper())

    @property
    def is_private(self) -> bool:
        """True when the owner is not a railroad, which the trailing X says."""
        return self.letters.endswith("X")

    @property
    def road(self) -> str:
        """The mark with any private suffix taken off."""
        return self.letters[:-1] if self.is_private else self.letters

    def __str__(self) -> str:
        return self.letters

    def __lt__(self, other: object) -> bool:
        if not isinstance(other, ReportingMark):
            return NotImplemented
        return self.letters < other.letters


@total_ordering
@dataclass(frozen=True, slots=True)
class CarMark:
    """A mark and a number, which together name one car anywhere on the continent."""

    mark: ReportingMark
    number: int

    def __post_init__(self) -> None:
        if not isinstance(self.number, int) or isinstance(self.number, bool):
            raise MarkError("a car number is a whole number", number=self.number)
        if not 1 <= self.number <= MAX_CAR_NUMBER:
            raise MarkError("a car number is one to six digits", number=self.number)

    @classmethod
    def parse(cls, text: str) -> CarMark:
        """Read ``DBLX 104207``, ``DBLX104207`` or ``DBLX 104207-3``."""
        cleaned = text.strip().upper()
        found = _SPLIT.match(cleaned)
        if found is None:
            raise MarkError("cannot read that as a car", text=text)
        letters, digits, stencilled = found.groups()
        number = int(digits)
        mark = cls(ReportingMark(letters), number)
        if stencilled is not None and int(stencilled) != mark.check_digit:
            raise MarkError(
                "the stencilled check digit does not match the number",
                text=text,
                expected=mark.check_digit,
            )
        return mark

    @property
    def check_digit(self) -> int:
        return check_digit(self.number)

    @property
    def is_private(self) -> bool:
        return self.mark.is_private

    def stencilled(self) -> str:
        """How the car reads on its side, check digit and all."""
        return f"{self.mark} {self.number:06d}-{self.check_digit}"

    def __str__(self) -> str:
        return f"{self.mark} {self.number:06d}"

    def __lt__(self, other: object) -> bool:
        if not isinstance(other, CarMark):
            return NotImplemented
        return (self.mark, self.number) < (other.mark, other.number)
