"""Fleet routes.

A car is addressed by its marks, which contain a space, so the path segment is
whatever the client sends and the parsing is done by the same reader the wheel
report goes through. That means ``DBL%20104207`` and ``DBL104207`` both reach
the same car, which is what anybody typing one into a browser expects.

The write routes commit through the unit of work dependency. The read routes
take the read only one, so a get can never leave anything behind however badly
somebody writes a handler.
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, status

from drawbarline.api.deps import read_only, unit_of_work
from drawbarline.api.schemas import (
    CarIn,
    CarOut,
    LeaseIn,
    OwnershipOut,
    Retired,
    RetirementIn,
)
from drawbarline.errors import RegistryError
from drawbarline.fleet.aar import CarTypeCode, MechanicalDesignation
from drawbarline.fleet.car import Car
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.measure import Length
from drawbarline.persistence.uow import UnitOfWork
from drawbarline.weight import Weight

router = APIRouter(prefix="/cars", tags=["fleet"])


def car_of(filing: CarIn) -> Car:
    return Car(
        mark=CarMark.parse(filing.mark),
        designation=MechanicalDesignation.parse(filing.designation),
        type_code=CarTypeCode.parse(filing.type_code),
        built=filing.built,
        light_weight=Weight(filing.light_weight),
        gross_rail_load=Weight(filing.gross_rail_load),
        outside_length=Length(filing.outside_length),
        rebuilt=filing.rebuilt,
    )


@router.post("", status_code=status.HTTP_201_CREATED, response_model=CarOut)
def file_car(filing: CarIn, work: UnitOfWork = Depends(unit_of_work)) -> CarOut:
    """File an equipment record."""
    return CarOut.of(work.fleet.add(car_of(filing)))


@router.get("", response_model=list[CarOut])
def list_cars(owner: str | None = None, work: UnitOfWork = Depends(read_only)) -> list[CarOut]:
    """Every car on the register, in mark order."""
    cars = work.fleet.by_owner(ReportingMark.parse(owner)) if owner else work.fleet.all()
    return [CarOut.of(car) for car in cars]


@router.get("/{mark}", response_model=CarOut)
def show_car(mark: str, work: UnitOfWork = Depends(read_only)) -> CarOut:
    """One car."""
    return CarOut.of(work.fleet.get(CarMark.parse(mark)))


@router.put("/{mark}", response_model=CarOut)
def correct_car(mark: str, filing: CarIn, work: UnitOfWork = Depends(unit_of_work)) -> CarOut:
    """File a corrected record, keeping the lease history."""
    car = car_of(filing)
    if car.mark != CarMark.parse(mark):
        raise RegistryError(
            "a correction does not change which car it is", path=mark, filed=str(car.mark)
        )
    return CarOut.of(work.fleet.replace(car))


@router.get("/{mark}/ownership", response_model=OwnershipOut)
def show_ownership(mark: str, work: UnitOfWork = Depends(read_only)) -> OwnershipOut:
    """Who is owed for a car."""
    return OwnershipOut.of(work.fleet.ownership(CarMark.parse(mark)))


@router.post("/{mark}/leases", status_code=status.HTTP_201_CREATED, response_model=OwnershipOut)
def add_lease(mark: str, filing: LeaseIn, work: UnitOfWork = Depends(unit_of_work)) -> OwnershipOut:
    """File a lease term against a car."""
    car_mark = CarMark.parse(mark)
    ownership = work.fleet.ownership(car_mark)
    ownership.lease(ReportingMark.parse(filing.lessee), filing.began, filing.ended)
    return OwnershipOut.of(work.fleet.save_ownership(car_mark, ownership))


@router.post("/{mark}/retirement", status_code=status.HTTP_201_CREATED, response_model=Retired)
def retire_car(
    mark: str, filing: RetirementIn, work: UnitOfWork = Depends(unit_of_work)
) -> Retired:
    """Take a car off the roster."""
    car_mark = CarMark.parse(mark)
    work.fleet.retire(car_mark, filing.on, filing.reason)
    return Retired(mark=str(car_mark), on=work.fleet.retired_on(car_mark))
