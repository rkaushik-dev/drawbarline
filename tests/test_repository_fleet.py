from datetime import date

import pytest

from drawbarline.errors import (
    ConfigurationError,
    DuplicateCar,
    RegistryError,
    UnknownCar,
)
from drawbarline.fleet.aar import CarKind, CarTypeCode, MechanicalDesignation
from drawbarline.fleet.car import Car
from drawbarline.fleet.ownership import Ownership
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.measure import Length
from drawbarline.persistence.base import create_all, make_engine
from drawbarline.persistence.uow import UnitOfWork
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


@pytest.fixture
def stocked(engine):
    with UnitOfWork(engine) as work:
        for mark in ("DBL 104207", "DBL 104208", "CN 400100"):
            work.fleet.add(a_car(mark))
        work.commit()
    return engine


def test_a_car_added_and_committed_comes_back(stocked):
    with UnitOfWork(stocked) as work:
        assert work.fleet.get(CarMark.parse("DBL 104207")) == a_car()


def test_a_car_added_without_a_commit_is_not_there(engine):
    with UnitOfWork(engine) as work:
        work.fleet.add(a_car())
    with UnitOfWork(engine) as work:
        assert work.fleet.has(CarMark.parse("DBL 104207")) is False


def test_a_block_that_raises_leaves_nothing_behind(engine):
    with pytest.raises(RuntimeError), UnitOfWork(engine) as work:
        work.fleet.add(a_car())
        raise RuntimeError("something went wrong")
    with UnitOfWork(engine) as work:
        assert len(work.fleet) == 0


def test_the_same_car_twice_is_refused(stocked):
    with UnitOfWork(stocked) as work, pytest.raises(DuplicateCar):
        work.fleet.add(a_car())


def test_a_car_nobody_filed_is_unknown(stocked):
    with UnitOfWork(stocked) as work, pytest.raises(UnknownCar):
        work.fleet.get(CarMark.parse("WC 1"))


def test_a_correction_is_written(stocked):
    with UnitOfWork(stocked) as work:
        work.fleet.replace(a_car(light_weight=Weight(64200)))
        work.commit()
    with UnitOfWork(stocked) as work:
        assert work.fleet.get(CarMark.parse("DBL 104207")).light_weight == Weight(64200)


def test_correcting_a_car_nobody_filed_is_refused(stocked):
    with UnitOfWork(stocked) as work, pytest.raises(UnknownCar):
        work.fleet.replace(a_car("WC 1"))


def test_a_new_car_is_owed_to_its_owner(stocked):
    with UnitOfWork(stocked) as work:
        assert work.fleet.ownership(CarMark.parse("CN 400100")).owner == CN


def test_leases_are_saved_and_read_back(stocked):
    mark = CarMark.parse("DBL 104207")
    with UnitOfWork(stocked) as work:
        ownership = work.fleet.ownership(mark)
        ownership.lease(CN, date(2025, 1, 1), date(2025, 6, 30))
        work.fleet.save_ownership(mark, ownership)
        work.commit()
    with UnitOfWork(stocked) as work:
        assert work.fleet.ownership(mark).holder_on(date(2025, 3, 1)) == CN


def test_saving_ownership_replaces_what_was_there(stocked):
    mark = CarMark.parse("DBL 104207")
    with UnitOfWork(stocked) as work:
        first = work.fleet.ownership(mark)
        first.lease(CN, date(2025, 1, 1), date(2025, 6, 30))
        work.fleet.save_ownership(mark, first)
        work.commit()
    with UnitOfWork(stocked) as work:
        second = Ownership(DBL)
        second.lease(ELS, date(2025, 1, 1), date(2025, 6, 30))
        work.fleet.save_ownership(mark, second)
        work.commit()
    with UnitOfWork(stocked) as work:
        assert work.fleet.ownership(mark).holder_on(date(2025, 3, 1)) == ELS
        assert len(work.fleet.ownership(mark).terms) == 1


def test_ownership_of_another_car_is_refused(stocked):
    with UnitOfWork(stocked) as work, pytest.raises(RegistryError):
        work.fleet.save_ownership(CarMark.parse("CN 400100"), Ownership(DBL))


def test_a_retirement_is_written_and_read(stocked):
    mark = CarMark.parse("DBL 104207")
    with UnitOfWork(stocked) as work:
        work.fleet.retire(mark, date(2025, 3, 31), "wreck at wallace")
        work.commit()
    with UnitOfWork(stocked) as work:
        assert work.fleet.retired_on(mark) == date(2025, 3, 31)


def test_retiring_twice_is_refused(stocked):
    mark = CarMark.parse("DBL 104207")
    with UnitOfWork(stocked) as work:
        work.fleet.retire(mark, date(2025, 3, 31))
        work.commit()
    with UnitOfWork(stocked) as work, pytest.raises(RegistryError):
        work.fleet.retire(mark, date(2025, 6, 30))


def test_retiring_before_the_build_is_refused(stocked):
    with UnitOfWork(stocked) as work, pytest.raises(RegistryError):
        work.fleet.retire(CarMark.parse("DBL 104207"), date(1990, 1, 1))


def test_the_cars_come_back_in_mark_order(stocked):
    with UnitOfWork(stocked) as work:
        assert [str(car.mark) for car in work.fleet.all()] == [
            "CN 400100",
            "DBL 104207",
            "DBL 104208",
        ]


def test_cars_come_back_by_owner(stocked):
    with UnitOfWork(stocked) as work:
        assert len(work.fleet.by_owner(DBL)) == 2


def test_cars_come_back_by_kind(stocked):
    with UnitOfWork(stocked) as work:
        assert len(work.fleet.by_kind(CarKind.COVERED_HOPPER)) == 3
        assert work.fleet.by_kind(CarKind.TANK) == []


def test_the_repository_builds_the_in_memory_register(stocked):
    mark = CarMark.parse("DBL 104207")
    with UnitOfWork(stocked) as work:
        ownership = work.fleet.ownership(mark)
        ownership.lease(CN, date(2025, 1, 1))
        work.fleet.save_ownership(mark, ownership)
        work.fleet.retire(CarMark.parse("DBL 104208"), date(2025, 3, 31))
        work.commit()
    with UnitOfWork(stocked) as work:
        register = work.fleet.register()
    assert len(register) == 3
    assert register.ownership(mark).holder_on(date(2025, 3, 1)) == CN
    assert register.retired_on(CarMark.parse("DBL 104208")) == date(2025, 3, 31)


def test_a_unit_of_work_that_is_not_open_has_no_session(engine):
    with pytest.raises(ConfigurationError):
        UnitOfWork(engine).session


def test_opening_the_same_unit_of_work_twice_is_refused(engine):
    with UnitOfWork(engine) as work, pytest.raises(ConfigurationError):
        work.__enter__()


def test_a_committed_unit_of_work_says_so(engine):
    with UnitOfWork(engine) as work:
        work.fleet.add(a_car())
        work.commit()
        assert work.committed is True


def test_a_rollback_undoes_what_has_not_been_committed(engine):
    with UnitOfWork(engine) as work:
        work.fleet.add(a_car())
        work.rollback()
        work.commit()
    with UnitOfWork(engine) as work:
        assert len(work.fleet) == 0
