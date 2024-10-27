"""The calendar the tariff counts on.

Car hire runs on hours and does not care what day it is. Demurrage runs on
days and cares a great deal, because a Saturday, a Sunday and six holidays a
year are not chargeable, and free time is quoted in chargeable days.

The holidays are computed rather than tabled. A table has to be extended every
December by somebody who remembers to, and the year it is not extended the
tariff quietly starts charging for Christmas. The observance rule is the
ordinary federal one: a holiday landing on a Saturday is kept the Friday
before, and one landing on a Sunday the Monday after.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta

from drawbarline.errors import DrawbarlineError

SATURDAY = 5
SUNDAY = 6


class CalendarError(DrawbarlineError):
    """A day cannot be placed on the calendar."""

    code = "calendar"


def _nth_weekday(year: int, month: int, weekday: int, nth: int) -> date:
    """The nth given weekday of a month, counting from one."""
    first = date(year, month, 1)
    ahead = (weekday - first.weekday()) % 7
    return first + timedelta(days=ahead + 7 * (nth - 1))


def _last_weekday(year: int, month: int, weekday: int) -> date:
    """The last given weekday of a month."""
    after = date(year + 1, 1, 1) if month == 12 else date(year, month + 1, 1)
    last = after - timedelta(days=1)
    return last - timedelta(days=(last.weekday() - weekday) % 7)


def observed(day: date) -> date:
    """Where a fixed date holiday actually falls for the office."""
    if day.weekday() == SATURDAY:
        return day - timedelta(days=1)
    if day.weekday() == SUNDAY:
        return day + timedelta(days=1)
    return day


@dataclass(frozen=True, slots=True)
class Holiday:
    """A day the tariff does not charge for."""

    name: str
    falls: date
    kept: date

    @property
    def moved(self) -> bool:
        return self.falls != self.kept


def holidays(year: int) -> tuple[Holiday, ...]:
    """The six days this tariff recognises, in the order they come."""
    if not 1900 <= year <= 2200:
        raise CalendarError("that year is outside anything we settle", year=year)
    fixed = {
        "new year": date(year, 1, 1),
        "independence day": date(year, 7, 4),
        "christmas": date(year, 12, 25),
    }
    moving = {
        "memorial day": _last_weekday(year, 5, 0),
        "labor day": _nth_weekday(year, 9, 0, 1),
        "thanksgiving": _nth_weekday(year, 11, 3, 4),
    }
    found = [Holiday(name, falls, observed(falls)) for name, falls in fixed.items()]
    found += [Holiday(name, falls, falls) for name, falls in moving.items()]
    return tuple(sorted(found, key=lambda h: h.kept))


def holiday_on(day: date) -> Holiday | None:
    """The holiday kept on a day, if any."""
    for holiday in holidays(day.year):
        if holiday.kept == day:
            return holiday
    return None


def is_holiday(day: date) -> bool:
    return holiday_on(day) is not None


def is_weekend(day: date) -> bool:
    return day.weekday() in (SATURDAY, SUNDAY)


def is_chargeable(day: date) -> bool:
    """A day demurrage may be charged for."""
    return not is_weekend(day) and not is_holiday(day)


def chargeable_days(first: date, last: date) -> int:
    """How many chargeable days there are from one day to another, both ends counted."""
    if last < first:
        raise CalendarError("that span runs backwards", first=str(first), last=str(last))
    count = 0
    day = first
    while day <= last:
        if is_chargeable(day):
            count += 1
        day += timedelta(days=1)
    return count


def next_chargeable(day: date) -> date:
    """The first chargeable day strictly after a day."""
    found = day + timedelta(days=1)
    while not is_chargeable(found):
        found += timedelta(days=1)
    return found


def add_chargeable_days(day: date, count: int) -> date:
    """Walk forward a number of chargeable days, which is how free time expires."""
    if count < 0:
        raise CalendarError("free time does not run backwards", count=count)
    found = day
    for _ in range(count):
        found = next_chargeable(found)
    return found
