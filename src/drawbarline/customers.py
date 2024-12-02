"""Who ships, where their track is, and how often we get to it.

An industry is a piece of private track off ours, with a name, a spot capacity
and a switching schedule. All three matter to demurrage. The spot capacity is
how many cars physically fit, and it is what turns an actual placement into a
constructive one when the twelfth car arrives at an eleven car track. The
schedule is which days we switch the industry at all, and a car cannot be
pulled on a day nobody goes there.

A shipper may be on an average agreement, which changes demurrage from a bill
per car into a monthly balance of credits and debits. Whether they are is a
property of the shipper and not of the car, so it lives here.
"""

from __future__ import annotations

from collections.abc import Iterator
from dataclasses import dataclass, field
from datetime import date

from drawbarline.errors import ConfigurationError, DrawbarlineError

WEEKDAYS = ("mon", "tue", "wed", "thu", "fri", "sat", "sun")


class CustomerError(DrawbarlineError):
    """A shipper or an industry is not on the register."""

    code = "customer"


@dataclass(frozen=True, slots=True)
class Shipper:
    """A party we bill demurrage to."""

    code: str
    name: str
    average_agreement: bool = False

    def __post_init__(self) -> None:
        if not self.code.isupper() or not self.code.isalnum():
            raise ConfigurationError("a shipper code is upper case", code=self.code)
        if not self.name.strip():
            raise ConfigurationError("a shipper needs a name", code=self.code)

    def __str__(self) -> str:
        return f"{self.code} {self.name}"


@dataclass(frozen=True, slots=True)
class Industry:
    """A private track we place cars on."""

    code: str
    name: str
    station: str
    shipper: Shipper
    spots: int
    switched: frozenset[str] = field(default_factory=lambda: frozenset(WEEKDAYS[:5]))

    def __post_init__(self) -> None:
        if not self.code.isupper() or not self.code.isalnum():
            raise ConfigurationError("an industry code is upper case", code=self.code)
        if self.spots < 1:
            raise ConfigurationError("an industry track holds at least one car", code=self.code)
        unknown = set(self.switched) - set(WEEKDAYS)
        if unknown:
            raise ConfigurationError("no such day", code=self.code, days=sorted(unknown))
        if not self.switched:
            raise ConfigurationError("an industry we never switch is closed", code=self.code)

    def switched_on(self, day: date) -> bool:
        return WEEKDAYS[day.weekday()] in self.switched

    def full(self, cars_on_hand: int) -> bool:
        """Whether another car has nowhere to go, which makes the next one constructive."""
        return cars_on_hand >= self.spots

    def __str__(self) -> str:
        return f"{self.code} {self.name} at {self.station}"


class CustomerRegister:
    """Every shipper and every piece of industry track on the line."""

    def __init__(self) -> None:
        self._shippers: dict[str, Shipper] = {}
        self._industries: dict[str, Industry] = {}

    def add_shipper(self, shipper: Shipper) -> Shipper:
        if shipper.code in self._shippers:
            raise CustomerError("that shipper is already on the register", code=shipper.code)
        self._shippers[shipper.code] = shipper
        return shipper

    def add_industry(self, industry: Industry) -> Industry:
        if industry.code in self._industries:
            raise CustomerError("that industry is already on the register", code=industry.code)
        if industry.shipper.code not in self._shippers:
            raise CustomerError(
                "the shipper has to be on the register first",
                industry=industry.code,
                shipper=industry.shipper.code,
            )
        self._industries[industry.code] = industry
        return industry

    def shipper(self, code: str) -> Shipper:
        try:
            return self._shippers[code]
        except KeyError:
            raise CustomerError("no such shipper", code=code) from None

    def industry(self, code: str) -> Industry:
        try:
            return self._industries[code]
        except KeyError:
            raise CustomerError("no such industry", code=code) from None

    def at_station(self, station: str) -> list[Industry]:
        return [track for track in self if track.station == station]

    def for_shipper(self, code: str) -> list[Industry]:
        self.shipper(code)
        return [track for track in self if track.shipper.code == code]

    def on_average_agreement(self) -> list[Shipper]:
        return sorted(
            (s for s in self._shippers.values() if s.average_agreement),
            key=lambda s: s.code,
        )

    def __iter__(self) -> Iterator[Industry]:
        for code in sorted(self._industries):
            yield self._industries[code]

    def __len__(self) -> int:
        return len(self._industries)
