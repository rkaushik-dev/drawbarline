from datetime import date

import pytest

from drawbarline.carhire.accrual import (
    as_read_at,
    cut_points,
    hourly,
    midnight_on,
    mileage,
    pieces,
    settlement_date,
)
from drawbarline.carhire.deprescription import Election, Elections
from drawbarline.carhire.rates import Band, Rate, RateTable
from drawbarline.clock import Interval, utc
from drawbarline.errors import RateError
from drawbarline.fleet.aar import CarTypeCode, MechanicalDesignation
from drawbarline.fleet.car import Car
from drawbarline.marks import CarMark
from drawbarline.measure import Length
from drawbarline.money import Money
from drawbarline.reference import drawbar_line
from drawbarline.weight import Weight

HOPPER = CarTypeCode("L351")


def a_car(built=date(2020, 6, 1)):
    return Car(
        mark=CarMark.parse("DBL 104207"),
        designation=MechanicalDesignation("LO"),
        type_code=HOPPER,
        built=built,
        light_weight=Weight(63500),
        gross_rail_load=Weight(286000),
        outside_length=Length.of(59, 6),
    )


def a_table():
    table = RateTable()
    table.add(Band(HOPPER, 0, 9, date(2024, 7, 1), Rate(4630, 1210)))
    table.add(Band(HOPPER, 10, 19, date(2024, 7, 1), Rate(3180, 1210)))
    table.add(Band(HOPPER, 0, 9, date(2025, 7, 1), Rate(4880, 1290)))
    table.add(Band(HOPPER, 10, 19, date(2025, 7, 1), Rate(3400, 1290)))
    # The old cars are on one flat band, which is what the schedule does once
    # a car is past twenty.
    table.add(Band(HOPPER, 20, 99, date(2024, 7, 1), Rate(1470, 1210)))
    return table


def test_the_accounting_day_runs_on_central_time():
    assert settlement_date(utc(2025, 1, 14, 5, 30)) == date(2025, 1, 13)


def test_midnight_is_an_instant_not_a_date():
    assert midnight_on(date(2025, 1, 14)) == utc(2025, 1, 14, 6, 0)


def test_a_span_inside_one_rate_is_one_piece():
    window = Interval(utc(2025, 1, 14, 12), utc(2025, 1, 16, 12))
    assert len(pieces(a_car(), a_table(), window)) == 1


def test_a_piece_carries_the_rate_in_force():
    window = Interval(utc(2025, 1, 14, 12), utc(2025, 1, 16, 12))
    assert pieces(a_car(), a_table(), window)[0].rate == Rate(4630, 1210)


def test_an_empty_span_has_no_pieces():
    window = Interval(utc(2025, 1, 14, 12), utc(2025, 1, 14, 12))
    assert pieces(a_car(), a_table(), window) == []


def test_a_span_over_a_rate_change_is_cut():
    window = Interval(utc(2025, 6, 30, 12), utc(2025, 7, 2, 12))
    cuts = cut_points(a_car(), a_table(), window)
    assert cuts == [date(2025, 7, 1)]


def test_the_two_pieces_take_the_two_rates():
    window = Interval(utc(2025, 6, 30, 12), utc(2025, 7, 2, 12))
    found = pieces(a_car(), a_table(), window)
    assert [piece.rate for piece in found] == [Rate(4630, 1210), Rate(4880, 1290)]


def test_the_pieces_add_up_to_the_whole_span():
    window = Interval(utc(2025, 6, 30, 12), utc(2025, 7, 2, 12))
    found = pieces(a_car(), a_table(), window)
    assert sum(p.interval.minutes for p in found) == window.minutes


def test_a_birthday_inside_a_span_cuts_it_too():
    car = a_car(built=date(2015, 6, 15))
    window = Interval(utc(2025, 6, 14, 12), utc(2025, 6, 16, 12))
    assert cut_points(car, a_table(), window) == [date(2025, 6, 15)]


def test_the_birthday_steps_the_rate_down_a_band():
    car = a_car(built=date(2015, 6, 15))
    window = Interval(utc(2025, 6, 14, 12), utc(2025, 6, 16, 12))
    found = pieces(car, a_table(), window)
    assert [piece.rate for piece in found] == [Rate(4630, 1210), Rate(3180, 1210)]


def test_a_leap_day_car_has_a_birthday_every_year():
    car = a_car(built=date(2016, 2, 29))
    window = Interval(utc(2025, 2, 27, 12), utc(2025, 3, 2, 12))
    assert cut_points(car, a_table(), window) == [date(2025, 2, 28)]


def test_a_cut_exactly_on_the_span_start_is_not_a_cut():
    # July is daylight time, so midnight at the general office is 05:00 utc.
    window = Interval(utc(2025, 7, 1, 5), utc(2025, 7, 2, 12))
    assert cut_points(a_car(), a_table(), window) == []


def test_a_cut_exactly_on_the_span_end_is_not_a_cut():
    window = Interval(utc(2025, 6, 29, 12), utc(2025, 7, 1, 5))
    assert cut_points(a_car(), a_table(), window) == []


