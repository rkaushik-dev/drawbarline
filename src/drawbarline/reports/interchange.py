"""What crossed the junctions, and what is still standing here.

The interchange balance is the first thing a connection disputes, because it
is the only figure both roads compute independently from the same events. If
we say we took ninety cars from CN in January and they say ninety two, the
argument is about two sheets, and finding which two is a great deal easier
when both sides have the count by junction rather than by road.

Cars on hand is the other half. A car received and not delivered is standing
somewhere on this railroad costing money by the hour, and the report that says
how many and how long is the one the general manager asks for by name.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime

from drawbarline.clock import Interval
from drawbarline.events.kinds import EventKind
from drawbarline.events.positions import on_line, positions
from drawbarline.events.stream import EventLog
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.reports.render import Align, Column, Table, heading, hours

MINUTES_PER_HOUR = 60


@dataclass(frozen=True, slots=True)
class Crossing:
    """What went each way at one junction with one road."""

    junction: str
    road: ReportingMark
    received: int
    delivered: int

    @property
    def net(self) -> int:
        """Positive when we took more than we gave, which grows the fleet on hand."""
        return self.received - self.delivered


def crossings(log: EventLog, window: Interval) -> list[Crossing]:
    """Every junction and road pair that saw a car, in junction order."""
    counts: dict[tuple[str, ReportingMark], list[int]] = {}
    for event in log.during(window):
        if not event.kind.is_interchange or event.road is None:
            continue
        key = (event.station, event.road)
        seen = counts.setdefault(key, [0, 0])
        if event.kind is EventKind.RECEIVED:
            seen[0] += 1
        else:
            seen[1] += 1
    return [
        Crossing(junction, road, taken, given)
        for (junction, road), (taken, given) in sorted(
            counts.items(), key=lambda pair: (pair[0][0], str(pair[0][1]))
        )
    ]


def balance_report(log: EventLog, window: Interval, title: str) -> list[str]:
    """One line a junction and road, with the net at the end."""
    table = Table(
        [
            Column("junction"),
            Column("road"),
            Column("received", Align.RIGHT),
            Column("delivered", Align.RIGHT),
            Column("net", Align.RIGHT),
        ]
    )
    found = crossings(log, window)
    for one in found:
        table.add(one.junction, str(one.road), str(one.received), str(one.delivered), str(one.net))
    table.total(
        f"{len(found)} junctions",
        "",
        str(sum(one.received for one in found)),
        str(sum(one.delivered for one in found)),
        str(sum(one.net for one in found)),
    )
    return [*heading("interchange balance", title), *table.lines()]


@dataclass(frozen=True, slots=True)
class OnHand:
    """A car standing on our rail, and how long it has been."""

    car: CarMark
    station: str
    industry: str | None
    since: datetime
    minutes: int
    bad_ordered: bool
    held: bool

    @property
    def hundredth_hours(self) -> int:
        return round(self.minutes * 100 / MINUTES_PER_HOUR)


def on_hand(log: EventLog, at: datetime) -> list[OnHand]:
    """Every car we have taken and not yet handed back, oldest first."""
    where = positions(log, at)
    found = []
    for car in on_line(where):
        state = where[car]
        if state.received_at is None:
            continue
        found.append(
            OnHand(
                car=car,
                station=state.station or "",
                industry=state.industry,
                since=state.received_at,
                minutes=int((at - state.received_at).total_seconds()) // 60,
                bad_ordered=state.bad_ordered,
                held=state.held,
            )
        )
    return sorted(found, key=lambda one: (one.since, str(one.car)))


def on_hand_report(log: EventLog, at: datetime) -> list[str]:
    """One line a car, oldest first, with the reason it is stuck if there is one."""
    table = Table(
        [
            Column("car"),
            Column("where"),
            Column("since"),
            Column("hours", Align.RIGHT),
            Column("note"),
        ]
    )
    found = on_hand(log, at)
    for one in found:
        reasons = []
        if one.bad_ordered:
            reasons.append("bad order")
        if one.held:
            reasons.append("held")
        table.add(
            str(one.car),
            one.industry or one.station,
            f"{one.since:%Y-%m-%d %H:%M}",
            hours(one.hundredth_hours),
            " ".join(reasons),
        )
    table.total(
        f"{len(found)} cars",
        "",
        "",
        hours(sum(one.hundredth_hours for one in found)),
        "",
    )
    return [*heading("cars on hand", f"{at:%Y-%m-%d %H:%M}Z"), *table.lines()]
