from datetime import date

import pytest

from drawbarline.clock import utc
from drawbarline.customers import CustomerRegister, Industry, Shipper
from drawbarline.events.kinds import EventKind
from drawbarline.events.record import Event
from drawbarline.events.stream import EventLog
from drawbarline.marks import CarMark
from drawbarline.switching import MoveKind, SwitchError, cars_on_track, plan
from support import bad_ordered, held, placed, received, released

MILL = Shipper("ESCPAP", "Escanaba Paper")
TUESDAY = date(2025, 1, 14)
SATURDAY = date(2025, 1, 18)
NOW = utc(2025, 1, 14, 12)


def customers(spots=3):
    register = CustomerRegister()
    register.add_shipper(MILL)
    register.add_industry(Industry("ESCPAP1", "mill house", "ESC", MILL, spots))
    register.add_industry(
        Industry("ESCPAP2", "mill north", "ESC", MILL, 2, frozenset({"mon", "wed"}))
    )
    register.add_industry(Industry("PWRGRN1", "elevator", "PWR", MILL, 20))
    return register


def a_log(*marks):
    log = EventLog()
    for at, mark in enumerate(marks, start=1):
        received(log, mark, at)
    return log


def test_a_track_with_nothing_on_it_holds_nothing():
    assert cars_on_track(a_log("CN 400100"), "ESCPAP1", NOW) == []


def test_a_placed_car_stands_on_its_track():
    log = a_log("CN 400100")
    placed(log, "CN 400100", 5)
    assert cars_on_track(log, "ESCPAP1", NOW) == [CarMark.parse("CN 400100")]


def test_a_released_car_is_pulled():
    log = a_log("CN 400100")
    placed(log, "CN 400100", 5)
    released(log, "CN 400100", 8)
    found = plan(log, customers(), "ESC", TUESDAY, NOW, [])
    assert [one.kind for one in found.moves] == [MoveKind.PULL]


def test_a_car_the_customer_has_not_released_stays_put():
    log = a_log("CN 400100")
    placed(log, "CN 400100", 5)
    assert plan(log, customers(), "ESC", TUESDAY, NOW, []).moves == ()


def test_a_car_is_placed_when_there_is_room():
    log = a_log("CN 400100")
    found = plan(log, customers(), "ESC", TUESDAY, NOW, [(CarMark.parse("CN 400100"), "ESCPAP1")])
    assert [one.kind for one in found.moves] == [MoveKind.PLACE]


def test_a_full_track_takes_a_constructive_placement():
    log = a_log("CN 400100", "CN 400101", "CN 400102")
    for mark in ("CN 400100", "CN 400101"):
        placed(log, mark, 5)
    found = plan(
        log,
        customers(spots=2),
        "ESC",
        TUESDAY,
        NOW,
        [(CarMark.parse("CN 400102"), "ESCPAP1")],
    )
    assert [one.kind for one in found.moves] == [MoveKind.CONSTRUCTIVE]


def test_a_constructive_placement_says_how_many_were_on_hand():
    log = a_log("CN 400100", "CN 400101", "CN 400102")
    for mark in ("CN 400100", "CN 400101"):
        placed(log, mark, 5)
    found = plan(
        log,
        customers(spots=2),
        "ESC",
        TUESDAY,
        NOW,
        [(CarMark.parse("CN 400102"), "ESCPAP1")],
    )
    assert found.moves[0].note == "2 on hand"


def test_a_pull_makes_room_for_a_placement():
    log = a_log("CN 400100", "CN 400101", "CN 400102")
    for mark in ("CN 400100", "CN 400101"):
        placed(log, mark, 5)
    released(log, "CN 400100", 8)
    found = plan(
        log,
        customers(spots=2),
        "ESC",
        TUESDAY,
        NOW,
        [(CarMark.parse("CN 400102"), "ESCPAP1")],
    )
    assert [one.kind for one in found.moves] == [MoveKind.PULL, MoveKind.PLACE]


def test_a_track_we_do_not_switch_today_is_not_worked():
    log = a_log("CN 400100")
    found = plan(log, customers(), "ESC", TUESDAY, NOW, [(CarMark.parse("CN 400100"), "ESCPAP2")])
    assert [one.kind for one in found.moves] == [MoveKind.NOT_SWITCHED]


