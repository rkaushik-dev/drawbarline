"""The fleet repository.

Same shape as the in memory register and the same refusals, so a caller that
works against one works against the other. That is not an accident and it is
not for its own sake: the reports and the close are written against the
register, and being able to hand them either a register built in a test or one
backed by the database is what makes them testable at all.

Nothing here caches. A repository that keeps its own copy of a car is a second
source of truth, and the session already has an identity map doing the job
properly.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from drawbarline.errors import DuplicateCar, RegistryError, UnknownCar
from drawbarline.fleet.aar import CarKind
from drawbarline.fleet.car import Car
from drawbarline.fleet.ownership import Ownership
from drawbarline.fleet.register import FleetRegister
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.persistence.mappers.fleet import (
    car_from_row,
    lease_rows_for,
    ownership_from_rows,
    row_from_car,
    update_row_from_car,
)
from drawbarline.persistence.models.fleet import CarRow, RetirementRow


class FleetRepository:
    """Every car we have a record for, on the other side of a session."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def _row(self, mark: CarMark) -> CarRow:
        row = self.session.get(CarRow, mark)
        if row is None:
            raise UnknownCar("no record of that car", car=str(mark))
        return row

    def add(self, car: Car) -> Car:
        if self.session.get(CarRow, car.mark) is not None:
            raise DuplicateCar("that car is already on the register", car=str(car.mark))
        self.session.add(row_from_car(car))
        self.session.flush()
        return car

    def replace(self, car: Car) -> Car:
        update_row_from_car(self._row(car.mark), car)
        self.session.flush()
        return car

    def get(self, mark: CarMark) -> Car:
        return car_from_row(self._row(mark))

    def has(self, mark: CarMark) -> bool:
        return self.session.get(CarRow, mark) is not None

    def ownership(self, mark: CarMark) -> Ownership:
        row = self._row(mark)
        return ownership_from_rows(row.owner, list(row.leases))

    def save_ownership(self, mark: CarMark, ownership: Ownership) -> Ownership:
        row = self._row(mark)
        if ownership.owner != row.owner:
            raise RegistryError(
                "that ownership belongs to another car",
                car=str(mark),
                owner=str(ownership.owner),
            )
        row.leases.clear()
        self.session.flush()
        row.leases.extend(lease_rows_for(mark, ownership))
        self.session.flush()
        return ownership

    def retire(self, mark: CarMark, on: date, reason: str = "") -> None:
        row = self._row(mark)
        if on < row.built:
            raise RegistryError(
                "a car cannot be retired before it was built", car=str(mark), on=str(on)
            )
        if row.retirement is not None:
            raise RegistryError(
                "that car is already retired", car=str(mark), on=str(row.retirement.on)
            )
        row.retirement = RetirementRow(car_mark=mark, on=on, reason=reason)
        self.session.flush()

    def retired_on(self, mark: CarMark) -> date | None:
        row = self._row(mark)
        return row.retirement.on if row.retirement else None

    def all(self) -> list[Car]:
        rows = self.session.execute(select(CarRow).order_by(CarRow.mark)).scalars()
        return [car_from_row(row) for row in rows]

    def by_owner(self, owner: ReportingMark) -> list[Car]:
        found = self.session.execute(
            select(CarRow).where(CarRow.owner == owner).order_by(CarRow.mark)
        ).scalars()
        return [car_from_row(row) for row in found]

    def by_kind(self, kind: CarKind) -> list[Car]:
        return [car for car in self.all() if car.kind is kind]

    def register(self) -> FleetRegister:
        """Build the in memory register the reports and the close work against."""
        register = FleetRegister()
        for row in self.session.execute(select(CarRow).order_by(CarRow.mark)).scalars():
            car = car_from_row(row)
            register.add(car)
            for term in ownership_from_rows(row.owner, list(row.leases)).terms:
                register.ownership(car.mark).lease(term.lessee, term.began, term.ended)
            if row.retirement is not None:
                register.retire(car.mark, row.retirement.on)
        return register

    def __len__(self) -> int:
        return len(self.all())
