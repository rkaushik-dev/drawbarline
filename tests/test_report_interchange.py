from drawbarline.clock import Interval, utc
from drawbarline.events.kinds import EventKind
from drawbarline.events.record import Event
from drawbarline.events.stream import EventLog
from drawbarline.marks import CarMark
from drawbarline.reports.interchange import (
    balance_report,
    crossings,
    on_hand,
    on_hand_report,
)
from support import bad_ordered, delivered, held, placed, received

JANUARY = Interval(utc(2025, 1, 1, 6), utc(2025, 2, 1, 6))
NOW = utc(2025, 1, 20, 12)


def a_log():
    log = EventLog()
    received(log, "CN 400100", 6)
    delivered(log, "CN 400100", 16)
    received(log, "CN 400101", 8)
    received(log, "DBL 104207", 9)
    return log


def test_a_junction_and_road_pair_counts_both_ways():
    found = crossings(a_log(), JANUARY)
    assert len(found) == 2
    taken = [one for one in found if one.junction == "ESC"][0]
    assert taken.received == 3
    assert taken.delivered == 0


def test_a_delivery_counts_at_the_junction_it_happened_at():
    found = crossings(a_log(), JANUARY)
    given = [one for one in found if one.junction == "MRN"][0]
    assert given.delivered == 1
    assert given.received == 0


def test_the_net_grows_when_we_take_more_than_we_give():
    found = crossings(a_log(), JANUARY)
    assert sum(one.net for one in found) == 2


def test_a_log_with_no_interchange_crosses_nothing():
    assert crossings(EventLog(), JANUARY) == []


def test_movements_are_not_crossings():
    log = EventLog()
    log.record(
        Event(EventKind.MOVED, CarMark.parse("CN 400100"), utc(2025, 1, 6, 18), "PWR", tenths=336)
    )
    assert crossings(log, JANUARY) == []


def test_the_balance_report_opens_with_a_heading():
    lines = balance_report(a_log(), JANUARY, "2025-01")
    assert lines[0] == "INTERCHANGE BALANCE"


def test_the_balance_report_has_a_line_a_junction():
    lines = balance_report(a_log(), JANUARY, "2025-01")
    assert any(line.startswith("ESC") for line in lines)
    assert any(line.startswith("MRN") for line in lines)


def test_the_balance_report_totals_both_directions():
    lines = balance_report(a_log(), JANUARY, "2025-01")
    total = [line for line in lines if line.startswith("2 junctions")][0]
    assert total.split()[-3:] == ["3", "1", "2"]


def test_an_empty_month_balances_at_nothing():
    lines = balance_report(EventLog(), JANUARY, "2025-01")
    assert any(line.startswith("0 junctions") for line in lines)


def test_a_car_taken_and_given_back_is_not_on_hand():
    found = on_hand(a_log(), NOW)
    assert CarMark.parse("CN 400100") not in [one.car for one in found]


def test_a_car_still_here_is_on_hand():
    found = on_hand(a_log(), NOW)
    assert [str(one.car) for one in found] == ["CN 400101", "DBL 104207"]


def test_cars_on_hand_come_back_oldest_first():
    found = on_hand(a_log(), NOW)
    assert found[0].since < found[1].since


def test_a_car_on_hand_counts_its_hours():
    found = on_hand(a_log(), NOW)
    assert found[0].hundredth_hours == 28800


def test_a_placed_car_reports_the_track_it_is_on():
    log = a_log()
    placed(log, "CN 400101", 10)
    found = [one for one in on_hand(log, NOW) if str(one.car) == "CN 400101"][0]
    assert found.industry == "ESCPAP1"


def test_a_bad_ordered_car_says_so():
    log = a_log()
    bad_ordered(log, "CN 400101", 10)
    found = [one for one in on_hand(log, NOW) if str(one.car) == "CN 400101"][0]
    assert found.bad_ordered is True


def test_a_held_car_says_so():
    log = a_log()
    held(log, "CN 400101", 10)
    found = [one for one in on_hand(log, NOW) if str(one.car) == "CN 400101"][0]
    assert found.held is True


def test_the_report_prints_the_reason_a_car_is_stuck():
    log = a_log()
    bad_ordered(log, "CN 400101", 10)
    lines = on_hand_report(log, NOW)
    row = [line for line in lines if line.startswith("CN 400101")][0]
    assert "bad order" in row


def test_the_report_totals_the_hours_on_hand():
    lines = on_hand_report(a_log(), NOW)
    assert any(line.startswith("2 cars") for line in lines)


def test_a_railroad_with_nothing_on_hand_says_none():
    lines = on_hand_report(EventLog(), NOW)
    assert any(line.startswith("0 cars") for line in lines)


def test_the_report_names_the_moment_it_was_taken():
    lines = on_hand_report(a_log(), NOW)
    assert lines[1] == "2025-01-20 12:00Z"


def test_no_line_runs_past_the_paper():
    for line in on_hand_report(a_log(), NOW):
        assert len(line) <= 80


def test_a_moment_before_a_car_arrived_leaves_it_out():
    assert on_hand(a_log(), utc(2025, 1, 7, 12)) != on_hand(a_log(), NOW)
