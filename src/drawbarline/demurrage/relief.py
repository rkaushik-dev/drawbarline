"""Relief a customer is owed when the bunching was our fault.

A shipper orders four cars a day. We hold them at Powers for a week because we
are short of power, then set out twenty eight on a Tuesday. Every one of those
cars starts its clock the same morning, the shipper can only work four a day,
and by Friday they are paying demurrage on cars they could not physically have
touched. That is bunching, and the relief for it is old and settled: count
each car as though it had arrived on the day it would have arrived if we had
delivered evenly.

The relief is per track and per day, because a customer with two tracks works
them independently, and it is ordered by car mark so that two runs of the same
month relieve the same cars. Which cars get the relief matters to nobody, but
picking a different set every run makes the bill unreproducible.

Constructive placement is the other half. A car we could not place because the
track was full is still on the customer's time, but only if the track really
was full. If it was not, we simply failed to deliver, and the clock has no
business running.
"""

from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass, replace

from drawbarline.customers import Industry
from drawbarline.days import add_chargeable_days, chargeable_days, next_chargeable
from drawbarline.demurrage.counting import Detention, free_time_ends
from drawbarline.demurrage.tariff import Tariff
from drawbarline.errors import TariffError
from drawbarline.marks import CarMark


@dataclass(frozen=True, slots=True)
class Relief:
    """How many days a car was pushed back, and why."""

    car: CarMark
    industry: str
    days: int
    reason: str = "bunching"

    def __str__(self) -> str:
        return f"{self.car} {self.days} days relief for {self.reason}"


def recount(detention: Detention, tariff: Tariff, days_later: int) -> Detention:
    """Recount a detention as though the clock had started later."""
    if days_later < 0:
        raise TariffError("relief does not run backwards", days=days_later)
    if days_later == 0:
        return detention
    clock = add_chargeable_days(detention.clock_starts, days_later)
    free_until = free_time_ends(clock, tariff.free_days(loaded=detention.loaded))
    first_charged = clock if free_until is None else next_chargeable(free_until)
    charged = (
        0
        if detention.counted_to < first_charged
        else chargeable_days(first_charged, detention.counted_to)
    )
    return replace(detention, clock_starts=clock, free_until=free_until, chargeable=charged)


def bunching(detentions: list[Detention], per_day: int) -> list[Relief]:
    """How far back each car has to be pushed for the delivery to read as even."""
    if per_day < 1:
        raise TariffError("a customer works at least one car a day", per_day=per_day)
    grouped: dict[tuple[str, object], list[Detention]] = defaultdict(list)
    for one in detentions:
        grouped[(one.industry, one.placed_on)].append(one)

    found: list[Relief] = []
    for (industry, _), group in grouped.items():
        for index, one in enumerate(sorted(group, key=lambda d: str(d.car))):
            days = index // per_day
            if days:
                found.append(Relief(one.car, industry, days))
    return sorted(found, key=lambda one: (one.industry, str(one.car)))


def relieve(detentions: list[Detention], tariff: Tariff, per_day: int) -> list[Detention]:
    """Recount a set of detentions with bunching relief applied."""
    allowed = {one.car: one.days for one in bunching(detentions, per_day)}
    return [recount(one, tariff, allowed.get(one.car, 0)) for one in detentions]


def constructive_stands(industry: Industry, cars_on_hand: int) -> bool:
    """Whether a constructive placement was really the customer holding us out."""
    return industry.full(cars_on_hand)


def check_constructive(detention: Detention, industry: Industry, cars_on_hand: int) -> Detention:
    """Refuse a constructive placement onto a track that had room."""
    if not detention.constructive:
        return detention
    if not constructive_stands(industry, cars_on_hand):
        raise TariffError(
            "the track had room, so nothing was constructively placed",
            car=str(detention.car),
            industry=industry.code,
            on_hand=cars_on_hand,
        )
    return detention
