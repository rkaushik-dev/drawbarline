from datetime import date

import pytest

from drawbarline.days import (
    CalendarError,
    add_chargeable_days,
    chargeable_days,
    holiday_on,
    holidays,
    is_chargeable,
    is_holiday,
    is_weekend,
    next_chargeable,
    observed,
)


def test_six_holidays_a_year():
    assert len(holidays(2025)) == 6


def test_holidays_come_back_in_the_order_they_are_kept():
    kept = [h.kept for h in holidays(2025)]
    assert kept == sorted(kept)


def test_memorial_day_is_the_last_monday_in_may():
    assert holiday_on(date(2025, 5, 26)).name == "memorial day"


def test_labor_day_is_the_first_monday_in_september():
    assert holiday_on(date(2025, 9, 1)).name == "labor day"


def test_thanksgiving_is_the_fourth_thursday_in_november():
    assert holiday_on(date(2025, 11, 27)).name == "thanksgiving"


def test_thanksgiving_moves_with_the_year():
    assert holiday_on(date(2026, 11, 26)).name == "thanksgiving"


def test_memorial_day_in_a_may_that_ends_on_a_monday():
    assert holiday_on(date(2027, 5, 31)).name == "memorial day"


def test_a_saturday_holiday_is_kept_the_friday_before():
    assert observed(date(2026, 7, 4)) == date(2026, 7, 3)


def test_a_sunday_holiday_is_kept_the_monday_after():
    assert observed(date(2022, 12, 25)) == date(2022, 12, 26)


def test_a_weekday_holiday_stays_put():
    assert observed(date(2025, 12, 25)) == date(2025, 12, 25)


def test_a_moved_holiday_says_so():
    fourth = [h for h in holidays(2026) if h.name == "independence day"][0]
    assert fourth.moved is True
    assert fourth.falls == date(2026, 7, 4)
    assert fourth.kept == date(2026, 7, 3)


def test_a_holiday_that_did_not_move_says_so():
    fourth = [h for h in holidays(2025) if h.name == "independence day"][0]
    assert fourth.moved is False


def test_an_ordinary_day_is_no_holiday():
    assert holiday_on(date(2025, 3, 12)) is None
    assert is_holiday(date(2025, 3, 12)) is False


def test_a_year_we_would_never_settle_is_refused():
    with pytest.raises(CalendarError):
        holidays(1700)


def test_saturdays_and_sundays_are_the_weekend():
    assert is_weekend(date(2025, 3, 15)) is True
    assert is_weekend(date(2025, 3, 16)) is True
    assert is_weekend(date(2025, 3, 17)) is False


def test_a_weekday_that_is_not_a_holiday_is_chargeable():
    assert is_chargeable(date(2025, 3, 12)) is True


def test_a_holiday_is_not_chargeable():
    assert is_chargeable(date(2025, 12, 25)) is False


def test_a_weekend_is_not_chargeable():
    assert is_chargeable(date(2025, 3, 15)) is False


def test_an_ordinary_week_holds_five_chargeable_days():
    assert chargeable_days(date(2025, 3, 10), date(2025, 3, 16)) == 5


def test_a_week_with_a_holiday_holds_four():
    assert chargeable_days(date(2025, 12, 22), date(2025, 12, 28)) == 4


def test_one_chargeable_day_counts_itself():
    assert chargeable_days(date(2025, 3, 12), date(2025, 3, 12)) == 1


def test_a_span_of_one_weekend_day_counts_nothing():
    assert chargeable_days(date(2025, 3, 15), date(2025, 3, 15)) == 0


def test_a_span_that_runs_backwards_is_refused():
    with pytest.raises(CalendarError):
        chargeable_days(date(2025, 3, 16), date(2025, 3, 10))


def test_the_next_chargeable_day_after_a_friday_is_monday():
    assert next_chargeable(date(2025, 3, 14)) == date(2025, 3, 17)


def test_the_next_chargeable_day_skips_a_holiday_weekend():
    assert next_chargeable(date(2025, 8, 29)) == date(2025, 9, 2)


def test_free_time_walks_forward_over_the_weekend():
    assert add_chargeable_days(date(2025, 3, 13), 2) == date(2025, 3, 17)


def test_no_free_time_expires_on_the_day_it_starts():
    assert add_chargeable_days(date(2025, 3, 13), 0) == date(2025, 3, 13)


def test_free_time_does_not_run_backwards():
    with pytest.raises(CalendarError):
        add_chargeable_days(date(2025, 3, 13), -1)


def test_free_time_can_cross_a_year_end():
    assert add_chargeable_days(date(2025, 12, 30), 3) == date(2026, 1, 5)
