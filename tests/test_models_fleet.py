from datetime import date

import pytest
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from drawbarline.fleet.aar import CarTypeCode, MechanicalDesignation
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.measure import Length
from drawbarline.persistence.base import create_all, make_engine, opened, table_names
from drawbarline.persistence.models import CarRow, LeaseRow, RetirementRow
from drawbarline.weight import Weight


@pytest.fixture
def engine():
    engine = make_engine()
    create_all(engine)
    return engine


def a_row(mark="DBL 104207", **changed):
    fields = {
        "mark": CarMark.parse(mark),
        "owner": ReportingMark(mark.split()[0]),
        "designation": MechanicalDesignation("LO"),
        "type_code": CarTypeCode("L351"),
        "built": date(2020, 6, 1),
        "light_weight": Weight(63500),
        "gross_rail_load": Weight(286000),
        "outside_length": Length.of(59, 6),
    }
    fields.update(changed)
    return CarRow(**fields)


def test_the_fleet_tables_are_on_the_metadata():
    for name in ("car", "lease", "retirement"):
        assert name in table_names()


def test_a_car_goes_in_and_comes_back_as_domain_objects(engine):
    with opened(engine) as session:
        session.add(a_row())
    with opened(engine) as session:
        found = session.get(CarRow, CarMark.parse("DBL 104207"))
    assert found.type_code == CarTypeCode("L351")
    assert found.light_weight == Weight(63500)
    assert found.outside_length == Length.of(59, 6)


def test_a_car_repr_names_its_marks():
    assert repr(a_row()) == "<CarRow DBL 104207>"


def test_the_same_car_twice_is_refused(engine):
    with opened(engine) as session:
        session.add(a_row())
    with pytest.raises(IntegrityError), opened(engine) as session:
        session.add(a_row())


def test_a_car_that_weighs_its_gross_rail_load_is_refused_by_the_table(engine):
    with pytest.raises(IntegrityError), opened(engine) as session:
        session.add(a_row(light_weight=Weight(286000)))


def test_a_car_of_no_length_is_refused_by_the_table(engine):
    with pytest.raises(IntegrityError), opened(engine) as session:
        session.add(a_row(outside_length=Length(0)))


def test_a_rebuild_date_is_optional(engine):
    with opened(engine) as session:
        session.add(a_row())
    with opened(engine) as session:
        assert session.get(CarRow, CarMark.parse("DBL 104207")).rebuilt is None


def test_a_lease_hangs_off_its_car(engine):
    with opened(engine) as session:
        car = a_row()
        car.leases.append(LeaseRow(lessee=ReportingMark("CN"), began=date(2025, 1, 1), ended=None))
        session.add(car)
    with opened(engine) as session:
        found = session.get(CarRow, CarMark.parse("DBL 104207"))
        assert [one.lessee for one in found.leases] == [ReportingMark("CN")]


def test_a_lease_against_a_car_nobody_filed_is_refused(engine):
    with pytest.raises(IntegrityError), opened(engine) as session:
        session.add(
            LeaseRow(
                car_mark=CarMark.parse("WC 000001"),
                lessee=ReportingMark("CN"),
                began=date(2025, 1, 1),
            )
        )


def test_a_lease_that_ends_before_it_begins_is_refused_by_the_table(engine):
    with pytest.raises(IntegrityError), opened(engine) as session:
        car = a_row()
        car.leases.append(
            LeaseRow(lessee=ReportingMark("CN"), began=date(2025, 6, 1), ended=date(2025, 1, 1))
        )
        session.add(car)


def test_two_leases_starting_the_same_day_are_refused(engine):
    with pytest.raises(IntegrityError), opened(engine) as session:
        car = a_row()
        car.leases.append(LeaseRow(lessee=ReportingMark("CN"), began=date(2025, 1, 1)))
        car.leases.append(LeaseRow(lessee=ReportingMark("ELS"), began=date(2025, 1, 1)))
        session.add(car)


def test_leases_come_back_in_the_order_they_ran(engine):
    with opened(engine) as session:
        car = a_row()
        car.leases.append(LeaseRow(lessee=ReportingMark("CN"), began=date(2025, 7, 1)))
        car.leases.append(
            LeaseRow(lessee=ReportingMark("ELS"), began=date(2025, 1, 1), ended=date(2025, 6, 30))
        )
        session.add(car)
    with opened(engine) as session:
        found = session.get(CarRow, CarMark.parse("DBL 104207"))
        assert [one.began for one in found.leases] == [date(2025, 1, 1), date(2025, 7, 1)]


def test_a_retirement_is_at_most_one_a_car(engine):
    with opened(engine) as session:
        car = a_row()
        car.retirement = RetirementRow(on=date(2025, 3, 31), reason="wreck at wallace")
        session.add(car)
    with opened(engine) as session:
        found = session.get(CarRow, CarMark.parse("DBL 104207"))
        assert found.retirement.reason == "wreck at wallace"


def test_deleting_a_car_takes_its_leases_with_it(engine):
    with opened(engine) as session:
        car = a_row()
        car.leases.append(LeaseRow(lessee=ReportingMark("CN"), began=date(2025, 1, 1)))
        session.add(car)
    with opened(engine) as session:
        session.delete(session.get(CarRow, CarMark.parse("DBL 104207")))
    with opened(engine) as session:
        assert session.execute(select(LeaseRow)).all() == []


def test_cars_can_be_found_by_owner(engine):
    with opened(engine) as session:
        session.add(a_row())
        session.add(a_row("DBL 104208"))
        session.add(a_row("CN 400100"))
    with opened(engine) as session:
        found = session.execute(
            select(CarRow).where(CarRow.owner == ReportingMark("DBL"))
        ).scalars()
        assert len(list(found)) == 2
