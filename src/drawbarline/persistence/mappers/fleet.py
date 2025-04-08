"""Equipment records, in both directions.

A car row and a car carry the same fields, so the mapping is dull, which is
what a mapping should be. The one decision in here is that the owner is stored
as its own column even though it can be read off the marks. It is stored
because every query that matters filters on it, and an index on a column
derived at read time is not a thing.

Ownership is the interesting half. The domain holds it as a small history
object with the rule that terms do not overlap. The table holds one row a
term. Reading it back means replaying the terms through the same object that
enforces the rule, so a database somebody edited by hand fails at the read
rather than producing a car that is owed to two parties at once.
"""

from __future__ import annotations

from drawbarline.errors import RegistryError
from drawbarline.fleet.car import Car
from drawbarline.fleet.ownership import Ownership
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.persistence.models.fleet import CarRow, LeaseRow


def car_from_row(row: CarRow) -> Car:
    return Car(
        mark=row.mark,
        designation=row.designation,
        type_code=row.type_code,
        built=row.built,
        light_weight=row.light_weight,
        gross_rail_load=row.gross_rail_load,
        outside_length=row.outside_length,
        rebuilt=row.rebuilt,
    )


def row_from_car(car: Car) -> CarRow:
    return CarRow(
        mark=car.mark,
        owner=car.owner,
        designation=car.designation,
        type_code=car.type_code,
        built=car.built,
        rebuilt=car.rebuilt,
        light_weight=car.light_weight,
        gross_rail_load=car.gross_rail_load,
        outside_length=car.outside_length,
    )


def update_row_from_car(row: CarRow, car: Car) -> CarRow:
    """Write a corrected record onto the row already in the session."""
    if row.mark != car.mark:
        raise RegistryError(
            "a correction does not change which car it is",
            row=str(row.mark),
            car=str(car.mark),
        )
    row.owner = car.owner
    row.designation = car.designation
    row.type_code = car.type_code
    row.built = car.built
    row.rebuilt = car.rebuilt
    row.light_weight = car.light_weight
    row.gross_rail_load = car.gross_rail_load
    row.outside_length = car.outside_length
    return row


def ownership_from_rows(owner: ReportingMark, rows: list[LeaseRow]) -> Ownership:
    """Replay lease rows through the object that holds the no overlap rule."""
    ownership = Ownership(owner)
    for row in sorted(rows, key=lambda one: one.began):
        ownership.lease(row.lessee, row.began, row.ended)
    return ownership


def lease_rows_for(car: CarMark, ownership: Ownership) -> list[LeaseRow]:
    return [
        LeaseRow(car_mark=car, lessee=term.lessee, began=term.began, ended=term.ended)
        for term in ownership.terms
    ]
