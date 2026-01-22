from datetime import date

import pytest

from drawbarline.carhire.deprescription import (
    Election,
    Elections,
    earns_car_hire,
    payable,
)
from drawbarline.carhire.rates import Band, Rate, RateError, RateTable
from drawbarline.fleet.aar import CarTypeCode, MechanicalDesignation
from drawbarline.fleet.car import Car
from drawbarline.marks import CarMark
from drawbarline.measure import Length
from drawbarline.weight import Weight

HOPPER = CarTypeCode("L351")
DAY = date(2025, 1, 14)


def a_car(built=date(2020, 6, 1)):
    return Car(
        mark=CarMark.parse("CN 400100"),
        designation=MechanicalDesignation("LO"),
        type_code=HOPPER,
        built=built,
        light_weight=Weight(63500),
        gross_rail_load=Weight(286000),
        outside_length=Length.of(59, 6),
    )


def a_schedule():
    table = RateTable()
    table.add(Band(HOPPER, 0, 9, date(2024, 7, 1), Rate(4630, 1210)))
    table.add(Band(HOPPER, 10, 99, date(2024, 7, 1), Rate(3180, 1210)))
    return table


def an_election(**changed):
    fields = {
        "type_code": HOPPER,
        "from_age": 0,
        "to_age": 9,
        "effective": date(2024, 7, 1),
        "hourly_tenths": 3900,
    }
    fields.update(changed)
    return Election(**fields)


def test_an_election_prints_its_band_and_rate():
    assert str(an_election()) == "L351 0-9 at 390.0c from 2024-07-01"


def test_a_band_that_runs_backwards_is_refused():
    with pytest.raises(RateError):
        an_election(from_age=9, to_age=0)


def test_a_band_starting_below_zero_is_refused():
    with pytest.raises(RateError):
        an_election(from_age=-1)


def test_a_negative_elected_rate_is_refused():
    with pytest.raises(RateError):
        an_election(hourly_tenths=-1)


def test_a_filing_below_the_schedule_is_taken():
    elections = Elections()
    elections.file(an_election(), a_schedule())
    assert len(elections) == 1


def test_a_filing_above_the_schedule_is_refused():
    elections = Elections()
    with pytest.raises(RateError) as caught:
        elections.file(an_election(hourly_tenths=5000), a_schedule())
    assert "never more" in caught.value.message


def test_a_filing_equal_to_the_schedule_is_taken():
    elections = Elections()
    elections.file(an_election(hourly_tenths=4630), a_schedule())
    assert len(elections) == 1


def test_a_filing_for_a_type_with_no_schedule_is_refused():
    elections = Elections()
    with pytest.raises(RateError):
        elections.file(an_election(type_code=CarTypeCode("T104")), a_schedule())


def test_an_elected_rate_reads_back():
    elections = Elections()
    elections.file(an_election(), a_schedule())
    assert elections.elected(HOPPER, 4, DAY) == 3900


def test_a_band_nobody_elected_reads_back_as_nothing():
    elections = Elections()
    elections.file(an_election(), a_schedule())
    assert elections.elected(HOPPER, 20, DAY) is None


def test_an_election_before_its_date_does_not_apply():
    elections = Elections()
    elections.file(an_election(effective=date(2025, 7, 1)), a_schedule())
    assert elections.elected(HOPPER, 4, DAY) is None


def test_a_later_filing_takes_over():
    elections = Elections()
    elections.file(an_election(), a_schedule())
    elections.file(an_election(effective=date(2025, 1, 1), hourly_tenths=3500), a_schedule())
    assert elections.elected(HOPPER, 4, DAY) == 3500


def test_the_filings_come_back_in_order():
    elections = Elections()
    elections.file(an_election(effective=date(2025, 1, 1)), a_schedule())
    elections.file(an_election(), a_schedule())
    assert [one.effective for one in elections.filings()] == [
        date(2024, 7, 1),
        date(2025, 1, 1),
    ]


def test_a_car_with_no_election_earns_the_schedule():
    assert payable(a_car(), a_schedule(), Elections(), DAY) == Rate(4630, 1210)


def test_a_car_with_an_election_earns_the_elected_rate():
    elections = Elections()
    elections.file(an_election(), a_schedule())
    assert payable(a_car(), a_schedule(), elections, DAY) == Rate(3900, 1210)


def test_an_election_leaves_the_mileage_half_alone():
    elections = Elections()
    elections.file(an_election(), a_schedule())
    found = payable(a_car(), a_schedule(), elections, DAY)
    assert found.mileage_mills == 1210


def test_a_car_past_its_fifty_years_earns_nothing():
    old = a_car(built=date(1970, 1, 1))
    assert payable(old, a_schedule(), Elections(), DAY) is None


def test_a_car_past_its_years_earns_nothing_even_with_an_election():
    elections = Elections()
    elections.file(an_election(), a_schedule())
    old = a_car(built=date(1970, 1, 1))
    assert payable(old, a_schedule(), elections, DAY) is None


def test_a_car_before_it_was_built_earns_nothing():
    assert payable(a_car(), a_schedule(), Elections(), date(1990, 1, 1)) is None


def test_the_eligibility_check_agrees_with_the_rate():
    old = a_car(built=date(1970, 1, 1))
    assert earns_car_hire(old, DAY) is False
    assert earns_car_hire(a_car(), DAY) is True


def test_an_older_car_falls_into_the_lower_band():
    older = a_car(built=date(2010, 1, 1))
    assert payable(older, a_schedule(), Elections(), DAY) == Rate(3180, 1210)


def test_an_election_on_one_band_leaves_the_other_alone():
    elections = Elections()
    elections.file(an_election(), a_schedule())
    older = a_car(built=date(2010, 1, 1))
    assert payable(older, a_schedule(), elections, DAY) == Rate(3180, 1210)
