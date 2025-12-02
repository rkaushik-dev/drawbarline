"""What is in the shop, and how long it has been there.

A bad ordered car is a car earning nothing and costing car hire, so the two
numbers the mechanical department is asked for are how many are down and how
long the ones that came back took. Both come off the same pair of events and
neither needs anything the log does not already carry.

The report separates the cars still down from the ones that came back, because
they are different questions. Cars still down are a queue somebody has to
work. Cars that came back are a record somebody is judged on.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime

from drawbarline.clock import Interval
from drawbarline.events.kinds import EventKind
from drawbarline.events.spans import scan_pairs
from drawbarline.events.stream import EventLog
from drawbarline.marks import CarMark
from drawbarline.reports.render import Align, Column, Table, heading

MINUTES_PER_DAY = 24 * 60


@dataclass(frozen=True, slots=True)
class ShopSpan:
    """One car, down and possibly back."""

    car: CarMark
    interval: Interval
    station: str
    still_down: bool

    @property
    def minutes(self) -> int:
        return self.interval.minutes

    @property
    def days(self) -> float:
        return self.minutes / MINUTES_PER_DAY

    def __str__(self) -> str:
        tail = "still down" if self.still_down else "back"
        return f"{self.car} {self.days:.1f} days {tail}"


def shop_spans(log: EventLog, until: datetime) -> list[ShopSpan]:
    """Every stretch a car has been bad ordered, up to a cut off."""
    found = []
    for car in log.cars():
        pairs = scan_pairs(
            log.stream(car),
            frozenset({EventKind.BAD_ORDERED}),
            frozenset({EventKind.REPAIRED}),
            until,
            what="the bad order",
        )
        for pair in pairs:
            found.append(
                ShopSpan(
                    car=car,
                    interval=pair.interval,
                    station=pair.opened_by.station,
                    still_down=pair.open,
                )
            )
    return sorted(found, key=lambda one: (one.interval.start, str(one.car)))


def down_now(spans: list[ShopSpan]) -> list[ShopSpan]:
    return [one for one in spans if one.still_down]


def came_back(spans: list[ShopSpan]) -> list[ShopSpan]:
    return [one for one in spans if not one.still_down]


def turn_days(spans: list[ShopSpan]) -> float:
    """The mean days to repair, over the cars that came back."""
    finished = came_back(spans)
    if not finished:
        return 0.0
    return sum(one.days for one in finished) / len(finished)


def shop_report(log: EventLog, until: datetime) -> list[str]:
    """The cars still down, then the ones that came back."""
    spans = shop_spans(log, until)
    lines = [*heading("bad order report", f"{until:%Y-%m-%d %H:%M}Z")]

    down = Table(
        [
            Column("car"),
            Column("at"),
            Column("since"),
            Column("days", Align.RIGHT),
        ]
    )
    for one in down_now(spans):
        down.add(
            str(one.car),
            one.station,
            f"{one.interval.start:%Y-%m-%d %H:%M}",
            f"{one.days:.1f}",
        )
    down.total(f"{len(down_now(spans))} down", "", "", "")
    lines += ["cars down", *down.lines(), ""]

    back = Table([Column("car"), Column("at"), Column("out"), Column("days", Align.RIGHT)])
    for one in came_back(spans):
        back.add(
            str(one.car),
            one.station,
            f"{one.interval.start:%Y-%m-%d}",
            f"{one.days:.1f}",
        )
    back.total(f"{len(came_back(spans))} back", "", "", f"{turn_days(spans):.1f}")
    lines += ["cars back in service", *back.lines()]
    return lines