def test_a_released_car_on_a_track_we_do_not_switch_stays():
    log = a_log("CN 400100")
    log.record(
        Event(
            EventKind.PLACED,
            CarMark.parse("CN 400100"),
            utc(2025, 1, 5, 20),
            "ESC",
            industry="ESCPAP2",
        )
    )
    assert plan(log, customers(), "ESC", TUESDAY, NOW, []).moves == ()


def test_a_bad_ordered_car_does_not_move():
    log = a_log("CN 400100")
    bad_ordered(log, "CN 400100", 6)
    found = plan(log, customers(), "ESC", TUESDAY, NOW, [(CarMark.parse("CN 400100"), "ESCPAP1")])
    assert found.moves[0].kind is MoveKind.STOPPED
    assert found.moves[0].note == "bad order"


def test_a_held_car_does_not_move():
    log = a_log("CN 400100")
    held(log, "CN 400100", 6)
    found = plan(log, customers(), "ESC", TUESDAY, NOW, [(CarMark.parse("CN 400100"), "ESCPAP1")])
    assert found.moves[0].note == "held"


def test_a_car_both_bad_ordered_and_held_says_both():
    log = a_log("CN 400100")
    bad_ordered(log, "CN 400100", 6)
    held(log, "CN 400100", 7)
    found = plan(log, customers(), "ESC", TUESDAY, NOW, [(CarMark.parse("CN 400100"), "ESCPAP1")])
    assert found.moves[0].note == "bad order and held"


def test_a_bad_ordered_car_on_a_track_is_not_pulled():
    log = a_log("CN 400100")
    placed(log, "CN 400100", 5)
    released(log, "CN 400100", 6)
    bad_ordered(log, "CN 400100", 7)
    found = plan(log, customers(), "ESC", TUESDAY, NOW, [])
    assert found.moves[0].kind is MoveKind.STOPPED


def test_a_car_not_on_our_rail_cannot_be_ordered():
    with pytest.raises(SwitchError):
        plan(
            EventLog(),
            customers(),
            "ESC",
            TUESDAY,
            NOW,
            [(CarMark.parse("CN 400100"), "ESCPAP1")],
        )


def test_a_track_at_another_station_is_refused():
    log = a_log("CN 400100")
    with pytest.raises(SwitchError):
        plan(log, customers(), "ESC", TUESDAY, NOW, [(CarMark.parse("CN 400100"), "PWRGRN1")])


def test_the_list_counts_what_the_crew_works():
    log = a_log("CN 400100", "CN 400101")
    placed(log, "CN 400100", 5)
    released(log, "CN 400100", 8)
    found = plan(log, customers(), "ESC", TUESDAY, NOW, [(CarMark.parse("CN 400101"), "ESCPAP1")])
    assert found.worked == 2
    assert len(found) == 2


def test_the_list_separates_what_it_could_not_do():
    log = a_log("CN 400100")
    bad_ordered(log, "CN 400100", 6)
    found = plan(log, customers(), "ESC", TUESDAY, NOW, [(CarMark.parse("CN 400100"), "ESCPAP1")])
    assert found.worked == 0
    assert len(found.stopped) == 1


def test_a_move_prints_what_the_crew_does():
    log = a_log("CN 400100")
    found = plan(log, customers(), "ESC", TUESDAY, NOW, [(CarMark.parse("CN 400100"), "ESCPAP1")])
    assert str(found.moves[0]) == "place CN 400100 ESCPAP1"


def test_a_saturday_works_nothing_at_a_weekday_track():
    log = a_log("CN 400100")
    placed(log, "CN 400100", 5)
    released(log, "CN 400100", 8)
    assert plan(log, customers(), "ESC", SATURDAY, NOW, []).moves == ()


def test_every_kind_says_whether_the_crew_touches_the_car():
    assert MoveKind.PULL.worked is True
    assert MoveKind.PLACE.worked is True
    assert MoveKind.CONSTRUCTIVE.worked is False
    assert MoveKind.STOPPED.worked is False
