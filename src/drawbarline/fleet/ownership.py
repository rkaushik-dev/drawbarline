"""Who the money for a car goes to, and when that changed.

The mark on the side says who owns the car. It does not say who collects for
it. A road leases a hundred covered hoppers to a grain shipper for three
years, and for those three years the hours run to the shipper, not to the
road, even though the stencil never changed.

So ownership is a small history rather than a field. Terms may not overlap,
because on any given day exactly one party is owed, and an overlap means two
parties will both bill and neither will back down.

A term with no end is open. There is at most one of those and it has to be the
last, otherwise a term filed later would silently disappear inside it.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from drawbarline.errors import DrawbarlineError
from drawbarline.marks import ReportingMark


class OwnershipError(DrawbarlineError):
    """A lease term cannot sit where it is being put."""

    code = "ownership"


@dataclass(frozen=True, slots=True)
class Lease:
    """A term during which somebody other than the owner collects."""

    lessee: ReportingMark
    began: date
    ended: date | None = None

    def __post_init__(self) -> None:
        if self.ended is not None and self.ended < self.began:
            raise OwnershipError(
                "a lease cannot end before it begins",
                lessee=str(self.lessee),
                began=str(self.began),
                ended=str(self.ended),
            )

    @property
    def open_ended(self) -> bool:
        return self.ended is None

    def covers(self, day: date) -> bool:
        """Both ends counted, because a lease runs to the end of its last day."""
        if day < self.began:
            return False
        return self.ended is None or day <= self.ended

    def overlaps(self, other: Lease) -> bool:
        left = self.ended is None or other.began <= self.ended
        right = other.ended is None or self.began <= other.ended
        return left and right

    def __str__(self) -> str:
        until = "open" if self.ended is None else str(self.ended)
        return f"{self.lessee} from {self.began} to {until}"


class Ownership:
    """The owner of one car and every term somebody else has held it under."""

    def __init__(self, owner: ReportingMark) -> None:
        self.owner = owner
        self._terms: list[Lease] = []

    def lease(self, lessee: ReportingMark, began: date, ended: date | None = None) -> Lease:
        term = Lease(lessee, began, ended)
        if lessee == self.owner:
            raise OwnershipError("an owner does not lease from itself", lessee=str(lessee))
        for standing in self._terms:
            if standing.overlaps(term):
                raise OwnershipError(
                    "that term overlaps one already filed",
                    filed=str(standing),
                    offered=str(term),
                )
        self._terms.append(term)
        self._terms.sort(key=lambda t: t.began)
        return term

    def end_open_term(self, on: date) -> Lease:
        """Close the open term, which is how a lease is cancelled early."""
        for index, term in enumerate(self._terms):
            if term.open_ended:
                closed = Lease(term.lessee, term.began, on)
                self._terms[index] = closed
                return closed
        raise OwnershipError("no term is open", on=str(on))

    def term_on(self, day: date) -> Lease | None:
        for term in self._terms:
            if term.covers(day):
                return term
        return None

    def holder_on(self, day: date) -> ReportingMark:
        """Who is owed for the car on a day."""
        term = self.term_on(day)
        return term.lessee if term is not None else self.owner

    def leased_on(self, day: date) -> bool:
        return self.term_on(day) is not None

    @property
    def terms(self) -> list[Lease]:
        return list(self._terms)
