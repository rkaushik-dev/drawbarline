import pytest

from drawbarline.clock import utc
from drawbarline.errors import EventError
from drawbarline.events.stream import EventLog
from drawbarline.reports.shop import (
    came_back,
    down_now,
    shop_report,
    shop_spans,
    turn_days,
)
from support import bad_ordered, delivered, received, repaired

NOW = utc(2025, 1, 20, 8)


def a_log(*cars):
    log = EventLog()
    for mark in cars:
        received(log, mark, 5)
    return log


def test_a_car_never_bad_ordered_has_no_span():
    assert shop_spans(a_log("CN 400100"), NOW) == []


def test_a_car_still_down_shows_as_down():
    log = a_log("CN 400100")
    bad_ordered(log, "CN 400100", 10)
    found = shop_spans(log, NOW)
    assert len(found) == 1
    assert found[0].still_down is True


def test_a_car_still_down_counts_to_the_cut_off():
    log = a_log("CN 400100")
    bad_ordered(log, "CN 400100", 10)
    assert shop_spans(log, NOW)[0].days == 10.0


def test_a_car_that_came_back_counts_the_days_it_was_out():
    log = a_log("CN 400100")
    bad_ordered(log, "CN 400100", 10)
    repaired(log, "CN 400100", 14)
    found = shop_spans(log, NOW)[0]
    assert found.still_down is False
    assert found.days == 4.0


def test_a_span_carries_where_the_car_was_set_out():
    log = a_log("CN 400100")
    bad_ordered(log, "CN 400100", 10, station="PWR")
    assert shop_spans(log, NOW)[0].station == "PWR"


def test_a_span_prints_its_days_and_state():
    log = a_log("CN 400100")
    bad_ordered(log, "CN 400100", 10)
    repaired(log, "CN 400100", 14)
    assert str(shop_spans(log, NOW)[0]) == "CN 400100 4.0 days back"


def test_a_car_down_twice_makes_two_spans():
    log = a_log("CN 400100")
    bad_ordered(log, "CN 400100", 8)
    repaired(log, "CN 400100", 9)
    bad_ordered(log, "CN 400100", 12)
    repaired(log, "CN 400100", 15)
    assert len(shop_spans(log, NOW)) == 2


def test_the_spans_come_back_oldest_first():
    log = a_log("CN 400100", "CN 400101")
    bad_ordered(log, "CN 400101", 8)
    bad_ordered(log, "CN 400100", 12)
    assert [str(one.car) for one in shop_spans(log, NOW)] == ["CN 400101", "CN 400100"]


def test_a_repair_without_a_bad_order_is_refused():
    log = a_log("CN 400100")
    repaired(log, "CN 400100", 10)
    with pytest.raises(EventError):
        shop_spans(log, NOW)


def test_the_cars_down_and_the_cars_back_are_separated():
    log = a_log("CN 400100", "CN 400101")
    bad_ordered(log, "CN 400100", 10)
    bad_ordered(log, "CN 400101", 10)
    repaired(log, "CN 400101", 14)
    spans = shop_spans(log, NOW)
    assert len(down_now(spans)) == 1
    assert len(came_back(spans)) == 1


def test_the_turn_time_is_the_mean_of_the_cars_that_came_back():
    log = a_log("CN 400100", "CN 400101")
    bad_ordered(log, "CN 400100", 10)
    repaired(log, "CN 400100", 12)
    bad_ordered(log, "CN 400101", 10)
    repaired(log, "CN 400101", 14)
    assert turn_days(shop_spans(log, NOW)) == 3.0


def test_a_car_still_down_is_left_out_of_the_turn_time():
    log = a_log("CN 400100", "CN 400101")
    bad_ordered(log, "CN 400100", 10)
    repaired(log, "CN 400100", 12)
    bad_ordered(log, "CN 400101", 10)
    assert turn_days(shop_spans(log, NOW)) == 2.0


def test_nothing_back_gives_no_turn_time():
    log = a_log("CN 400100")
    bad_ordered(log, "CN 400100", 10)
    assert turn_days(shop_spans(log, NOW)) == 0.0


def test_the_report_opens_with_a_heading():
    lines = shop_report(a_log("CN 400100"), NOW)
    assert lines[0] == "BAD ORDER REPORT"


def test_the_report_has_both_sections():
    lines = shop_report(a_log("CN 400100"), NOW)
    assert "cars down" in lines
    assert "cars back in service" in lines


def test_the_report_lists_a_car_that_is_down():
    log = a_log("CN 400100")
    bad_ordered(log, "CN 400100", 10)
    lines = shop_report(log, NOW)
    assert any(line.startswith("CN 400100") for line in lines)
    assert any(line.startswith("1 down") for line in lines)


def test_the_report_carries_the_turn_time_on_the_total():
    log = a_log("CN 400100")
    bad_ordered(log, "CN 400100", 10)
    repaired(log, "CN 400100", 14)
    lines = shop_report(log, NOW)
    total = [line for line in lines if line.startswith("1 back")][0]
    assert total.strip().endswith("4.0")


def test_a_railroad_with_nothing_down_reports_none():
    lines = shop_report(a_log("CN 400100"), NOW)
    assert any(line.startswith("0 down") for line in lines)
    assert any(line.startswith("0 back") for line in lines)


def test_a_delivered_car_still_shows_its_repair_history():
    log = a_log("CN 400100")
    bad_ordered(log, "CN 400100", 10)
    repaired(log, "CN 400100", 12)
    delivered(log, "CN 400100", 14)
    assert len(shop_spans(log, NOW)) == 1


def test_every_line_fits_the_paper():
    log = a_log("CN 400100")
    bad_ordered(log, "CN 400100", 10)
    for line in shop_report(log, NOW):
        assert len(line) <= 80
