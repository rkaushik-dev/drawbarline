from datetime import date

import pytest

from drawbarline.errors import DuplicateCar, RegistryError, UnknownCar
from drawbarline.fleet.aar import CarKind, CarTypeCode, MechanicalDesignation
from drawbarline.fleet.car import Car
from drawbarline.fleet.register import FleetRegister
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.measure import Length
from drawbarline.weight import Weight

HOPPER = (MechanicalDesignation("LO"), CarTypeCode("L351"))
BOX = (MechanicalDesignation("XM"), CarTypeCode("X202"))


def a_car(mark, kinds=HOPPER, built=date(1998, 6, 1)):
    designation, type_code = kinds
    return Car(
        mark=CarMark.parse(mark),
        designation=designation,
        type_code=type_code,
        built=built,
        light_weight=Weight(63500),
        gross_rail_load=Weight(286000),
        outside_length=Length.of(59, 6),
    )


def a_register():
    register = FleetRegister()
    register.add(a_car("DBL 104207"))
    register.add(a_car("DBL 104208"))
    register.add(a_car("CN 400100", BOX))
    register.add(a_car("PLMX 9001", BOX))
    return register


def test_an_empty_register_holds_nothing():
    assert len(FleetRegister()) == 0


def test_a_car_goes_on_and_comes_back():
    register = FleetRegister()
    car = register.add(a_car("DBL 104207"))
    assert register.get(CarMark.parse("DBL 104207")) is car


def test_the_same_car_twice_is_refused():
    register = a_register()
    with pytest.raises(DuplicateCar):
        register.add(a_car("DBL 104207"))


def test_a_car_nobody_filed_is_unknown():
    with pytest.raises(UnknownCar):
        a_register().get(CarMark.parse("WC 1"))


def test_membership_reads_off_the_mark():
    register = a_register()
    assert CarMark.parse("DBL 104207") in register
    assert CarMark.parse("WC 1") not in register
    assert "DBL 104207" not in register


def test_the_register_iterates_in_mark_order():
    assert [str(car.mark) for car in a_register()] == [
        "CN 400100",
        "DBL 104207",
        "DBL 104208",
        "PLMX 009001",
    ]


def test_a_corrected_record_replaces_the_old_one():
    register = a_register()
    corrected = a_car("DBL 104207", built=date(1999, 1, 1))
    register.replace(corrected)
    assert register.get(CarMark.parse("DBL 104207")).built == date(1999, 1, 1)


def test_a_correction_keeps_the_lease_history():
    register = a_register()
    mark = CarMark.parse("DBL 104207")
    register.ownership(mark).lease(ReportingMark("CN"), date(2025, 1, 1))
    register.replace(a_car("DBL 104207", built=date(1999, 1, 1)))
    assert register.ownership(mark).holder_on(date(2025, 3, 1)) == ReportingMark("CN")


def test_correcting_a_car_nobody_filed_is_refused():
    with pytest.raises(UnknownCar):
        a_register().replace(a_car("WC 1"))


def test_a_new_car_starts_owed_to_its_owner():
    register = a_register()
    mark = CarMark.parse("CN 400100")
    assert register.ownership(mark).holder_on(date(2025, 3, 1)) == ReportingMark("CN")


def test_ownership_of_a_car_nobody_filed_is_refused():
    with pytest.raises(UnknownCar):
        a_register().ownership(CarMark.parse("WC 1"))


def test_a_car_is_in_service_the_day_it_was_built():
    register = a_register()
    assert register.in_service_on(CarMark.parse("DBL 104207"), date(1998, 6, 1)) is True


def test_a_car_is_not_in_service_before_it_was_built():
    register = a_register()
    assert register.in_service_on(CarMark.parse("DBL 104207"), date(1998, 5, 31)) is False


def test_a_retired_car_stays_in_service_through_its_last_day():
    register = a_register()
    mark = CarMark.parse("DBL 104207")
    register.retire(mark, date(2025, 3, 31))
    assert register.in_service_on(mark, date(2025, 3, 31)) is True
    assert register.in_service_on(mark, date(2025, 4, 1)) is False


def test_a_retired_car_is_still_on_the_register():
    register = a_register()
    mark = CarMark.parse("DBL 104207")
    register.retire(mark, date(2025, 3, 31))
    assert register.get(mark) is not None
    assert register.retired_on(mark) == date(2025, 3, 31)


def test_a_car_still_running_has_no_retirement_date():
    assert a_register().retired_on(CarMark.parse("DBL 104207")) is None


def test_retiring_a_car_twice_is_refused():
    register = a_register()
    mark = CarMark.parse("DBL 104207")
    register.retire(mark, date(2025, 3, 31))
    with pytest.raises(RegistryError):
        register.retire(mark, date(2025, 6, 30))


def test_retiring_a_car_before_it_was_built_is_refused():
    register = a_register()
    with pytest.raises(RegistryError):
        register.retire(CarMark.parse("DBL 104207"), date(1990, 1, 1))


def test_a_snapshot_leaves_out_what_is_retired():
    register = a_register()
    register.retire(CarMark.parse("DBL 104207"), date(2025, 3, 31))
    marks = [str(car.mark) for car in register.snapshot(date(2025, 6, 1))]
    assert "DBL 104207" not in marks
    assert len(marks) == 3


def test_cars_come_back_by_kind():
    assert len(a_register().by_kind(CarKind.BOX)) == 2
    assert len(a_register().by_kind(CarKind.TANK)) == 0


def test_cars_come_back_by_type_code():
    assert len(a_register().by_type_code(CarTypeCode("L351"))) == 2


def test_cars_come_back_by_owner():
    owned = a_register().by_owner(ReportingMark("DBL"))
    assert [str(car.mark) for car in owned] == ["DBL 104207", "DBL 104208"]


def test_private_cars_come_back_on_their_own():
    assert [str(c.mark) for c in a_register().private_cars()] == ["PLMX 009001"]


def test_cars_running_out_of_life_come_back_together():
    register = a_register()
    register.add(a_car("CN 300001", BOX, built=date(1972, 1, 1)))
    expiring = register.expiring_before(date(2025, 1, 1))
    assert [str(car.mark) for car in expiring] == ["CN 300001"]


def test_cars_come_back_by_who_holds_them():
    register = a_register()
    register.ownership(CarMark.parse("DBL 104207")).lease(
        ReportingMark("ELS"), date(2025, 1, 1), date(2025, 6, 30)
    )
    held = register.held_by(ReportingMark("ELS"), date(2025, 3, 1))
    assert [str(car.mark) for car in held] == ["DBL 104207"]
