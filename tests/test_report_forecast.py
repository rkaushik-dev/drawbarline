import pytest

from drawbarline.carhire.rates import RateError
from drawbarline.clock import utc
from drawbarline.events.stream import EventLog
from drawbarline.marks import ReportingMark
from drawbarline.money import Money
from drawbarline.reports.forecast import (
    a_day,
    by_road,
    days_until,
    forecast_report,
    over,
    standing_cars,
)
from support import HOME, a_fleet, a_rate_table, delivered, received

NOW = utc(2025, 1, 10, 12)
CN = ReportingMark("CN")
ELS = ReportingMark("ELS")


def a_world(*marks):
    register = a_fleet(*marks)
    log = EventLog()
    for mark in marks:
        received(log, mark, 6)
    return register, log


def standing(*marks):
    register, log = a_world(*marks)
    return standing_cars(register, log, a_rate_table(), NOW, HOME)


def test_a_foreign_car_on_hand_is_standing():
    found = standing("CN 400100")
    assert [str(one.car) for one in found] == ["CN 400100"]


def test_our_own_car_costs_us_nothing():
    assert standing("DBL 104207") == []


def test_a_private_car_is_left_out_because_it_earns_no_hours():
    assert standing("PLMX 9001") == []


def test_a_delivered_car_is_not_standing():
    register, log = a_world("CN 400100")
    delivered(log, "CN 400100", 8)
    assert standing_cars(register, log, a_rate_table(), NOW, HOME) == []


def test_a_car_with_no_equipment_record_is_left_out():
    register = a_fleet()
    log = EventLog()
    received(log, "CN 400100", 6)
    assert standing_cars(register, log, a_rate_table(), NOW, HOME) == []


def test_a_car_with_no_rate_on_file_is_left_out():
    from drawbarline.carhire.rates import RateTable

    register, log = a_world("CN 400100")
    assert standing_cars(register, log, RateTable(), NOW, HOME) == []


def test_a_day_is_the_hourly_rate_over_twenty_four_hours():
    found = standing("CN 400100")[0]
    assert found.a_day == Money(round(4630 * 24 / 10))


def test_a_car_prints_where_it_is_and_what_it_costs():
    found = standing("CN 400100")[0]
    assert str(found) == "CN 400100 at ESC $111.12 a day"


def test_the_whole_railroad_adds_up():
    found = standing("CN 400100", "CN 400101")
    assert a_day(found) == Money(2 * 11112)


def test_nothing_standing_costs_nothing():
    assert a_day([]) == Money(0)


def test_the_burn_gathers_by_road():
    found = standing("CN 400100", "CN 400101")
    assert by_road(found) == {CN: Money(22224)}


def test_a_run_of_days_multiplies_the_burn():
    found = standing("CN 400100")
    assert over(found, 7) == Money(11112 * 7)


def test_a_run_of_no_days_costs_nothing():
    assert over(standing("CN 400100"), 0) == Money(0)


def test_a_backwards_forecast_is_refused():
    with pytest.raises(RateError):
        over(standing("CN 400100"), -1)


def test_the_days_to_reach_an_amount():
    found = standing("CN 400100")
    assert days_until(found, Money(50000)) == 5


def test_an_amount_already_reached_takes_no_days():
    assert days_until(standing("CN 400100"), Money(0)) == 0


def test_nothing_accruing_never_reaches_an_amount():
    with pytest.raises(RateError):
        days_until([], Money(100))


def test_the_report_opens_with_a_heading():
    lines = forecast_report(standing("CN 400100"), NOW)
    assert lines[0] == "CAR HIRE FORECAST"
    assert "nothing moves" in lines[1]


def test_the_report_has_a_line_a_road():
    lines = forecast_report(standing("CN 400100"), NOW)
    assert any(line.startswith("CN") for line in lines)


def test_the_report_totals_every_road():
    lines = forecast_report(standing("CN 400100", "CN 400101"), NOW)
    total = [line for line in lines if line.startswith("all roads")][0]
    assert "$222.24" in total


def test_the_report_runs_the_days_out():
    lines = forecast_report(standing("CN 400100"), NOW, days=7)
    assert any("$777.84" in line for line in lines)


def test_a_railroad_with_nothing_standing_reports_nothing():
    lines = forecast_report([], NOW)
    assert any(line.startswith("all roads") for line in lines)


def test_every_line_fits_the_paper():
    for line in forecast_report(standing("CN 400100"), NOW):
        assert len(line) <= 80
