"""The fleet register.

One place that knows every car we have a record for, whether it is still in
service, and who is owed for it. Cars go in and are retired out. Nothing is
ever deleted, because a car retired in March is still on three months of
unsettled car hire and the settlement has to be able to look it up.

The register holds foreign cars as well as our own. We keep a record of any
car we have ever received in interchange, because we cannot bill a car we
cannot describe.
"""

from __future__ import annotations

from collections.abc import Iterator
from datetime import date

from drawbarline.errors import DuplicateCar, RegistryError, UnknownCar
from drawbarline.fleet.aar import CarKind, CarTypeCode
from drawbarline.fleet.car import Car
from drawbarline.fleet.ownership import Ownership
from drawbarline.marks import CarMark, ReportingMark


class FleetRegister:
    """Every car the road has a record for."""

    def __init__(self) -> None:
        self._cars: dict[CarMark, Car] = {}
        self._ownership: dict[CarMark, Ownership] = {}
        self._retired: dict[CarMark, date] = {}

    def add(self, car: Car) -> Car:
        if car.mark in self._cars:
            raise DuplicateCar("that car is already on the register", car=str(car.mark))
        self._cars[car.mark] = car
        self._ownership[car.mark] = Ownership(car.owner)
        return car

    def replace(self, car: Car) -> Car:
        """File a corrected record, keeping the lease history the car already has."""
        if car.mark not in self._cars:
            raise UnknownCar("no record of that car", car=str(car.mark))
        self._cars[car.mark] = car
        return car

    def get(self, mark: CarMark) -> Car:
        try:
            return self._cars[mark]
        except KeyError:
            raise UnknownCar("no record of that car", car=str(mark)) from None

    def has(self, mark: CarMark) -> bool:
        return mark in self._cars

    def ownership(self, mark: CarMark) -> Ownership:
        if mark not in self._ownership:
            raise UnknownCar("no record of that car", car=str(mark))
        return self._ownership[mark]

    def retire(self, mark: CarMark, on: date) -> None:
        car = self.get(mark)
        if on < car.built:
            raise RegistryError(
                "a car cannot be retired before it was built", car=str(mark), on=str(on)
            )
        if mark in self._retired:
            raise RegistryError(
                "that car is already retired", car=str(mark), on=str(self._retired[mark])
            )
        self._retired[mark] = on

    def retired_on(self, mark: CarMark) -> date | None:
        return self._retired.get(mark)

    def in_service_on(self, mark: CarMark, day: date) -> bool:
        car = self.get(mark)
        if day < car.built:
            return False
        retired = self._retired.get(mark)
        return retired is None or day <= retired

    def snapshot(self, day: date) -> list[Car]:
        """Every car in service on a day, in mark order."""
        return [car for car in self if self.in_service_on(car.mark, day)]

    def by_kind(self, kind: CarKind) -> list[Car]:
        return [car for car in self if car.kind is kind]

    def by_type_code(self, code: CarTypeCode) -> list[Car]:
        return [car for car in self if car.type_code == code]

    def by_owner(self, owner: ReportingMark) -> list[Car]:
        return [car for car in self if car.owner == owner]

    def private_cars(self) -> list[Car]:
        return [car for car in self if car.is_private]

    def expiring_before(self, day: date) -> list[Car]:
        """Cars whose fifty years run out before a date, which is the retirement list."""
        return [car for car in self if car.interchange_expires < day]

    def held_by(self, holder: ReportingMark, day: date) -> list[Car]:
        return [car for car in self if self.ownership(car.mark).holder_on(day) == holder]

    def __iter__(self) -> Iterator[Car]:
        for mark in sorted(self._cars):
            yield self._cars[mark]

    def __len__(self) -> int:
        return len(self._cars)

    def __contains__(self, mark: object) -> bool:
        return isinstance(mark, CarMark) and mark in self._cars
