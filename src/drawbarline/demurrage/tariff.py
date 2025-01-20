"""The demurrage tariff.

A tariff says three things. How many chargeable days a customer gets free, how
much each day after that costs, and that the price goes up the longer they
keep it. The last part is the point of the whole exercise. Demurrage is not
rent, it is a lever, and a flat rate is a lever nobody feels.

Free time is quoted in chargeable days, so a car placed on a Friday afternoon
with two days free is not due back until Tuesday. That counting lives in the
calendar and not here, because the same calendar governs the invoice date and
the average agreement settle.

Loads and empties get different free time. An empty is on the customer track
because we put it there for them to load, and the clock the industry needs to
turn a car is not the clock a consignee needs to unload one.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from drawbarline.errors import TariffError
from drawbarline.money import Money


@dataclass(frozen=True, slots=True)
class Step:
    """A price that runs through a numbered chargeable day."""

    through: int
    cents: int

    def __post_init__(self) -> None:
        if self.through < 0:
            raise TariffError("a step runs through a day or through the end", through=self.through)
        if self.cents < 0:
            raise TariffError("a step does not pay the customer", cents=self.cents)

    @property
    def open_ended(self) -> bool:
        """The last step, which runs for as long as the car is held."""
        return self.through == 0

    def __str__(self) -> str:
        where = "and thereafter" if self.open_ended else f"through day {self.through}"
        return f"{Money(self.cents)} {where}"


@dataclass(frozen=True, slots=True)
class Tariff:
    """One published schedule, in force from a date."""

    code: str
    effective: date
    free_days_loaded: int
    free_days_empty: int
    steps: tuple[Step, ...]

    def __post_init__(self) -> None:
        if not self.code.strip():
            raise TariffError("a tariff is published under a number")
        if self.free_days_loaded < 0 or self.free_days_empty < 0:
            raise TariffError("free time is not negative", code=self.code)
        if not self.steps:
            raise TariffError("a tariff prices at least one day", code=self.code)
        closed = [step for step in self.steps[:-1] if step.open_ended]
        if closed:
            raise TariffError("only the last step runs to the end", code=self.code)
        if not self.steps[-1].open_ended:
            raise TariffError("the last step runs to the end", code=self.code)
        bounds = [step.through for step in self.steps[:-1]]
        if bounds != sorted(set(bounds)):
            raise TariffError("the steps climb in order", code=self.code)

    def free_days(self, *, loaded: bool) -> int:
        return self.free_days_loaded if loaded else self.free_days_empty

    def rate_on_day(self, day_number: int) -> Money:
        """What the nth chargeable day past free time costs."""
        if day_number < 1:
            raise TariffError("chargeable days are numbered from one", day=day_number)
        for step in self.steps:
            if step.open_ended or day_number <= step.through:
                return Money(step.cents)
        raise TariffError("the schedule ran out", code=self.code, day=day_number)

    def charge(self, chargeable_days: int) -> Money:
        """What a number of chargeable days past free time comes to."""
        if chargeable_days < 0:
            raise TariffError("a car cannot be held for a negative day", days=chargeable_days)
        running = 0
        for day_number in range(1, chargeable_days + 1):
            running += self.rate_on_day(day_number).cents
        return Money(running)

    def __str__(self) -> str:
        return f"tariff {self.code} from {self.effective}"


class TariffBook:
    """Every tariff the road has published, newest last."""

    def __init__(self) -> None:
        self._tariffs: list[Tariff] = []

    def publish(self, tariff: Tariff) -> Tariff:
        if any(one.effective == tariff.effective for one in self._tariffs):
            raise TariffError(
                "a tariff already takes effect that day", effective=str(tariff.effective)
            )
        self._tariffs.append(tariff)
        self._tariffs.sort(key=lambda one: one.effective)
        return tariff

    def in_effect_on(self, day: date) -> Tariff:
        standing = [one for one in self._tariffs if one.effective <= day]
        if not standing:
            raise TariffError("no tariff was in effect", on=str(day))
        return standing[-1]

    def effective_dates(self) -> list[date]:
        return [one.effective for one in self._tariffs]

    def __len__(self) -> int:
        return len(self._tariffs)
