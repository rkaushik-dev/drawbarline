"""What kind of car it is.

Two identifiers ride together on every equipment record. The mechanical
designation is the short letter code a car has carried since long before
computers, one to four letters where the first says what the car is: X for a
box, T for a tank, L for a covered hopper. The car type code is four
characters, the kind letter again and then three digits that narrow it down to
the door width, the roof, the discharge gear.

They are entered by different people from different paperwork, so they
disagree more often than anyone would like. :func:`agrees` is the edit that
catches it before a rate is looked up on the wrong one.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from enum import Enum

from drawbarline.errors import DrawbarlineError

_DESIGNATION = re.compile(r"^[A-Z]{1,4}$")
_TYPE_CODE = re.compile(r"^([A-Z])(\d{3})$")


class EquipmentError(DrawbarlineError):
    """A car kind, designation or type code will not read."""

    code = "equipment"


class CarKind(Enum):
    """The letter that opens a mechanical designation."""

    BOX = "X"
    COVERED_HOPPER = "L"
    OPEN_HOPPER = "H"
    GONDOLA = "G"
    FLAT = "F"
    TANK = "T"
    REFRIGERATOR = "R"
    VEHICULAR = "V"
    STACK = "S"
    MAINTENANCE = "M"

    @property
    def letter(self) -> str:
        return self.value

    @property
    def description(self) -> str:
        return _DESCRIPTIONS[self]

    @property
    def carries_lading(self) -> bool:
        """Maintenance of way equipment moves, but nobody ships in it."""
        return self is not CarKind.MAINTENANCE

    @classmethod
    def from_letter(cls, letter: str) -> CarKind:
        try:
            return cls(letter)
        except ValueError:
            raise EquipmentError("no car kind carries that letter", letter=letter) from None


_DESCRIPTIONS = {
    CarKind.BOX: "box car",
    CarKind.COVERED_HOPPER: "covered hopper",
    CarKind.OPEN_HOPPER: "open top hopper",
    CarKind.GONDOLA: "gondola",
    CarKind.FLAT: "flat car",
    CarKind.TANK: "tank car",
    CarKind.REFRIGERATOR: "refrigerator car",
    CarKind.VEHICULAR: "vehicular flat",
    CarKind.STACK: "well car",
    CarKind.MAINTENANCE: "maintenance of way",
}


@dataclass(frozen=True, slots=True)
class MechanicalDesignation:
    """One to four letters, the first of which says what the car is."""

    letters: str

    def __post_init__(self) -> None:
        if not isinstance(self.letters, str) or not _DESIGNATION.match(self.letters):
            raise EquipmentError("a designation is one to four capitals", letters=self.letters)
        CarKind.from_letter(self.letters[0])

    @classmethod
    def parse(cls, text: str) -> MechanicalDesignation:
        return cls(text.strip().upper())

    @property
    def kind(self) -> CarKind:
        return CarKind.from_letter(self.letters[0])

    @property
    def qualifiers(self) -> str:
        """The letters after the kind, which say how the car is equipped."""
        return self.letters[1:]

    def __str__(self) -> str:
        return self.letters


@dataclass(frozen=True, slots=True)
class CarTypeCode:
    """A kind letter and three digits, as the equipment register carries it."""

    code: str

    def __post_init__(self) -> None:
        if not isinstance(self.code, str):
            raise EquipmentError("a type code is text", code=self.code)
        found = _TYPE_CODE.match(self.code)
        if found is None:
            raise EquipmentError("a type code is a letter and three digits", code=self.code)
        CarKind.from_letter(found.group(1))

    @classmethod
    def parse(cls, text: str) -> CarTypeCode:
        return cls(text.strip().upper())

    @property
    def kind(self) -> CarKind:
        return CarKind.from_letter(self.code[0])

    @property
    def detail(self) -> int:
        """The three digits, which narrow the kind down to a body style."""
        return int(self.code[1:])

    def __str__(self) -> str:
        return self.code


def agrees(designation: MechanicalDesignation, type_code: CarTypeCode) -> bool:
    """Whether the two identifiers on a record are talking about the same car."""
    return designation.kind is type_code.kind
