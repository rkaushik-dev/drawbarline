from datetime import date

import pytest

from drawbarline.clock import Interval, utc
from drawbarline.events.stream import EventLog
from drawbarline.marks import CarMark
from drawbarline.reports.fleet import (
    age_profile,
    age_report,
    cycle_of,
    cycles,
    expiring_within,
    utilisation_report,
)
from support import a_car, a_fleet, delivered, moved, placed, pulled, received, released

JANUARY = Interval(utc(2025, 1, 1, 6), utc(2025, 2, 1, 6))
CAR = CarMark.parse("CN 400100")


def a_log():
    log = EventLog()
    received(log, "CN 400100", 6)
    moved(log, "CN 400100", 6, 336)
    placed(log, "CN 400100", 7)
    released(log, "CN 400100", 14)
    pulled(log, "CN 400100", 15)
    delivered(log, "CN 400100", 16)
    return log


def test_a_car_that_came_and_went_shows_one_visit():
    found = cycle_of(a_log(), CAR, JANUARY)
    assert found.visits == 1


def test_the_turn_time_is_the_whole_stay():
    found = cycle_of(a_log(), CAR, JANUARY)
    assert found.on_line_minutes == 10 * 24 * 60


def test_the_standing_time_is_the_part_on_the_customer_track():
    found = cycle_of(a_log(), CAR, JANUARY)
    assert found.on_track_minutes == 8 * 24 * 60 + 2 * 60


def test_the_moving_time_is_what_is_left():
    found = cycle_of(a_log(), CAR, JANUARY)
    assert found.moving_minutes == found.on_line_minutes - found.on_track_minutes


def test_the_turn_time_reads_in_hours():
    assert cycle_of(a_log(), CAR, JANUARY).turn_hundredths == 24000


def test_the_miles_come_off_the_movements():
    assert cycle_of(a_log(), CAR, JANUARY).tenths_run == 336


def test_a_car_that_never_came_has_no_cycle():
    assert cycle_of(EventLog(), CAR, JANUARY).on_line_minutes == 0


def test_cars_with_nothing_in_the_window_are_left_out():
    assert cycles(EventLog(), JANUARY) == []


def test_the_cycles_cover_every_car_that_moved():
    assert [one.car for one in cycles(a_log(), JANUARY)] == [CAR]


def test_the_report_opens_with_a_heading():
    lines = utilisation_report(a_log(), JANUARY, "2025-01")
    assert lines[0] == "FLEET UTILISATION"


def test_the_report_has_a_line_a_car():
    lines = utilisation_report(a_log(), JANUARY, "2025-01")
    assert any(line.startswith("CN 400100") for line in lines)


def test_the_report_prints_the_turn_and_standing_hours():
    lines = utilisation_report(a_log(), JANUARY, "2025-01")
    row = [line for line in lines if line.startswith("CN 400100")][0]
    assert "240.00" in row
    assert "194.00" in row


def test_the_report_totals_the_cars():
    lines = utilisation_report(a_log(), JANUARY, "2025-01")
    assert any(line.startswith("1 cars") for line in lines)


def test_an_empty_log_reports_no_cars():
    lines = utilisation_report(EventLog(), JANUARY, "2025-01")
    assert any(line.startswith("0 cars") for line in lines)


def test_the_age_profile_counts_by_decade():
    register = a_fleet()
    register.add(a_car("DBL 104207", built=date(2020, 6, 1)))
    register.add(a_car("DBL 104208", built=date(2010, 6, 1)))
    register.add(a_car("CN 400100", built=date(2009, 6, 1)))
    assert age_profile(register, date(2025, 7, 1)) == {"0 to 9": 1, "10 to 19": 2}


def test_a_retired_car_is_left_out_of_the_profile():
    register = a_fleet()
    register.add(a_car("DBL 104207", built=date(2020, 6, 1)))
    register.retire(CarMark.parse("DBL 104207"), date(2024, 12, 31))
    assert age_profile(register, date(2025, 7, 1)) == {}


def test_the_cars_running_out_of_life_come_back():
    register = a_fleet()
    register.add(a_car("DBL 104207", built=date(1978, 6, 1)))
    register.add(a_car("DBL 104208", built=date(2020, 6, 1)))
    found = expiring_within(register, date(2025, 7, 1), 5)
    assert [str(one) for one in found] == ["DBL 104207"]


def test_nothing_expiring_comes_back_empty():
    register = a_fleet()
    register.add(a_car("DBL 104208", built=date(2020, 6, 1)))
    assert expiring_within(register, date(2025, 7, 1), 5) == []


def test_the_age_report_carries_the_profile_and_the_list():
    register = a_fleet()
    register.add(a_car("DBL 104207", built=date(1978, 6, 1)))
    register.add(a_car("DBL 104208", built=date(2020, 6, 1)))
    lines = age_report(register, date(2025, 7, 1))
    assert lines[0] == "FLEET AGE PROFILE"
    assert any(line.startswith("all cars") for line in lines)
    assert any("within 5 years: 1" in line for line in lines)
    assert any(line.strip() == "DBL 104207" for line in lines)


def test_the_age_report_on_an_empty_fleet_totals_nothing():
    lines = age_report(a_fleet(), date(2025, 7, 1))
    total = [line for line in lines if line.startswith("all cars")][0]
    assert total.split()[-1] == "0"


@pytest.mark.parametrize("line", utilisation_report(a_log(), JANUARY, "2025-01"))
def test_no_line_runs_past_the_paper(line):
    assert len(line) <= 80
