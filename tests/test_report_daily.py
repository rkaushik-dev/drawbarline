from datetime import date, datetime

from drawbarline.clock import utc
from drawbarline.distribution import Distribution
from drawbarline.embargo import Embargo, EmbargoRegister
from drawbarline.events.stream import EventLog
from drawbarline.reports.daily import counts_in, daily_summary, window_of
from support import bad_ordered, delivered, placed, pulled, received, released

DAY = date(2025, 1, 14)
AT = utc(2025, 1, 14, 8)


def a_log():
    log = EventLog()
    received(log, "CN 400100", 13, hour=9)
    received(log, "CN 400101", 13, hour=10)
    received(log, "CN 400102", 13, hour=11)
    delivered(log, "CN 400102", 13, hour=12)
    placed(log, "CN 400100", 13)
    return log


def some_embargoes():
    register = EmbargoRegister()
    register.issue(Embargo(101, date(2025, 1, 6), "bridge out", station="HRM"))
    return register


def test_the_window_is_the_day_before_the_moment():
    found = window_of(DAY, AT)
    assert found.end == datetime.combine(DAY, AT.timetz())
    assert found.minutes == 24 * 60


def test_the_counts_split_by_kind():
    found = counts_in(a_log(), window_of(DAY, AT))
    assert found.received == 3
    assert found.delivered == 1
    assert found.placed == 1


def test_the_net_change_is_what_we_took_less_what_we_gave():
    found = counts_in(a_log(), window_of(DAY, AT))
    assert found.net_cars == 2


def test_the_worked_count_is_placements_and_pulls():
    log = a_log()
    released(log, "CN 400100", 13)
    pulled(log, "CN 400100", 13)
    found = counts_in(log, window_of(DAY, AT))
    assert found.worked == 2


def test_an_empty_day_counts_nothing():
    found = counts_in(EventLog(), window_of(DAY, AT))
    assert found.received == 0
    assert found.net_cars == 0


def test_an_event_outside_the_window_is_not_counted():
    log = EventLog()
    received(log, "CN 400100", 6)
    assert counts_in(log, window_of(DAY, AT)).received == 0


def test_the_sheet_opens_with_a_heading():
    lines = daily_summary(a_log(), EmbargoRegister(), Distribution(), DAY, AT)
    assert lines[0] == "MORNING SHEET"
    assert lines[1] == "2025-01-14"


def test_the_sheet_carries_the_movement_table():
    lines = daily_summary(a_log(), EmbargoRegister(), Distribution(), DAY, AT)
    assert any(line.startswith("received in interchange") for line in lines)
    assert any(line.startswith("net change on line") for line in lines)


def test_the_sheet_counts_the_cars_on_hand():
    lines = daily_summary(a_log(), EmbargoRegister(), Distribution(), DAY, AT)
    assert any("cars on hand          2" in line for line in lines)


def test_the_sheet_counts_the_bad_orders():
    log = a_log()
    bad_ordered(log, "CN 400101", 13, hour=14)
    lines = daily_summary(log, EmbargoRegister(), Distribution(), DAY, AT)
    assert any("cars bad ordered      1" in line for line in lines)


def test_a_bad_ordered_car_is_named_at_the_bottom():
    log = a_log()
    bad_ordered(log, "CN 400101", 13, hour=14)
    lines = daily_summary(log, EmbargoRegister(), Distribution(), DAY, AT)
    assert "bad order" in lines
    assert any("CN 400101" in line for line in lines)


def test_the_sheet_counts_the_live_embargoes():
    lines = daily_summary(a_log(), some_embargoes(), Distribution(), DAY, AT)
    assert any("embargoes in force    1" in line for line in lines)


def test_a_live_embargo_is_named_at_the_bottom():
    lines = daily_summary(a_log(), some_embargoes(), Distribution(), DAY, AT)
    assert any("embargo 101 at HRM" in line for line in lines)


def test_a_railroad_with_no_embargoes_names_none():
    lines = daily_summary(a_log(), EmbargoRegister(), Distribution(), DAY, AT)
    assert "embargoes" not in lines


def test_the_sheet_carries_the_car_orders():
    found = Distribution()
    found.short[1] = 3
    lines = daily_summary(a_log(), EmbargoRegister(), found, DAY, AT)
    assert any("car orders short      3" in line for line in lines)


def test_an_empty_railroad_still_produces_a_sheet():
    lines = daily_summary(EventLog(), EmbargoRegister(), Distribution(), DAY, AT)
    assert lines[0] == "MORNING SHEET"
    assert any("cars on hand          0" in line for line in lines)


def test_every_line_fits_the_paper():
    lines = daily_summary(a_log(), some_embargoes(), Distribution(), DAY, AT)
    for line in lines:
        assert len(line) <= 80
