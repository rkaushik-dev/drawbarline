"""The accounting period.

Everything settles by calendar month on the general office clock. A period
knows its own boundaries as instants, not as dates, because a span that ends
at midnight on the first has to belong to exactly one of two months and the
answer cannot depend on which zone the reader is in.

A period is open until it is closed and then nothing may be posted to it. It
can be reopened, but only with a reason on file, because reopening a month
after a statement has gone out means the statement was wrong and somebody has
to be able to find out why later. A month reopened and closed again is not the
same as a month that was never closed, and the register remembers the
difference.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from drawbarline.carhire.accrual import midnight_on
from drawbarline.clock import Interval
from drawbarline.errors import SettlementError

MONTHS_IN_A_YEAR = 12


@dataclass(frozen=True, slots=True)
class Period:
    """One calendar month of accounting."""

    year: int
    month: int

    def __post_init__(self) -> None:
        if not 1 <= self.month <= MONTHS_IN_A_YEAR:
            raise SettlementError("that is not a month", month=self.month)
        if not 1900 <= self.year <= 2200:
            raise SettlementError("that is not a year we settle", year=self.year)

    @classmethod
    def of(cls, day: date) -> Period:
        return cls(day.year, day.month)

    @property
    def first_day(self) -> date:
        return date(self.year, self.month, 1)

    @property
    def last_day(self) -> date:
        return self.following.first_day - _ONE_DAY

    @property
    def following(self) -> Period:
        if self.month == MONTHS_IN_A_YEAR:
            return Period(self.year + 1, 1)
        return Period(self.year, self.month + 1)

    @property
    def preceding(self) -> Period:
        if self.month == 1:
            return Period(self.year - 1, MONTHS_IN_A_YEAR)
        return Period(self.year, self.month - 1)

    @property
    def window(self) -> Interval:
        """The month as instants, closed at the start and open at the end."""
        return Interval(midnight_on(self.first_day), midnight_on(self.following.first_day))

    def covers(self, day: date) -> bool:
        return self.first_day <= day <= self.last_day

    @property
    def label(self) -> str:
        return f"{self.year:04d}-{self.month:02d}"

    def __str__(self) -> str:
        return self.label

    def __lt__(self, other: Period) -> bool:
        if not isinstance(other, Period):
            return NotImplemented
        return (self.year, self.month) < (other.year, other.month)


_ONE_DAY = date(2000, 1, 2) - date(2000, 1, 1)


@dataclass(frozen=True, slots=True)
class Reopening:
    """A month opened again after it was closed, and why."""

    period: Period
    on: date
    reason: str

    def __post_init__(self) -> None:
        if not self.reason.strip():
            raise SettlementError("reopening a month needs a reason", period=str(self.period))


class PeriodRegister:
    """Which months are closed, and every time one was opened again."""

    def __init__(self) -> None:
        self._closed: set[Period] = set()
        self._reopenings: list[Reopening] = []

    def close(self, period: Period) -> Period:
        if period in self._closed:
            raise SettlementError("that month is already closed", period=str(period))
        self._closed.add(period)
        return period

    def reopen(self, period: Period, on: date, reason: str) -> Reopening:
        if period not in self._closed:
            raise SettlementError("that month is not closed", period=str(period))
        self._closed.discard(period)
        found = Reopening(period, on, reason)
        self._reopenings.append(found)
        return found

    def is_open(self, period: Period) -> bool:
        return period not in self._closed

    def require_open(self, period: Period) -> Period:
        if period in self._closed:
            raise SettlementError("that month is closed to posting", period=str(period))
        return period

    def closed_periods(self) -> list[Period]:
        return sorted(self._closed)

    def reopenings(self, period: Period | None = None) -> list[Reopening]:
        if period is None:
            return list(self._reopenings)
        return [one for one in self._reopenings if one.period == period]

    def was_ever_reopened(self, period: Period) -> bool:
        return any(one.period == period for one in self._reopenings)
