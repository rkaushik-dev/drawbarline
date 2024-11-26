from datetime import date

import pytest

from drawbarline.fleet.aar import CarTypeCode, MechanicalDesignation
from drawbarline.fleet.acceptance import Finding, Refusal, acceptable, inspect
from drawbarline.fleet.car import Car
from drawbarline.fleet.register import FleetRegister
from drawbarline.marks import CarMark
from drawbarline.measure import Length
from drawbarline.weight import Weight

DAY = date(2025, 1, 14)


def a_car(mark="DBL 104207", built=date(1998, 6, 1)):
    return Car(
        mark=CarMark.parse(mark),
        designation=MechanicalDesignation("LO"),
        type_code=CarTypeCode("L351"),
        built=built,
        light_weight=Weight(63500),
        gross_rail_load=Weight(286000),
        outside_length=Length.of(59, 6),
    )


@pytest.fixture
def register():
    register = FleetRegister()
    register.add(a_car())
    return register


def test_a_sound_car_draws_no_findings(register):
    assert inspect(register, CarMark.parse("DBL 104207"), DAY) == []


def test_a_sound_car_is_acceptable(register):
    assert acceptable(register, CarMark.parse("DBL 104207"), DAY) is True


def test_a_car_with_no_record_is_refused(register):
    findings = inspect(register, CarMark.parse("WC 1"), DAY)
    assert [f.reason for f in findings] == [Refusal.NOT_REGISTERED]


def test_a_car_with_no_record_draws_nothing_else(register):
    findings = inspect(register, CarMark.parse("WC 1"), DAY, defects=("BROKEN",))
    assert len(findings) == 1


def test_a_retired_car_is_refused(register):
    mark = CarMark.parse("DBL 104207")
    register.retire(mark, date(2024, 12, 31))
    findings = inspect(register, mark, DAY)
    assert [f.reason for f in findings] == [Refusal.RETIRED]
    assert findings[0].detail == "2024-12-31"


def test_a_car_dated_before_it_existed_is_refused(register):
    findings = inspect(register, CarMark.parse("DBL 104207"), date(1990, 1, 1))
    assert [f.reason for f in findings] == [Refusal.NOT_YET_BUILT]


def test_a_car_past_fifty_years_is_refused():
    register = FleetRegister()
    register.add(a_car(built=date(1970, 1, 1)))
    findings = inspect(register, CarMark.parse("DBL 104207"), DAY)
    assert [f.reason for f in findings] == [Refusal.AGE]


def test_a_lading_inside_the_load_limit_passes(register):
    findings = inspect(register, CarMark.parse("DBL 104207"), DAY, lading=Weight(200000))
    assert findings == []


def test_a_lading_on_the_load_limit_passes(register):
    findings = inspect(register, CarMark.parse("DBL 104207"), DAY, lading=Weight(222500))
    assert findings == []


def test_an_overload_is_refused_and_says_how_far(register):
    findings = inspect(register, CarMark.parse("DBL 104207"), DAY, lading=Weight(225000))
    assert [f.reason for f in findings] == [Refusal.OVERLOADED]
    assert findings[0].detail == "over by 2,500 lb"


def test_every_defect_the_inspector_finds_is_its_own_finding(register):
    findings = inspect(register, CarMark.parse("DBL 104207"), DAY, defects=("WHEEL", "COUPLER"))
    assert [f.detail for f in findings] == ["WHEEL", "COUPLER"]


def test_findings_pile_up_rather_than_stopping_at_the_first(register):
    mark = CarMark.parse("DBL 104207")
    register.retire(mark, date(2024, 12, 31))
    findings = inspect(register, mark, DAY, lading=Weight(300000), defects=("WHEEL",))
    assert [f.reason for f in findings] == [
        Refusal.RETIRED,
        Refusal.OVERLOADED,
        Refusal.DEFECT,
    ]


def test_a_finding_with_no_detail_prints_the_reason_alone():
    assert str(Finding(Refusal.AGE)) == "past fifty years in interchange service"


def test_a_finding_with_detail_prints_it_in_brackets():
    assert str(Finding(Refusal.DEFECT, "WHEEL")) == (
        "carries a defect that has to be repaired first (WHEEL)"
    )


def test_a_car_with_any_finding_is_not_acceptable(register):
    assert acceptable(register, CarMark.parse("DBL 104207"), DAY, defects=("WHEEL",)) is False


def test_every_refusal_carries_readable_text():
    assert all(reason.text for reason in Refusal)
