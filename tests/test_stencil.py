from datetime import date

import pytest

from drawbarline.fleet.aar import CarTypeCode, MechanicalDesignation
from drawbarline.fleet.car import Car
from drawbarline.fleet.stencil import (
    StencilError,
    Weighing,
    block,
    due_by,
    exempt,
    load_limit_from,
    overdue_on,
)
from drawbarline.marks import CarMark
from drawbarline.measure import Length
from drawbarline.weight import Weight


def a_car(designation="LO", type_code="L351"):
    return Car(
        mark=CarMark.parse("DBL 104207"),
        designation=MechanicalDesignation(designation),
        type_code=CarTypeCode(type_code),
        built=date(1998, 6, 1),
        light_weight=Weight(63500),
        gross_rail_load=Weight(286000),
        outside_length=Length.of(59, 6),
    )


def a_weighing(**changed):
    fields = {"pounds": Weight(63500), "on": date(2015, 3, 4), "where": "ESC"}
    fields.update(changed)
    return Weighing(**fields)


def test_a_weighing_prints_the_place_and_the_stamp():
    assert str(a_weighing()) == "ESC 3-15"


def test_a_stamp_carries_two_digits_of_year():
    assert a_weighing(on=date(2005, 11, 1)).stamp == "11-05"


def test_a_builders_weight_says_new():
    assert a_weighing(where="NEW").as_built is True


def test_a_scale_weighing_is_not_as_built():
    assert a_weighing().as_built is False


def test_a_place_is_taken_upper_case():
    assert a_weighing(where="esc").where == "ESC"


def test_a_weighing_with_no_place_is_refused():
    with pytest.raises(StencilError):
        a_weighing(where="  ")


def test_a_place_that_is_not_a_station_code_is_refused():
    with pytest.raises(StencilError):
        a_weighing(where="the scale at powers")


def test_a_hopper_is_due_back_in_ten_years():
    assert due_by(a_car(), a_weighing()) == date(2025, 3, 4)


def test_a_leap_day_weighing_comes_due_at_the_end_of_february():
    assert due_by(a_car(), a_weighing(on=date(2016, 2, 29))) == date(2026, 2, 28)


def test_a_tank_car_is_exempt_from_reweighing():
    tank = a_car("T", "T104")
    assert exempt(tank) is True
    assert due_by(tank, a_weighing()) is None


def test_maintenance_equipment_is_exempt():
    assert exempt(a_car("M", "M930")) is True


def test_a_hopper_is_not_exempt():
    assert exempt(a_car()) is False


def test_a_car_inside_its_cycle_is_not_overdue():
    assert overdue_on(a_car(), a_weighing(), date(2025, 3, 4)) is False


def test_a_car_past_its_cycle_is_overdue():
    assert overdue_on(a_car(), a_weighing(), date(2025, 3, 5)) is True


def test_an_exempt_car_is_never_overdue():
    assert overdue_on(a_car("T", "T104"), a_weighing(), date(2099, 1, 1)) is False


def test_the_load_limit_uses_the_weighed_light_weight():
    assert load_limit_from(a_car(), a_weighing(pounds=Weight(64200))) == Weight(221800)


def test_a_heavier_car_carries_less():
    lighter = load_limit_from(a_car(), a_weighing(pounds=Weight(63500)))
    heavier = load_limit_from(a_car(), a_weighing(pounds=Weight(64200)))
    assert heavier < lighter


def test_a_scale_reading_at_the_gross_rail_load_is_refused():
    with pytest.raises(StencilError):
        load_limit_from(a_car(), a_weighing(pounds=Weight(286000)))


def test_the_stencil_block_reads_the_way_the_car_is_painted():
    assert block(a_car(), a_weighing()) == [
        "DBL 104207",
        "LO L351",
        "CAPY 286000",
        "LD LMT 222500",
        "LT WT 63500",
        "RWT ESC 3-15",
    ]


def test_a_car_that_has_never_been_reweighed_carries_its_build_stamp():
    lines = block(a_car(), a_weighing(where="NEW", on=date(1998, 6, 1)))
    assert lines[-1] == "NEW 6-98"
