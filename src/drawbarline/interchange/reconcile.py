"""Comparing our interchange record against the other road's.

Both sides of a junction record the same handoffs and both sides bill off
their own record, so the month a connection disputes is the month the two
records differ. Finding the difference by eye across two hundred records is a
morning's work and it is the first thing anybody automates.

Three kinds of difference matter and they mean different things. A record only
we have is usually a car we took and they have not billed us for yet, which is
in our favour and worth telling them about anyway. A record only they have is
a car they think we took, which is the expensive one. A record both have at
different times is the common one and it is nearly always a clerk reading a
clock at the wrong end of a shift.

Matching is on the car, the kind and the station, and never on the time,
because the time is the thing being compared. Two records for the same car and
kind at the same place on the same day are the same handoff however far apart
the clocks are.
"""

from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass, field
from datetime import timedelta

from drawbarline.events.record import Event
from drawbarline.marks import CarMark

TOLERANCE = timedelta(minutes=30)


def key_of(event: Event) -> tuple[CarMark, str, str, str]:
    """What makes two records the same handoff, which is everything but the clock."""
    return (event.car, event.kind.name, event.station, f"{event.when:%Y-%m-%d}")


@dataclass(frozen=True, slots=True)
class Timing:
    """One handoff both sides recorded, at different times."""

    ours: Event
    theirs: Event

    @property
    def apart(self) -> timedelta:
        return abs(self.ours.when - self.theirs.when)

    @property
    def minutes_apart(self) -> int:
        return int(self.apart.total_seconds()) // 60

    @property
    def we_are_later(self) -> bool:
        return self.ours.when > self.theirs.when

    def __str__(self) -> str:
        way = "later" if self.we_are_later else "earlier"
        return f"{self.ours.car} {self.ours.kind} we are {self.minutes_apart} minutes {way}"


@dataclass(slots=True)
class Reconciliation:
    """What the two records agree on and what they do not."""

    agreed: list[Event] = field(default_factory=list)
    only_ours: list[Event] = field(default_factory=list)
    only_theirs: list[Event] = field(default_factory=list)
    timings: list[Timing] = field(default_factory=list)

    @property
    def clean(self) -> bool:
        return not self.only_ours and not self.only_theirs and not self.timings

    @property
    def differences(self) -> int:
        return len(self.only_ours) + len(self.only_theirs) + len(self.timings)

    def worst_timing(self) -> Timing | None:
        if not self.timings:
            return None
        return max(self.timings, key=lambda one: one.apart)

    def __str__(self) -> str:
        if self.clean:
            return f"{len(self.agreed)} records, both sides agree"
        return (
            f"{len(self.agreed)} agreed, {len(self.only_ours)} only ours, "
            f"{len(self.only_theirs)} only theirs, {len(self.timings)} out of time"
        )


def compare(
    ours: list[Event], theirs: list[Event], *, tolerance: timedelta = TOLERANCE
) -> Reconciliation:
    """Set two records of the same junction and month against each other."""
    if tolerance < timedelta(0):
        raise ValueError("a tolerance does not run backwards")

    mine: dict[tuple, list[Event]] = defaultdict(list)
    for event in ours:
        mine[key_of(event)].append(event)

    found = Reconciliation()
    for event in sorted(theirs, key=lambda one: one.order):
        key = key_of(event)
        if not mine[key]:
            found.only_theirs.append(event)
            continue
        match = mine[key].pop(0)
        if abs(match.when - event.when) > tolerance:
            found.timings.append(Timing(match, event))
        else:
            found.agreed.append(match)

    left = [one for events in mine.values() for one in events]
    found.only_ours = sorted(left, key=lambda one: one.order)
    return found
