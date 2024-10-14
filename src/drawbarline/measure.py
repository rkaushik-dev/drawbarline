"""Lengths, kept in whole inches.

Car dimensions come off a stencil and out of the equipment register in feet and
inches, never in decimal feet. A car is 89 feet 4 inches over the pulling
faces, and the inch matters because clearance is decided on it. Holding the
value as an int of inches means no length ever has to be compared with a
tolerance, and it means feet and inches only exist at the edges where somebody
reads or writes one.

Nothing here is negative. A negative length in this domain is always a
subtraction done in the wrong order, and it is cheaper to find that at the
subtraction than three screens later.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from functools import total_ordering

from drawbarline.errors import DrawbarlineError

INCHES_PER_FOOT = 12

_FORMS = (
    re.compile(r"^(?P<feet>\d+)\s*'\s*(?:(?P<inches>\d{1,2})\s*\")?$"),
    re.compile(r"^(?P<feet>\d+)\s*ft\.?\s*(?:(?P<inches>\d{1,2})\s*in\.?)?$", re.I),
    re.compile(r"^(?P<feet>\d+)\s*-\s*(?P<inches>\d{1,2})$"),
    re.compile(r"^(?P<inches>\d+)\s*\"$"),
)


class MeasureError(DrawbarlineError):
    """A length cannot be read, or cannot exist."""

    code = "measure"


@total_ordering
@dataclass(frozen=True, slots=True)
class Length:
    """A length in whole inches."""

    inches: int

    def __post_init__(self) -> None:
        if not isinstance(self.inches, int) or isinstance(self.inches, bool):
            raise MeasureError("a length is a whole number of inches", inches=self.inches)
        if self.inches < 0:
            raise MeasureError("a length is not negative", inches=self.inches)

    @classmethod
    def of(cls, feet: int, inches: int = 0) -> Length:
        """Build from feet and inches. The inches part may exceed eleven."""
        if not isinstance(feet, int) or not isinstance(inches, int):
            raise MeasureError("feet and inches are whole numbers", feet=feet, inches=inches)
        return cls(feet * INCHES_PER_FOOT + inches)

    @classmethod
    def parse(cls, text: str) -> Length:
        """Read the forms a register and a stencil actually use."""
        cleaned = text.strip()
        for form in _FORMS:
            found = form.match(cleaned)
            if found is None:
                continue
            parts = found.groupdict()
            feet = int(parts.get("feet") or 0)
            inches = int(parts.get("inches") or 0)
            if feet and inches >= INCHES_PER_FOOT:
                raise MeasureError(
                    "a written length carries under twelve inches", text=text, inches=inches
                )
            return cls.of(feet, inches)
        raise MeasureError("cannot read that as a length", text=text)

    @property
    def feet(self) -> int:
        """Whole feet, discarding the inches."""
        return self.inches // INCHES_PER_FOOT

    @property
    def inches_part(self) -> int:
        """The inches left over after the whole feet."""
        return self.inches % INCHES_PER_FOOT

    def in_feet(self) -> float:
        """Decimal feet, for a report that wants one. Never for arithmetic."""
        return self.inches / INCHES_PER_FOOT

    def __str__(self) -> str:
        if self.inches_part == 0:
            return f"{self.feet}'"
        return f"{self.feet}'{self.inches_part}\""

    def __add__(self, other: Length) -> Length:
        if not isinstance(other, Length):
            return NotImplemented
        return Length(self.inches + other.inches)

    def __sub__(self, other: Length) -> Length:
        if not isinstance(other, Length):
            return NotImplemented
        if other.inches > self.inches:
            raise MeasureError("that subtraction runs past zero", left=str(self), right=str(other))
        return Length(self.inches - other.inches)

    def __mul__(self, count: int) -> Length:
        if not isinstance(count, int) or isinstance(count, bool):
            return NotImplemented
        if count < 0:
            raise MeasureError("a length does not scale by a negative", count=count)
        return Length(self.inches * count)

    __rmul__ = __mul__

    def __lt__(self, other: object) -> bool:
        if not isinstance(other, Length):
            return NotImplemented
        return self.inches < other.inches


ZERO = Length(0)


def longest(lengths: list[Length]) -> Length:
    """The longest of several, refusing an empty list rather than guessing zero."""
    if not lengths:
        raise MeasureError("no lengths to compare")
    return max(lengths)


def total(lengths: list[Length]) -> Length:
    """The sum, which is zero over nothing because a train of no cars is short."""
    running = ZERO
    for one in lengths:
        running = running + one
    return running
