"""Handing out document numbers without leaving gaps.

The rule the office cares about is that invoice 5001 is followed by invoice
5002 and that nobody can produce 5002 twice. Both halves come from the same
place: the number is taken by an update inside the caller's transaction, so a
second caller waits, and a caller that rolls back gives its number up.

A series that has never been used starts wherever the caller says it starts,
once. Asking again with a different start is refused rather than quietly
ignored, because a second start is somebody trying to renumber a series that
has documents in it.
"""

from __future__ import annotations

from sqlalchemy import select, update
from sqlalchemy.orm import Session

from drawbarline.errors import ConfigurationError
from drawbarline.persistence.models.numbering import CounterRow

INVOICES = "demurrage_invoice"
STATEMENTS = "statement"
REPAIR_CARDS = "repair_card"
WAYBILLS = "waybill"

SERIES = (INVOICES, STATEMENTS, REPAIR_CARDS, WAYBILLS)


class Numbering:
    """Every document series, and where each has got to."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def _row(self, name: str) -> CounterRow | None:
        return self.session.get(CounterRow, name)

    def start(self, name: str, at: int) -> int:
        """Open a series. Doing it twice is refused, not ignored."""
        if name not in SERIES:
            raise ConfigurationError("no such document series", series=name)
        if at < 1:
            raise ConfigurationError("a series starts at one or later", at=at)
        if self._row(name) is not None:
            raise ConfigurationError("that series is already open", series=name)
        self.session.add(CounterRow(name=name, next_number=at))
        self.session.flush()
        return at

    def peek(self, name: str) -> int:
        row = self._row(name)
        if row is None:
            raise ConfigurationError("that series has not been opened", series=name)
        return row.next_number

    def take(self, name: str) -> int:
        """One number, taken inside the caller's transaction."""
        return self.take_many(name, 1)[0]

    def take_many(self, name: str, count: int) -> list[int]:
        """A run of numbers, which is what cutting a month of statements needs."""
        if count < 1:
            raise ConfigurationError("ask for at least one number", count=count)
        row = self._row(name)
        if row is None:
            raise ConfigurationError("that series has not been opened", series=name)
        first = row.next_number
        self.session.execute(
            update(CounterRow)
            .where(CounterRow.name == name)
            .values(next_number=CounterRow.next_number + count)
        )
        self.session.expire(row)
        self.session.flush()
        return list(range(first, first + count))

    def open_series(self) -> list[str]:
        rows = self.session.execute(select(CounterRow.name).order_by(CounterRow.name))
        return list(rows.scalars())
