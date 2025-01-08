from datetime import date

import pytest

from drawbarline.carhire.rates import Band, Rate, RateTable
from drawbarline.errors import RateError
from drawbarline.fleet.aar import CarTypeCode

HOPPER = CarTypeCode("L351")
BOX = CarTypeCode("X202")


def a_table():
    table = RateTable()
    table.add(Band(HOPPER, 0, 9, date(2024, 7, 1), Rate(4630, 1210)))
    table.add(Band(HOPPER, 10, 19, date(2024, 7, 1), Rate(3180, 1210)))
    table.add(Band(HOPPER, 20, 99, date(2024, 7, 1), Rate(1470, 1210)))
    table.add(Band(HOPPER, 0, 9, date(2025, 7, 1), Rate(4880, 1290)))
    table.add(Band(BOX, 0, 99, date(2024, 7, 1), Rate(2240, 980)))
    return table


def test_a_rate_prints_both_halves():
    assert str(Rate(4630, 1210)) == "463.0c an hour and 121.0c a mile"


def test_a_rate_reads_in_cents():
    rate = Rate(4630, 1210)
    assert rate.hourly_cents == 463.0
    assert rate.mileage_cents == 121.0


def test_a_negative_hourly_rate_is_refused():
    with pytest.raises(RateError):
        Rate(-1, 100)


def test_a_negative_mileage_rate_is_refused():
    with pytest.raises(RateError):
        Rate(100, -1)


def test_a_free_rate_is_allowed():
    assert Rate(0, 0).hourly_cents == 0


def test_a_band_covers_its_own_ages():
    band = Band(HOPPER, 10, 19, date(2024, 7, 1), Rate(3180, 1210))
    assert band.covers_age(10) is True
    assert band.covers_age(19) is True
    assert band.covers_age(20) is False


def test_a_band_that_ends_before_it_starts_is_refused():
    with pytest.raises(RateError):
        Band(HOPPER, 19, 10, date(2024, 7, 1), Rate(1, 1))


def test_a_band_starting_below_zero_is_refused():
    with pytest.raises(RateError):
        Band(HOPPER, -1, 10, date(2024, 7, 1), Rate(1, 1))


def test_a_new_car_takes_the_top_rate():
    assert a_table().lookup(HOPPER, 3, date(2025, 1, 14)) == Rate(4630, 1210)


def test_an_older_car_takes_a_lower_rate():
    assert a_table().lookup(HOPPER, 25, date(2025, 1, 14)) == Rate(1470, 1210)


def test_the_band_boundary_belongs_to_the_lower_band():
    assert a_table().lookup(HOPPER, 9, date(2025, 1, 14)) == Rate(4630, 1210)
    assert a_table().lookup(HOPPER, 10, date(2025, 1, 14)) == Rate(3180, 1210)


def test_a_rate_filed_later_takes_over_on_its_date():
    table = a_table()
    assert table.lookup(HOPPER, 3, date(2025, 6, 30)) == Rate(4630, 1210)
    assert table.lookup(HOPPER, 3, date(2025, 7, 1)) == Rate(4880, 1290)


def test_a_band_with_no_later_filing_keeps_running():
    assert a_table().lookup(HOPPER, 25, date(2026, 1, 1)) == Rate(1470, 1210)


def test_a_date_before_anything_was_filed_has_no_rate():
    with pytest.raises(RateError):
        a_table().lookup(HOPPER, 3, date(2024, 1, 1))


def test_a_car_type_with_no_rate_is_refused():
    with pytest.raises(RateError):
        a_table().lookup(CarTypeCode("T104"), 3, date(2025, 1, 14))


def test_a_negative_age_is_refused():
    with pytest.raises(RateError):
        a_table().lookup(HOPPER, -1, date(2025, 1, 14))


def test_an_age_past_every_band_is_refused():
    table = RateTable()
    table.add(Band(BOX, 0, 40, date(2024, 7, 1), Rate(2240, 980)))
    with pytest.raises(RateError):
        table.lookup(BOX, 41, date(2025, 1, 14))


def test_two_bands_overlapping_on_the_same_date_are_refused():
    table = a_table()
    with pytest.raises(RateError):
        table.add(Band(HOPPER, 5, 15, date(2024, 7, 1), Rate(1, 1)))


def test_the_same_ages_on_a_different_date_are_fine():
    table = a_table()
    table.add(Band(HOPPER, 10, 19, date(2026, 1, 1), Rate(3300, 1290)))
    assert table.lookup(HOPPER, 12, date(2026, 2, 1)) == Rate(3300, 1290)


def test_the_effective_dates_come_back_in_order():
    assert a_table().effective_dates(HOPPER) == [date(2024, 7, 1), date(2025, 7, 1)]


def test_a_type_with_no_bands_has_no_effective_dates():
    assert a_table().effective_dates(CarTypeCode("T104")) == []


def test_a_span_reports_where_the_rate_steps():
    changes = a_table().changes_between(HOPPER, date(2025, 6, 1), date(2025, 8, 1))
    assert changes == [date(2025, 7, 1)]


def test_a_span_with_no_step_reports_nothing():
    assert a_table().changes_between(HOPPER, date(2025, 1, 1), date(2025, 6, 1)) == []


def test_a_step_on_the_first_day_of_the_span_is_not_a_split():
    assert a_table().changes_between(HOPPER, date(2025, 7, 1), date(2025, 8, 1)) == []


def test_a_step_on_the_last_day_of_the_span_is_a_split():
    assert a_table().changes_between(HOPPER, date(2025, 6, 1), date(2025, 7, 1)) == [
        date(2025, 7, 1)
    ]


def test_a_backwards_span_is_refused():
    with pytest.raises(RateError):
        a_table().changes_between(HOPPER, date(2025, 8, 1), date(2025, 6, 1))


def test_the_table_lists_the_types_it_covers():
    assert a_table().types() == [HOPPER, BOX]


def test_the_table_counts_its_bands():
    assert len(a_table()) == 5
