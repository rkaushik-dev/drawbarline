from datetime import date

import pytest

from drawbarline.fleet.aar import CarKind, CarTypeCode, MechanicalDesignation
from drawbarline.fleet.car import Car, CarRecordError
from drawbarline.marks import CarMark
from drawbarline.measure import Length
from drawbarline.weight import NominalCapacity, Weight, WeightError


def a_car(**changed):
    fields = {
        "mark": CarMark.parse("DBL 104207"),
        "designation": MechanicalDesignation("LO"),
        "type_code": CarTypeCode("L351"),
        "built": date(1998, 6, 1),
        "light_weight": Weight(63500),
        "gross_rail_load": Weight(286000),
        "outside_length": Length.of(59, 6),
    }
    fields.update(changed)
    return Car(**fields)


def test_a_car_prints_its_marks_and_designation():
    assert str(a_car()) == "DBL 104207 LO"


def test_a_car_knows_its_kind():
    assert a_car().kind is CarKind.COVERED_HOPPER


def test_a_car_knows_its_owner():
    assert str(a_car().owner) == "DBL"


def test_a_railroad_car_is_not_private():
    assert a_car().is_private is False


def test_a_car_under_a_private_mark_is_private():
    assert a_car(mark=CarMark.parse("PLMX 4207")).is_private is True


def test_the_load_limit_comes_off_the_two_weights():
    assert a_car().load_limit == Weight(222500)


def test_the_capacity_class_comes_off_the_gross_rail_load():
    assert a_car().capacity is NominalCapacity.ONE_TEN


def test_a_designation_and_type_code_that_disagree_are_refused():
    with pytest.raises(CarRecordError) as caught:
        a_car(type_code=CarTypeCode("X402"))
    assert caught.value.context["car"] == "DBL 104207"


def test_a_car_that_weighs_its_own_gross_rail_load_is_refused():
    with pytest.raises(WeightError):
        a_car(light_weight=Weight(286000))


def test_a_rebuild_before_the_build_is_refused():
    with pytest.raises(CarRecordError):
        a_car(rebuilt=date(1990, 1, 1))


def test_a_rebuild_on_the_build_date_is_allowed():
    assert a_car(rebuilt=date(1998, 6, 1)).dates_from == date(1998, 6, 1)


def test_the_fifty_year_clock_runs_from_the_build():
    assert a_car().interchange_expires == date(2048, 6, 1)


def test_a_rebuild_resets_the_fifty_year_clock():
    assert a_car(rebuilt=date(2012, 9, 15)).interchange_expires == date(2062, 9, 15)


def test_a_leap_day_build_expires_on_the_end_of_february():
    car = a_car(built=date(2000, 2, 29))
    assert car.interchange_expires == date(2050, 2, 28)


def test_a_car_inside_its_life_is_eligible():
    assert a_car().eligible_on(date(2025, 1, 14)) is True


def test_a_car_past_its_life_is_not_eligible():
    assert a_car(built=date(1970, 1, 1)).eligible_on(date(2025, 1, 14)) is False


def test_a_car_on_the_day_it_expires_is_not_eligible():
    assert a_car().eligible_on(date(2048, 6, 1)) is False


def test_a_car_before_it_was_built_is_not_eligible():
    assert a_car().eligible_on(date(1997, 1, 1)) is False


def test_age_counts_whole_years():
    assert a_car().age_on(date(2025, 6, 1)) == 27


def test_age_does_not_round_up_a_part_year():
    assert a_car().age_on(date(2025, 5, 31)) == 26


def test_age_before_the_car_existed_is_refused():
    with pytest.raises(CarRecordError):
        a_car().age_on(date(1990, 1, 1))


def test_two_records_of_the_same_car_compare_equal():
    assert a_car() == a_car()


def test_a_car_record_is_hashable():
    assert len({a_car(), a_car()}) == 1
