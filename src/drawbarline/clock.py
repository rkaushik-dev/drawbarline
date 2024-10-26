"""The settlement clock.

Two rules run everything that follows. Inside the system a moment is an aware
datetime in UTC and nothing else, because two roads on either side of a time
zone boundary have to agree on the order of events. At the edges a moment is
local time at a station, because that is what the clerk wrote down and what the
tariff means by a day.

Between the two sits the awkward hour twice a year. On the spring morning
02:30 local does not exist, and on the autumn morning 01:30 happens twice. A
car really is placed at those times. We refuse the first outright, because the
clerk mistyped, and we refuse the second unless the caller says which of the
two it was. Guessing silently is how an hour of car hire goes missing every
November.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

from drawbarline.errors import DrawbarlineError
from drawbarline.network import Station

MINUTES_PER_HOUR = 60
SECONDS_PER_MINUTE = 60


class ClockError(DrawbarlineError):
    """A moment cannot be placed on the clock."""

    code = "clock"


def utc(year: int, month: int, day: int, hour: int = 0, minute: int = 0) -> datetime:
    """A moment stated directly in UTC, which is what tests and files carry."""
    return datetime(year, month, day, hour, minute, tzinfo=UTC)


def require_aware(moment: datetime, what: str = "moment") -> datetime:
    """Refuse a naive datetime rather than letting it mean local time by accident."""
    if moment.tzinfo is None or moment.utcoffset() is None:
        raise ClockError("a moment has to carry its zone", what=what)
    return moment.astimezone(UTC)


def to_utc(station: Station, local: datetime, *, second_pass: bool = False) -> datetime:
    """Read a local clock reading at a station as a moment.

    ``second_pass`` picks the later of the two readings on the morning the
    clocks go back. It is meaningless on every other day and is refused there,
    so a caller cannot leave it set and quietly shift an hour.
    """
    if local.tzinfo is not None:
        raise ClockError("a station reading is a local wall clock, with no zone attached")
    first = local.replace(tzinfo=station.tz, fold=0)
    second = local.replace(tzinfo=station.tz, fold=1)

    # A reading in the gap comes back shifted when it round trips, because the
    # zone has no offset that puts it where the clerk wrote it.
    rebuilt = first.astimezone(UTC).astimezone(station.tz).replace(tzinfo=None)
    if rebuilt != local:
        raise ClockError(
            "that reading did not happen, the clocks went forward",
            station=station.code,
            local=local.isoformat(),
        )

    if first.utcoffset() == second.utcoffset():
        if second_pass:
            raise ClockError(
                "that reading happened once, so there is no second pass",
                station=station.code,
                local=local.isoformat(),
            )
        return first.astimezone(UTC)
    return (second if second_pass else first).astimezone(UTC)


def to_local(station: Station, moment: datetime) -> datetime:
    """What the clock at a station read at that moment."""
    return require_aware(moment).astimezone(station.tz)


@dataclass(frozen=True, slots=True)
class Interval:
    """A half open span of time, closed at the start and open at the end."""

    start: datetime
    end: datetime

    def __post_init__(self) -> None:
        object.__setattr__(self, "start", require_aware(self.start, "start"))
        object.__setattr__(self, "end", require_aware(self.end, "end"))
        if self.end < self.start:
            raise ClockError(
                "an interval does not end before it starts",
                start=self.start.isoformat(),
                end=self.end.isoformat(),
            )

    @property
    def empty(self) -> bool:
        return self.start == self.end

    @property
    def minutes(self) -> int:
        """Whole minutes. Nothing on a railroad is billed finer than this."""
        return int((self.end - self.start).total_seconds()) // SECONDS_PER_MINUTE

    @property
    def hundredth_hours(self) -> int:
        """Hours to two places, which is the unit car hire is settled in."""
        return round(self.minutes * 100 / MINUTES_PER_HOUR)

    def contains(self, moment: datetime) -> bool:
        return self.start <= require_aware(moment) < self.end

    def overlaps(self, other: Interval) -> bool:
        return self.start < other.end and other.start < self.end

    def clip(self, window: Interval) -> Interval:
        """The part of this interval inside a window, possibly empty."""
        start = max(self.start, window.start)
        end = min(self.end, window.end)
        if end < start:
            return Interval(window.start, window.start)
        return Interval(start, end)

    def shift(self, by: timedelta) -> Interval:
        return Interval(self.start + by, self.end + by)

    def __str__(self) -> str:
        return f"{self.start:%Y-%m-%d %H:%M}Z to {self.end:%Y-%m-%d %H:%M}Z"