def test_one_hour_at_the_top_rate_is_the_rate_in_cents():
    window = Interval(utc(2025, 1, 14, 12), utc(2025, 1, 14, 13))
    assert hourly(a_car(), a_table(), window) == Money(463)


def test_a_day_is_twenty_four_times_an_hour():
    window = Interval(utc(2025, 1, 14, 12), utc(2025, 1, 15, 12))
    assert hourly(a_car(), a_table(), window) == Money(463 * 24)


def test_half_an_hour_is_half_the_rate():
    window = Interval(utc(2025, 1, 14, 12), utc(2025, 1, 14, 12, 30))
    assert hourly(a_car(), a_table(), window) == Money(232)


def test_an_empty_span_costs_nothing():
    window = Interval(utc(2025, 1, 14, 12), utc(2025, 1, 14, 12))
    assert hourly(a_car(), a_table(), window) == Money(0)


def test_rounding_happens_once_on_the_piece_not_on_each_hour():
    ten_minutes = Interval(utc(2025, 1, 14, 12), utc(2025, 1, 14, 12, 10))
    six_of_them = Interval(utc(2025, 1, 14, 12), utc(2025, 1, 14, 13))
    assert hourly(a_car(), a_table(), six_of_them) != hourly(a_car(), a_table(), ten_minutes) * 6


def test_a_piece_prints_its_day_hours_and_money():
    window = Interval(utc(2025, 1, 14, 12), utc(2025, 1, 14, 13))
    assert str(pieces(a_car(), a_table(), window)[0]) == "2025-01-14 1.00 hours $4.63"


def test_a_hundred_miles_is_a_hundred_times_the_mileage_rate():
    assert mileage(a_car(), a_table(), 1000, date(2025, 1, 14)) == Money(12100)


def test_a_tenth_of_a_mile_is_a_tenth_of_the_rate():
    assert mileage(a_car(), a_table(), 1, date(2025, 1, 14)) == Money(12)


def test_no_miles_costs_nothing():
    assert mileage(a_car(), a_table(), 0, date(2025, 1, 14)) == Money(0)


def test_negative_miles_are_refused():
    with pytest.raises(RateError):
        mileage(a_car(), a_table(), -1, date(2025, 1, 14))


def test_mileage_follows_the_rate_in_force():
    later = mileage(a_car(), a_table(), 1000, date(2025, 7, 1))
    assert later == Money(12900)


def test_a_span_reads_back_on_a_station_clock():
    line = drawbar_line()
    window = Interval(utc(2025, 1, 14, 14), utc(2025, 1, 14, 18))
    assert as_read_at(window, line.station("ESC")) == "2025-01-14 09:00 to 2025-01-14 13:00"


def test_the_same_span_reads_an_hour_earlier_across_the_seam():
    line = drawbar_line()
    window = Interval(utc(2025, 1, 14, 14), utc(2025, 1, 14, 18))
    assert as_read_at(window, line.station("MRN")).startswith("2025-01-14 08:00")


def a_condemned_car():
    return a_car(built=date(1970, 1, 1))


def test_a_condemned_car_is_priced_at_nothing():
    window = Interval(utc(2025, 1, 14, 12), utc(2025, 1, 16, 12))
    assert hourly(a_condemned_car(), a_table(), window) == Money(0)


def test_a_condemned_car_produces_no_pieces():
    window = Interval(utc(2025, 1, 14, 12), utc(2025, 1, 16, 12))
    assert pieces(a_condemned_car(), a_table(), window) == []


def test_a_condemned_car_runs_no_chargeable_miles():
    assert mileage(a_condemned_car(), a_table(), 1000, date(2025, 1, 14)) == Money(0)


def test_a_span_that_crosses_the_expiry_only_prices_the_live_part():
    car = a_car(built=date(1975, 6, 15))
    window = Interval(utc(2025, 6, 14, 12), utc(2025, 6, 16, 12))
    found = pieces(car, a_table(), window)
    assert len(found) == 1
    assert found[0].on == date(2025, 6, 14)


def test_an_election_lowers_the_hourly_charge():
    table = a_table()
    elections = Elections()
    elections.file(Election(HOPPER, 0, 9, date(2024, 7, 1), 3900), table)
    window = Interval(utc(2025, 1, 14, 12), utc(2025, 1, 14, 13))
    assert hourly(a_car(), table, window, elections) == Money(390)


def test_an_election_leaves_the_mileage_alone():
    table = a_table()
    elections = Elections()
    elections.file(Election(HOPPER, 0, 9, date(2024, 7, 1), 3900), table)
    assert mileage(a_car(), table, 1000, date(2025, 1, 14), elections) == Money(12100)


def test_no_election_prices_at_the_schedule():
    window = Interval(utc(2025, 1, 14, 12), utc(2025, 1, 14, 13))
    assert hourly(a_car(), a_table(), window, Elections()) == Money(463)
