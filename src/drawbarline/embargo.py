"""Embargoes, which are how a railroad says stop.

When a bridge is out at Hermansville, or a paper mill has forty cars on the
ground and no room for the forty first, the road issues an embargo. It names
what it stops: a station, a commodity family, a connection, or some
combination, and it runs from a date to a date. Cars caught by one do not
move, and the hours they stand are reclaimable, which is why the register is
here and not in a note on somebody's desk.

An embargo that restricts nothing is refused. That sounds obvious and it is
the single most common thing anybody files by accident, because the form has
four optional fields and leaving all four empty embargoes the entire railroad.

A permit is the way around one. An embargo may allow permits or not, and a car
moving under a permit number against an embargo that does not allow them is a
car somebody has to answer for.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from drawbarline.errors import DrawbarlineError
from drawbarline.lading import Commodity
from drawbarline.marks import ReportingMark
from drawbarline.mileage import milepost


class EmbargoError(DrawbarlineError):
    """An embargo cannot be filed or cannot be lifted."""

    code = "embargo"


@dataclass(frozen=True, slots=True)
class Embargo:
    """One restriction, from a date to a date."""

    number: int
    effective: date
    reason: str
    station: str | None = None
    commodity_family: str | None = None
    road: ReportingMark | None = None
    expires: date | None = None
    permits_allowed: bool = False

    def __post_init__(self) -> None:
        if self.number < 1:
            raise EmbargoError("an embargo is numbered from one", number=self.number)
        if not self.reason.strip():
            raise EmbargoError("an embargo carries a reason", number=self.number)
        if self.station is not None:
            milepost(self.station)
        if self.commodity_family is not None and not self.commodity_family.isdigit():
            raise EmbargoError(
                "a commodity family is digits", number=self.number, family=self.commodity_family
            )
        if self.station is None and self.commodity_family is None and self.road is None:
            raise EmbargoError(
                "an embargo that restricts nothing stops the whole railroad",
                number=self.number,
            )
        if self.expires is not None and self.expires < self.effective:
            raise EmbargoError(
                "an embargo cannot expire before it takes effect", number=self.number
            )

    def in_force_on(self, day: date) -> bool:
        if day < self.effective:
            return False
        return self.expires is None or day <= self.expires

    def catches(
        self,
        *,
        station: str | None = None,
        commodity: Commodity | None = None,
        road: ReportingMark | None = None,
    ) -> bool:
        """Whether a movement runs into this embargo. Every stated field has to match."""
        if self.station is not None and self.station != station:
            return False
        if self.commodity_family is not None and (
            commodity is None or not commodity.code.within(self.commodity_family)
        ):
            return False
        return not (self.road is not None and self.road != road)

    def __str__(self) -> str:
        parts = [f"embargo {self.number}"]
        if self.station:
            parts.append(f"at {self.station}")
        if self.commodity_family:
            parts.append(f"on {self.commodity_family}")
        if self.road:
            parts.append(f"with {self.road}")
        return " ".join(parts)


class EmbargoRegister:
    """Every embargo the road has issued, live or lifted."""

    def __init__(self) -> None:
        self._embargoes: dict[int, Embargo] = {}

    def issue(self, embargo: Embargo) -> Embargo:
        if embargo.number in self._embargoes:
            raise EmbargoError("that embargo is already issued", number=embargo.number)
        self._embargoes[embargo.number] = embargo
        return embargo

    def lift(self, number: int, on: date) -> Embargo:
        """Close an open embargo early, which is what lifting one is."""
        standing = self._embargoes.get(number)
        if standing is None:
            raise EmbargoError("no such embargo", number=number)
        if standing.expires is not None:
            raise EmbargoError("that embargo already has an end", number=number)
        if on < standing.effective:
            raise EmbargoError("an embargo cannot be lifted before it took effect", number=number)
        lifted = Embargo(
            number=standing.number,
            effective=standing.effective,
            reason=standing.reason,
            station=standing.station,
            commodity_family=standing.commodity_family,
            road=standing.road,
            expires=on,
            permits_allowed=standing.permits_allowed,
        )
        self._embargoes[number] = lifted
        return lifted

    def get(self, number: int) -> Embargo:
        standing = self._embargoes.get(number)
        if standing is None:
            raise EmbargoError("no such embargo", number=number)
        return standing

    def in_force_on(self, day: date) -> list[Embargo]:
        return sorted(
            (one for one in self._embargoes.values() if one.in_force_on(day)),
            key=lambda one: one.number,
        )

    def against(
        self,
        day: date,
        *,
        station: str | None = None,
        commodity: Commodity | None = None,
        road: ReportingMark | None = None,
    ) -> list[Embargo]:
        """Every live embargo a particular movement runs into."""
        return [
            one
            for one in self.in_force_on(day)
            if one.catches(station=station, commodity=commodity, road=road)
        ]

    def __len__(self) -> int:
        return len(self._embargoes)
