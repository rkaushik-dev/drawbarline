"""The embargo repository.

The whole register loads, because there are never many live embargoes and
every movement has to be checked against all of them. Lifting one goes through
the domain object so the rule that a lift cannot predate the embargo is
enforced in one place.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from drawbarline.embargo import Embargo, EmbargoError, EmbargoRegister
from drawbarline.persistence.models.embargo import EmbargoRow
from drawbarline.persistence.repositories.counting import count_of


def embargo_from_row(row: EmbargoRow) -> Embargo:
    return Embargo(
        number=row.number,
        effective=row.effective,
        reason=row.reason,
        station=row.station,
        commodity_family=row.commodity_family,
        road=row.road,
        expires=row.expires,
        permits_allowed=row.permits_allowed,
    )


def row_from_embargo(embargo: Embargo) -> EmbargoRow:
    return EmbargoRow(
        number=embargo.number,
        effective=embargo.effective,
        reason=embargo.reason,
        station=embargo.station,
        commodity_family=embargo.commodity_family,
        road=embargo.road,
        expires=embargo.expires,
        permits_allowed=embargo.permits_allowed,
    )


class EmbargoRepository:
    """Every embargo the road has issued, live or lifted."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def issue(self, embargo: Embargo) -> Embargo:
        if self.session.get(EmbargoRow, embargo.number) is not None:
            raise EmbargoError("that embargo is already issued", number=embargo.number)
        self.session.add(row_from_embargo(embargo))
        self.session.flush()
        return embargo

    def get(self, number: int) -> Embargo:
        row = self.session.get(EmbargoRow, number)
        if row is None:
            raise EmbargoError("no such embargo", number=number)
        return embargo_from_row(row)

    def lift(self, number: int, on: date) -> Embargo:
        row = self.session.get(EmbargoRow, number)
        if row is None:
            raise EmbargoError("no such embargo", number=number)
        register = EmbargoRegister()
        register.issue(embargo_from_row(row))
        lifted = register.lift(number, on)
        row.expires = lifted.expires
        self.session.flush()
        return lifted

    def all(self) -> list[Embargo]:
        rows = self.session.execute(select(EmbargoRow).order_by(EmbargoRow.number)).scalars()
        return [embargo_from_row(row) for row in rows]

    def in_force_on(self, day: date) -> list[Embargo]:
        return [one for one in self.all() if one.in_force_on(day)]

    def register(self) -> EmbargoRegister:
        found = EmbargoRegister()
        for one in self.all():
            found.issue(one)
        return found

    def __len__(self) -> int:
        return count_of(self.session, EmbargoRow.number)
