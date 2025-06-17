"""What the fleet did, and how old it is getting.

Turn time is the number the operating department is judged on. It is the hours
between taking a car and handing it back, and the part of that the car spent
standing on somebody's private track is the part they can do something about.
So the report keeps the two apart rather than printing one figure nobody can
act on.

The age profile is the other half of the same worry. Cars leave interchange
service fifty years after they were built and there is no appeal, so the
office wants to know every year how many go out in the next five. A fleet that
is fine this year and has thirty cars expiring in three is a fleet with a
capital problem nobody has noticed.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from drawbarline.clock import Interval
from drawbarline.events.kinds import EventKind
from drawbarline.events.spans import on_line_spans, on_track_spans
from drawbarline.events.stream import EventLog
from drawbarline.fleet.register import FleetRegister
from drawbarline.marks import CarMark
from drawbarline.reports.render import Align, Column, Table, heading, hours, miles

DECADE = 10
LOOKAHEAD_YEARS = 5


@dataclass(frozen=True, slots=True)
class Cycle:
    """One car's month, split into the parts somebody answers for."""

    car: CarMark
    visits: int
    on_line_minutes: int
    on_track_minutes: int
    tenths_run: int

    @property
    def moving_minutes(self) -> int:
        """Time on our rail that the car was not standing on a customer track."""
        return max(self.on_line_minutes - self.on_track_minutes, 0)

    @property
    def turn_hundredths(self) -> int:
        return round(self.on_line_minutes * 100 / 60)

    @property
    def standing_hundredths(self) -> int:
        return round(self.on_track_minutes * 100 / 60)


def cycle_of(log: EventLog, car: CarMark, window: Interval) -> Cycle:
    """Work one car's month out of its stream."""
    stream = log.stream(car)
    on_line = [one.interval.clip(window) for one in on_line_spans(stream, window.end)]
    on_track = [one.interval.clip(window) for one in on_track_spans(stream, window.end)]
    ran = sum(event.tenths or 0 for event in stream.during(window) if event.kind is EventKind.MOVED)
    return Cycle(
        car=car,
        visits=len([one for one in on_line if not one.empty]),
        on_line_minutes=sum(one.minutes for one in on_line),
        on_track_minutes=sum(one.minutes for one in on_track),
        tenths_run=ran,
    )


def cycles(log: EventLog, window: Interval) -> list[Cycle]:
    found = [cycle_of(log, car, window) for car in log.cars()]
    return [one for one in found if one.on_line_minutes or one.visits]


def utilisation_report(log: EventLog, window: Interval, title: str) -> list[str]:
    """One line a car, turn time against standing time."""
    table = Table(
        [
            Column("car"),
            Column("visits", Align.RIGHT),
            Column("turn hrs", Align.RIGHT),
            Column("standing", Align.RIGHT),
            Column("miles", Align.RIGHT),
        ]
    )
    found = cycles(log, window)
    for one in found:
        table.add(
            str(one.car),
            str(one.visits),
            hours(one.turn_hundredths),
            hours(one.standing_hundredths),
            miles(one.tenths_run),
        )
    table.total(
        f"{len(found)} cars",
        str(sum(one.visits for one in found)),
        hours(sum(one.turn_hundredths for one in found)),
        hours(sum(one.standing_hundredths for one in found)),
        miles(sum(one.tenths_run for one in found)),
    )
    return [*heading("fleet utilisation", title), *table.lines()]


def age_profile(register: FleetRegister, on: date) -> dict[str, int]:
    """How many cars sit in each decade of age."""
    found: dict[str, int] = {}
    for car in register:
        if not register.in_service_on(car.mark, on):
            continue
        decade = car.age_on(on) // DECADE * DECADE
        label = f"{decade} to {decade + DECADE - 1}"
        found[label] = found.get(label, 0) + 1
    return dict(sorted(found.items(), key=lambda pair: int(pair[0].split()[0])))


def expiring_within(register: FleetRegister, on: date, years: int) -> list[CarMark]:
    """Cars whose fifty years run out inside a number of years from a date."""
    horizon = date(on.year + years, on.month, min(on.day, 28))
    return [
        car.mark
        for car in register
        if register.in_service_on(car.mark, on) and car.interchange_expires <= horizon
    ]


def age_report(register: FleetRegister, on: date) -> list[str]:
    """The age profile with the retirement list under it."""
    table = Table([Column("age"), Column("cars", Align.RIGHT)])
    profile = age_profile(register, on)
    for label, count in profile.items():
        table.add(label, str(count))
    table.total("all cars", str(sum(profile.values())))

    leaving = expiring_within(register, on, LOOKAHEAD_YEARS)
    tail = [
        "",
        f"out of interchange service within {LOOKAHEAD_YEARS} years: {len(leaving)}",
    ]
    tail += [f"  {mark}" for mark in leaving]
    return [*heading("fleet age profile", str(on)), *table.lines(), *tail]
