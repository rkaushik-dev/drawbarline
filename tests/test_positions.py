from drawbarline.clock import utc
from drawbarline.events.positions import (
    movable,
    on_line,
    on_track,
    placed,
    positions,
    state_of,
    unplaced,
)
from drawbarline.events.stream import EventLog
from drawbarline.marks import CarMark
from support import bad_ordered, delivered, held, pulled, received, released
from support import placed as place

NOW = utc(2025, 1, 20, 12)


def a_log():
    log = EventLog()
    received(log, "CN 400100", 6)
    received(log, "CN 400101", 6)
    received(log, "CN 400102", 6)
    place(log, "CN 400100", 7)
    delivered(log, "CN 400102", 16)
    return log


def test_a_walk_covers_every_car_in_the_log():
    found = positions(a_log(), NOW)
    assert len(found) == 3


def test_a_car_still_here_is_on_line():
    found = positions(a_log(), NOW)
    assert [str(one) for one in on_line(found)] == ["CN 400100", "CN 400101"]


def test_a_delivered_car_is_not_on_line():
    found = positions(a_log(), NOW)
    assert CarMark.parse("CN 400102") not in on_line(found)


def test_a_placed_car_shows_on_its_track():
    found = positions(a_log(), NOW)
    assert [str(one) for one in on_track(found, "ESCPAP1")] == ["CN 400100"]


def test_a_track_with_nothing_on_it_is_empty():
    found = positions(a_log(), NOW)
    assert on_track(found, "PWRGRN1") == []


def test_the_placed_cars_come_back_together():
    found = positions(a_log(), NOW)
    assert [str(one) for one in placed(found)] == ["CN 400100"]


def test_the_unplaced_cars_are_the_rest_of_what_is_here():
    found = positions(a_log(), NOW)
    assert [str(one) for one in unplaced(found)] == ["CN 400101"]


def test_a_bad_ordered_car_is_not_movable():
    log = a_log()
    bad_ordered(log, "CN 400101", 8)
    found = positions(log, NOW)
    assert CarMark.parse("CN 400101") not in movable(found)


def test_a_held_car_is_not_movable():
    log = a_log()
    held(log, "CN 400101", 8)
    found = positions(log, NOW)
    assert CarMark.parse("CN 400101") not in movable(found)


def test_one_car_state_reads_back():
    found = positions(a_log(), NOW)
    assert state_of(found, CarMark.parse("CN 400100")).industry == "ESCPAP1"


def test_a_car_nobody_has_recorded_reads_as_off_line():
    found = positions(a_log(), NOW)
    assert state_of(found, CarMark.parse("WC 000001")).on_line is False


def test_a_walk_to_an_earlier_moment_sees_less():
    found = positions(a_log(), utc(2025, 1, 6, 18))
    assert placed(found) == []


def test_a_walk_before_anything_happened_sees_nothing_on_line():
    assert on_line(positions(a_log(), utc(2025, 1, 1))) == []


def test_an_empty_log_walks_to_nothing():
    assert positions(EventLog(), NOW) == {}


def test_the_walk_agrees_with_the_switch_list():
    from drawbarline.switching import cars_on_track

    log = a_log()
    assert cars_on_track(log, "ESCPAP1", NOW) == on_track(positions(log, NOW), "ESCPAP1")


def test_the_walk_agrees_with_the_on_hand_report():
    from drawbarline.reports.interchange import on_hand

    log = a_log()
    assert [one.car for one in on_hand(log, NOW)] == on_line(positions(log, NOW))


def test_a_released_car_is_still_placed_until_it_is_pulled():
    log = a_log()
    released(log, "CN 400100", 10)
    assert placed(positions(log, NOW)) == [CarMark.parse("CN 400100")]


def test_a_pulled_car_leaves_the_track():
    log = a_log()
    released(log, "CN 400100", 10)
    pulled(log, "CN 400100", 11)
    assert placed(positions(log, NOW)) == []
