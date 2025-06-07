"""What goes over the wire.

The domain objects are frozen dataclasses holding value objects, and none of
that serialises usefully. So there is a schema a resource, and every one of
them is written as plain scalars: pounds as integers, inches as integers, money
as cents. A client that wants dollars formats them, because a number that
arrives as a string with a currency symbol on it has been rounded by somebody
and there is no way to tell who.

Derived fields go out on the way back and are never accepted on the way in. A
load limit posted by a client is a load limit somebody can disagree with the
car about.
"""

from __future__ import annotations

from datetime import date

from pydantic import BaseModel, ConfigDict, Field

from drawbarline.fleet.car import Car
from drawbarline.fleet.ownership import Lease, Ownership


class CarIn(BaseModel):
    """An equipment record as it is filed."""

    model_config = ConfigDict(extra="forbid")

    mark: str = Field(description="Reporting mark and number, such as DBL 104207.")
    designation: str = Field(description="Mechanical designation, such as LO.")
    type_code: str = Field(description="AAR car type code, such as L351.")
    built: date
    light_weight: int = Field(description="Pounds.")
    gross_rail_load: int = Field(description="Pounds.")
    outside_length: int = Field(description="Inches over the pulling faces.")
    rebuilt: date | None = None


class CarOut(BaseModel):
    """An equipment record as it reads back."""

    mark: str
    stencil: str
    owner: str
    designation: str
    type_code: str
    kind: str
    built: date
    rebuilt: date | None
    light_weight: int
    gross_rail_load: int
    load_limit: int
    outside_length: int
    private: bool
    interchange_expires: date

    @classmethod
    def of(cls, car: Car) -> CarOut:
        return cls(
            mark=str(car.mark),
            stencil=car.mark.stencilled(),
            owner=str(car.owner),
            designation=str(car.designation),
            type_code=str(car.type_code),
            kind=car.kind.description,
            built=car.built,
            rebuilt=car.rebuilt,
            light_weight=car.light_weight.pounds,
            gross_rail_load=car.gross_rail_load.pounds,
            load_limit=car.load_limit.pounds,
            outside_length=car.outside_length.inches,
            private=car.is_private,
            interchange_expires=car.interchange_expires,
        )


class LeaseIn(BaseModel):
    """A term somebody other than the owner collects for."""

    model_config = ConfigDict(extra="forbid")

    lessee: str
    began: date
    ended: date | None = None


class LeaseOut(BaseModel):
    lessee: str
    began: date
    ended: date | None

    @classmethod
    def of(cls, term: Lease) -> LeaseOut:
        return cls(lessee=str(term.lessee), began=term.began, ended=term.ended)


class OwnershipOut(BaseModel):
    """Who is owed for a car, and every term on file."""

    owner: str
    terms: list[LeaseOut]

    @classmethod
    def of(cls, ownership: Ownership) -> OwnershipOut:
        return cls(
            owner=str(ownership.owner),
            terms=[LeaseOut.of(term) for term in ownership.terms],
        )


class RetirementIn(BaseModel):
    """Taking a car off the roster."""

    model_config = ConfigDict(extra="forbid")

    on: date
    reason: str = ""


class Retired(BaseModel):
    mark: str
    on: date | None
