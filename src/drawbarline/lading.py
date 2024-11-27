"""What is in the car.

Freight is named by a standard transportation commodity code, seven digits
that narrow from a two digit major group down to the particular product. The
first two digits are what a tariff usually keys on and the last five are what
a claim argues about. Hazardous material carries its own major group, which is
why a code beginning 49 changes how the car is handled, placarded and placed.

A lading is a commodity and a weight. An empty car has no lading at all rather
than a lading of nothing, because the two are treated differently everywhere:
an empty moves under a different rate, a different routing rule and a
different demurrage clock.
"""

from __future__ import annotations

import re
from dataclasses import dataclass

from drawbarline.errors import DrawbarlineError
from drawbarline.weight import Weight

_STCC = re.compile(r"^\d{7}$")

HAZARDOUS_GROUP = "49"


class LadingError(DrawbarlineError):
    """A commodity code or a lading will not read."""

    code = "lading"


@dataclass(frozen=True, slots=True)
class Stcc:
    """A seven digit standard transportation commodity code."""

    digits: str

    def __post_init__(self) -> None:
        if not isinstance(self.digits, str) or not _STCC.match(self.digits):
            raise LadingError("a commodity code is seven digits", digits=self.digits)

    @classmethod
    def parse(cls, text: str) -> Stcc:
        """Read a code with or without the dashes a tariff prints it with."""
        return cls(text.strip().replace("-", "").replace(" ", ""))

    @property
    def major_group(self) -> str:
        return self.digits[:2]

    @property
    def hazardous(self) -> bool:
        return self.major_group == HAZARDOUS_GROUP

    def at_level(self, level: int) -> str:
        """The first ``level`` digits, which is how a tariff matches a family."""
        if not 1 <= level <= 7:
            raise LadingError("a commodity code has seven levels", level=level)
        return self.digits[:level]

    def within(self, family: str) -> bool:
        """Whether this code sits under a shorter code."""
        if not family.isdigit() or not 1 <= len(family) <= 7:
            raise LadingError("a family is one to seven digits", family=family)
        return self.digits.startswith(family)

    def __str__(self) -> str:
        return f"{self.digits[:2]} {self.digits[2:5]} {self.digits[5:]}"


@dataclass(frozen=True, slots=True)
class Commodity:
    """A named product, as the tariff file carries it."""

    code: Stcc
    name: str
    bulk: bool = False

    def __post_init__(self) -> None:
        if not self.name.strip():
            raise LadingError("a commodity needs a name", code=str(self.code))

    @property
    def hazardous(self) -> bool:
        return self.code.hazardous

    def __str__(self) -> str:
        return f"{self.code} {self.name}"


@dataclass(frozen=True, slots=True)
class Lading:
    """A commodity and how much of it is in the car."""

    commodity: Commodity
    weight: Weight

    def __post_init__(self) -> None:
        if self.weight.pounds == 0:
            raise LadingError(
                "a loaded car weighs something, an empty one has no lading at all",
                commodity=str(self.commodity.code),
            )

    @property
    def hazardous(self) -> bool:
        return self.commodity.hazardous

    def __str__(self) -> str:
        return f"{self.commodity.name} {self.weight}"


WHEAT = Commodity(Stcc("0113710"), "wheat", bulk=True)
PULPWOOD = Commodity(Stcc("2411110"), "pulpwood", bulk=True)
KRAFT_PAPER = Commodity(Stcc("2621315"), "kraft paper")
IRON_ORE_PELLETS = Commodity(Stcc("1011710"), "iron ore pellets", bulk=True)
SODIUM_HYDROXIDE = Commodity(Stcc("4930040"), "sodium hydroxide solution")
LUMBER = Commodity(Stcc("2421110"), "rough sawn lumber")
