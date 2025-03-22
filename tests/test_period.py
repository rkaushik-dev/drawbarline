from datetime import date

import pytest

from drawbarline.clock import utc
from drawbarline.errors import SettlementError
from drawbarline.settlement.period import Period, PeriodRegister, Reopening


def test_a_period_prints_as_a_year_and_month():
    assert str(Period(2025, 1)) == "2025-01"


def test_a_period_reads_off_a_date():
    assert Period.of(date(2025, 3, 17)) == Period(2025, 3)


def test_a_month_that_is_not_a_month_is_refused():
    with pytest.raises(SettlementError):
        Period(2025, 13)


def test_a_year_we_would_never_settle_is_refused():
    with pytest.raises(SettlementError):
        Period(1700, 1)


def test_a_period_knows_its_first_and_last_day():
    period = Period(2025, 1)
    assert period.first_day == date(2025, 1, 1)
    assert period.last_day == date(2025, 1, 31)


def test_february_ends_when_february_ends():
    assert Period(2025, 2).last_day == date(2025, 2, 28)


def test_a_leap_february_has_its_extra_day():
    assert Period(2024, 2).last_day == date(2024, 2, 29)


def test_december_runs_into_january():
    assert Period(2025, 12).following == Period(2026, 1)


def test_january_runs_back_into_december():
    assert Period(2025, 1).preceding == Period(2024, 12)


def test_the_window_starts_at_midnight_on_the_first():
    assert Period(2025, 1).window.start == utc(2025, 1, 1, 6)


def test_the_window_ends_where_the_next_one_starts():
    assert Period(2025, 1).window.end == Period(2025, 2).window.start


def test_a_summer_window_moves_with_the_clocks():
    assert Period(2025, 7).window.start == utc(2025, 7, 1, 5)


def test_a_period_covers_its_own_days():
    period = Period(2025, 1)
    assert period.covers(date(2025, 1, 1)) is True
    assert period.covers(date(2025, 1, 31)) is True
    assert period.covers(date(2025, 2, 1)) is False


def test_periods_sort_in_order():
    assert sorted([Period(2025, 3), Period(2024, 12), Period(2025, 1)]) == [
        Period(2024, 12),
        Period(2025, 1),
        Period(2025, 3),
    ]


def test_a_month_starts_open():
    assert PeriodRegister().is_open(Period(2025, 1)) is True


def test_closing_a_month_shuts_it():
    register = PeriodRegister()
    register.close(Period(2025, 1))
    assert register.is_open(Period(2025, 1)) is False


def test_closing_a_month_twice_is_refused():
    register = PeriodRegister()
    register.close(Period(2025, 1))
    with pytest.raises(SettlementError):
        register.close(Period(2025, 1))


def test_posting_to_an_open_month_is_allowed():
    assert PeriodRegister().require_open(Period(2025, 1)) == Period(2025, 1)


def test_posting_to_a_closed_month_is_refused():
    register = PeriodRegister()
    register.close(Period(2025, 1))
    with pytest.raises(SettlementError):
        register.require_open(Period(2025, 1))


def test_reopening_a_closed_month_opens_it():
    register = PeriodRegister()
    register.close(Period(2025, 1))
    register.reopen(Period(2025, 1), date(2025, 3, 4), "cn sent a corrected sheet")
    assert register.is_open(Period(2025, 1)) is True


def test_reopening_a_month_that_is_open_is_refused():
    with pytest.raises(SettlementError):
        PeriodRegister().reopen(Period(2025, 1), date(2025, 3, 4), "no reason")


def test_reopening_without_a_reason_is_refused():
    register = PeriodRegister()
    register.close(Period(2025, 1))
    with pytest.raises(SettlementError):
        register.reopen(Period(2025, 1), date(2025, 3, 4), "   ")


def test_a_reopening_stays_on_file_after_the_month_closes_again():
    register = PeriodRegister()
    register.close(Period(2025, 1))
    register.reopen(Period(2025, 1), date(2025, 3, 4), "cn sent a corrected sheet")
    register.close(Period(2025, 1))
    assert register.was_ever_reopened(Period(2025, 1)) is True


def test_a_month_never_reopened_says_so():
    register = PeriodRegister()
    register.close(Period(2025, 1))
    assert register.was_ever_reopened(Period(2025, 1)) is False


def test_the_reopenings_for_one_month_come_back_alone():
    register = PeriodRegister()
    for period in (Period(2025, 1), Period(2025, 2)):
        register.close(period)
        register.reopen(period, date(2025, 4, 1), "late sheet")
    assert len(register.reopenings(Period(2025, 1))) == 1
    assert len(register.reopenings()) == 2


def test_the_closed_months_come_back_in_order():
    register = PeriodRegister()
    register.close(Period(2025, 3))
    register.close(Period(2025, 1))
    assert register.closed_periods() == [Period(2025, 1), Period(2025, 3)]


def test_a_reopening_carries_its_reason():
    found = Reopening(Period(2025, 1), date(2025, 3, 4), "corrected interchange sheet")
    assert found.reason == "corrected interchange sheet"
