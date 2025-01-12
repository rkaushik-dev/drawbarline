"""Hours we are not going to pay for.

Car hire runs from the moment we take a car until the moment we hand it back,
and most of that time we deserve to pay for. Some of it we do not. If the car
arrived with a broken wheel and sat four days in our rip track waiting on a
part, those four days are the owner's problem and we reclaim them. If a
government embargo shut the line and the car could not move, the same.

The walk that finds a hold is the same walk that finds a car cycle, so it is
the one in the events package rather than a second copy here.

The rules are numbered and the numbers travel on the statement, because a
connection disputing a reclaim disputes it by rule. So the reason is an
enumeration carrying its rule number, not a free text note somebody typed.

A reclaim is priced exactly the way the charge it offsets is priced, by
running the same accrual over the reclaimed span. Pricing it any other way
means the two never quite cancel and the difference shows up as a penny a car
on the statement.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from enum import Enum

from drawbarline.carhire.accrual import hourly
from drawbarline.carhire.rates import RateTable
from drawbarline.clock import Interval
from drawbarline.events.kinds import EventKind
from drawbarline.events.spans import scan_pairs
from drawbarline.events.stream import CarStream
from drawbarline.fleet.car import Car
from drawbarline.marks import CarMark
from drawbarline.money import Money, total


class ReclaimReason(Enum):
    """Why the hours come back, by the rule they come back under."""

    REPAIRS = 5
    SHIPPER_DELAY = 15
    EMBARGO = 24

    @property
    def rule(self) -> str:
        return f"rule {self.value}"

    @property
    def text(self) -> str:
        return _REASON_TEXT[self]


_REASON_TEXT = {
    ReclaimReason.REPAIRS: "held for repairs the owner answers for",
    ReclaimReason.SHIPPER_DELAY: "held under load past the reclaim threshold",
    ReclaimReason.EMBARGO: "held under embargo",
}


@dataclass(frozen=True, slots=True)
class Reclaim:
    """A stretch of hours taken back off a charge."""

    car: CarMark
    reason: ReclaimReason
    interval: Interval
    amount: Money

    @property
    def hundredth_hours(self) -> int:
        return self.interval.hundredth_hours

    def __str__(self) -> str:
        return f"{self.car} {self.reason.rule} {self.amount}"


def paired_spans(
    stream: CarStream, opens: EventKind, closes: EventKind, until: datetime, what: str
) -> list[Interval]:
    """The intervals between one kind of event and another, cut at the month end."""
    pairs = scan_pairs(stream, frozenset({opens}), frozenset({closes}), until, what=what)
    return [pair.interval for pair in pairs]


def repair_holds(stream: CarStream, until: datetime) -> list[Interval]:
    return paired_spans(stream, EventKind.BAD_ORDERED, EventKind.REPAIRED, until, "the bad order")


def embargo_holds(stream: CarStream, until: datetime) -> list[Interval]:
    return paired_spans(stream, EventKind.HELD, EventKind.FREED, until, "the hold")


def reclaims_for(
    car: Car,
    stream: CarStream,
    table: RateTable,
    window: Interval,
    *,
    owner_repairs: bool = True,
) -> list[Reclaim]:
    """Every reclaim the stream supports inside a window."""
    found: list[Reclaim] = []
    if owner_repairs:
        for span in repair_holds(stream, window.end):
            found.append(_priced(car, table, window, span, ReclaimReason.REPAIRS))
    for span in embargo_holds(stream, window.end):
        found.append(_priced(car, table, window, span, ReclaimReason.EMBARGO))
    return [one for one in found if not one.interval.empty]


def _priced(
    car: Car,
    table: RateTable,
    window: Interval,
    span: Interval,
    reason: ReclaimReason,
) -> Reclaim:
    cut = span.clip(window)
    return Reclaim(car.mark, reason, cut, hourly(car, table, cut))


def net_of_reclaims(charge_amount: Money, reclaims: list[Reclaim]) -> Money:
    """A charge with its reclaims taken off, which never goes below nothing."""
    taken = total(one.amount for one in reclaims)
    if taken > charge_amount:
        return Money(0)
    return charge_amount - taken
