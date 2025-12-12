"""The morning sheet.

One page, read standing up, before anybody has sat down. It answers the four
questions the superintendent asks every day in the same order: how many cars
are we holding, what moved yesterday, what is stopped, and what did we fail to
do for a customer.

Nothing on it is new. Every figure is one another report already produces, and
that is the point: the morning sheet and the monthly report have to agree, and
the way to guarantee that is for one to be built out of the same functions as
the other.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, timedelta

from drawbarline.clock import Interval
from drawbarline.distribution import Distribution
from drawbarline.embargo import EmbargoRegister
from drawbarline.events.kinds import EventKind
from drawbarline.events.stream import EventLog
from drawbarline.reports.interchange import on_hand
from drawbarline.reports.render import Align, Column, Table, heading
from drawbarline.reports.shop import down_now, shop_spans

HOURS_A_DAY = 24


@dataclass(frozen=True, slots=True)
class DayCounts:
    """What happened in one day, by the kind of thing it was."""

    received: int
    delivered: int
    placed: int
    pulled: int
    bad_ordered: int

    @property
    def worked(self) -> int:
        return self.placed + self.pulled

    @property
    def net_cars(self) -> int:
        """Positive when the railroad grew, which is more cars to pay for."""
        return self.received - self.delivered


def window_of(day: date, at: datetime) -> Interval:
    """The twenty four hours ending at a moment on a day."""
    end = datetime.combine(day, at.timetz())
    return Interval(end - timedelta(hours=HOURS_A_DAY), end)


def counts_in(log: EventLog, window: Interval) -> DayCounts:
    """How many of each kind happened in a window."""
    seen = {kind: 0 for kind in EventKind}
    for event in log.during(window):
        seen[event.kind] += 1
    return DayCounts(
        received=seen[EventKind.RECEIVED],
        delivered=seen[EventKind.DELIVERED],
        placed=seen[EventKind.PLACED] + seen[EventKind.CONSTRUCTIVE],
        pulled=seen[EventKind.PULLED],
        bad_ordered=seen[EventKind.BAD_ORDERED],
    )


def daily_summary(
    log: EventLog,
    embargoes: EmbargoRegister,
    distribution: Distribution,
    day: date,
    at: datetime,
) -> list[str]:
    """The morning sheet for one day."""
    window = window_of(day, at)
    counts = counts_in(log, window)
    standing = on_hand(log, at)
    down = down_now(shop_spans(log, at))
    live = embargoes.in_force_on(day)

    movement = Table([Column("what"), Column("cars", Align.RIGHT)])
    movement.add("received in interchange", str(counts.received))
    movement.add("delivered in interchange", str(counts.delivered))
    movement.add("placed at industries", str(counts.placed))
    movement.add("pulled off industries", str(counts.pulled))
    movement.add("set out bad order", str(counts.bad_ordered))
    movement.total("net change on line", str(counts.net_cars))

    lines = [*heading("morning sheet", str(day)), *movement.lines(), ""]
    lines.append(f"cars on hand          {len(standing)}")
    lines.append(f"cars bad ordered      {len(down)}")
    lines.append(f"embargoes in force    {len(live)}")
    lines.append(f"car orders filled     {distribution.filled}")
    lines.append(f"car orders short      {distribution.unfilled}")

    if down:
        lines.append("")
        lines.append("bad order")
        lines.extend(f"  {span}" for span in down)
    if live:
        lines.append("")
        lines.append("embargoes")
        lines.extend(f"  {embargo}" for embargo in live)
    return lines
