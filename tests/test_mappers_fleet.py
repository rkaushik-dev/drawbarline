from datetime import date

import pytest

from drawbarline.errors import RegistryError
from drawbarline.fleet.aar import CarTypeCode, MechanicalDesignation
from drawbarline.fleet.car import Car
from drawbarline.fleet.ownership import Ownership, OwnershipError
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.measure import Length
from drawbarline.persistence.base import create_all, make_engine, opened
from drawbarline.persistence.mappers.fleet import (
    car_from_row,
    lease_rows_for,
    ownership_from_rows,
    row_from_car,
    update_row_from_car,
)
from drawbarline.persistence.models.fleet import CarRow, LeaseRow
from drawbarline.weight import Weight

DBL = ReportingMark("DBL")
CN = ReportingMark("CN")
ELS = ReportingMark("ELS")


def a_car(mark="DBL 104207", **changed):
    fields = {
        "mark": CarMark.parse(mark),
        "designation": MechanicalDesignation("LO"),
        "type_code": CarTypeCode("L351"),
        "built": date(2020, 6, 1),
        "light_weight": Weight(63500),
        "gross_rail_load": Weight(286000),
        "outside_length": Length.of(59, 6),
    }
    fields.update(changed)
    return Car(**fields)


@pytest.fixture
def engine():
    engine = make_engine()
    create_all(engine)
    return engine


def test_a_car_maps_to_a_row_and_back():
    assert car_from_row(row_from_car(a_car())) == a_car()


def test_a_rebuilt_car_keeps_its_rebuild_date():
    car = a_car(rebuilt=date(2022, 9, 1))
    assert car_from_row(row_from_car(car)).rebuilt == date(2022, 9, 1)


def test_the_owner_is_written_as_its_own_column():
    assert row_from_car(a_car()).owner == DBL


def test_a_private_car_writes_its_private_mark_as_the_owner():
    assert row_from_car(a_car("PLMX 9001")).owner == ReportingMark("PLMX")


def test_a_car_survives_a_trip_through_the_database(engine):
    with opened(engine) as session:
        session.add(row_from_car(a_car()))
    with opened(engine) as session:
        found = car_from_row(session.get(CarRow, CarMark.parse("DBL 104207")))
    assert found == a_car()


def test_a_correction_writes_onto_the_row_in_the_session(engine):
    with opened(engine) as session:
        session.add(row_from_car(a_car()))
    with opened(engine) as session:
        row = session.get(CarRow, CarMark.parse("DBL 104207"))
        update_row_from_car(row, a_car(light_weight=Weight(64200)))
    with opened(engine) as session:
        found = session.get(CarRow, CarMark.parse("DBL 104207"))
        assert found.light_weight == Weight(64200)


def test_a_correction_cannot_change_which_car_it_is():
    row = row_from_car(a_car())
    with pytest.raises(RegistryError):
        update_row_from_car(row, a_car("DBL 104208"))


def test_an_unleased_car_makes_no_lease_rows():
    assert lease_rows_for(CarMark.parse("DBL 104207"), Ownership(DBL)) == []


def test_every_term_becomes_a_row():
    ownership = Ownership(DBL)
    ownership.lease(CN, date(2025, 1, 1), date(2025, 6, 30))
    ownership.lease(ELS, date(2025, 7, 1))
    rows = lease_rows_for(CarMark.parse("DBL 104207"), ownership)
    assert [one.lessee for one in rows] == [CN, ELS]


def test_rows_replay_into_the_same_ownership():
    ownership = Ownership(DBL)
    ownership.lease(CN, date(2025, 1, 1), date(2025, 6, 30))
    ownership.lease(ELS, date(2025, 7, 1))
    rows = lease_rows_for(CarMark.parse("DBL 104207"), ownership)
    rebuilt = ownership_from_rows(DBL, rows)
    assert rebuilt.holder_on(date(2025, 3, 1)) == CN
    assert rebuilt.holder_on(date(2025, 8, 1)) == ELS


def test_rows_out_of_order_still_replay():
    rows = [
        LeaseRow(lessee=ELS, began=date(2025, 7, 1)),
        LeaseRow(lessee=CN, began=date(2025, 1, 1), ended=date(2025, 6, 30)),
    ]
    assert ownership_from_rows(DBL, rows).holder_on(date(2025, 3, 1)) == CN


def test_rows_that_overlap_are_refused_at_the_read():
    rows = [
        LeaseRow(lessee=CN, began=date(2025, 1, 1), ended=date(2025, 6, 30)),
        LeaseRow(lessee=ELS, began=date(2025, 5, 1), ended=date(2025, 12, 31)),
    ]
    with pytest.raises(OwnershipError):
        ownership_from_rows(DBL, rows)


def test_ownership_survives_a_trip_through_the_database(engine):
    ownership = Ownership(DBL)
    ownership.lease(CN, date(2025, 1, 1), date(2025, 6, 30))
    with opened(engine) as session:
        row = row_from_car(a_car())
        row.leases.extend(lease_rows_for(row.mark, ownership))
        session.add(row)
    with opened(engine) as session:
        row = session.get(CarRow, CarMark.parse("DBL 104207"))
        rebuilt = ownership_from_rows(row.owner, list(row.leases))
    assert rebuilt.holder_on(date(2025, 3, 1)) == CN
    assert rebuilt.holder_on(date(2025, 8, 1)) == DBL
