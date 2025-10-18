from datetime import date

import pytest

from drawbarline.clock import utc
from drawbarline.distribution import (
    CarOrder,
    DistributionError,
    Empty,
    distribute,
    empties_on_hand,
)
from drawbarline.events.stream import EventLog
from drawbarline.fleet.aar import CarKind
from drawbarline.marks import CarMark
from support import a_car, a_fleet, bad_ordered, placed, received

STATIONS = {"ESCPAP1": "ESC", "PWRGRN1": "PWR", "IMTMIN1": "IMT"}
NOW = utc(2025, 1, 14, 12)
HOPPER = CarKind.COVERED_HOPPER


def an_order(number=1, cars=1, industry="ESCPAP1", wanted=date(2025, 1, 14), kind=HOPPER):
    return CarOrder(
        number=number,
        shipper="ESCPAP",
        industry=industry,
        kind=kind,
        cars=cars,
        wanted_on=wanted,
    )


def an_empty(number, station="ESC", kind=HOPPER):
    return Empty(CarMark.parse(f"CN {number}"), kind, station)


def test_an_order_prints_what_it_asks_for():
    assert str(an_order(cars=4)) == "order 1 ESCPAP 4 covered hopper for 2025-01-14"


def test_an_order_numbered_below_one_is_refused():
    with pytest.raises(DistributionError):
        an_order(number=0)


def test_an_order_for_no_cars_is_refused():
    with pytest.raises(DistributionError):
        an_order(cars=0)


def test_an_order_with_no_track_is_refused():
    with pytest.raises(DistributionError):
        an_order(industry="  ")


def test_the_oldest_order_comes_first():
    early = an_order(2, wanted=date(2025, 1, 10))
    late = an_order(1, wanted=date(2025, 1, 14))
    assert sorted([late, early], key=lambda one: one.priority)[0] is early


def test_two_orders_the_same_day_go_by_number():
    first = an_order(1)
    second = an_order(2)
    assert sorted([second, first], key=lambda one: one.priority)[0] is first


def test_one_empty_fills_one_order():
    found = distribute([an_order()], [an_empty(400100)], STATIONS)
    assert found.filled == 1
    assert found.complete is True


def test_an_order_with_nothing_suitable_goes_short():
    found = distribute([an_order(cars=2)], [an_empty(400100)], STATIONS)
    assert found.filled == 1
    assert found.short == {1: 1}
    assert found.complete is False


def test_a_car_of_the_wrong_kind_is_not_offered():
    found = distribute([an_order()], [an_empty(400100, kind=CarKind.TANK)], STATIONS)
    assert found.filled == 0
    assert found.unfilled == 1


def test_the_nearest_empty_is_taken():
    empties = [an_empty(400100, "GRB"), an_empty(400101, "WLS")]
    found = distribute([an_order()], empties, STATIONS)
    assert str(found.assignments[0].car) == "CN 400101"


def test_a_tie_on_distance_goes_to_the_lower_car_number():
    empties = [an_empty(400102, "WLS"), an_empty(400101, "WLS")]
    found = distribute([an_order()], empties, STATIONS)
    assert str(found.assignments[0].car) == "CN 400101"


def test_two_runs_of_the_same_morning_agree():
    empties = [an_empty(400103, "GRB"), an_empty(400101, "WLS"), an_empty(400102, "WLS")]
    orders = [an_order(1), an_order(2)]
    first = distribute(orders, empties, STATIONS)
    second = distribute(list(reversed(orders)), list(reversed(empties)), STATIONS)
    assert [str(one.car) for one in first.assignments] == [
        str(one.car) for one in second.assignments
    ]


def test_an_empty_is_never_assigned_twice():
    found = distribute([an_order(1), an_order(2)], [an_empty(400100)], STATIONS)
    assert found.filled == 1
    assert found.short == {2: 1}


def test_the_older_order_is_filled_first():
    orders = [an_order(1, wanted=date(2025, 1, 20)), an_order(2, wanted=date(2025, 1, 10))]
    found = distribute(orders, [an_empty(400100)], STATIONS)
    assert found.assignments[0].order == 2


def test_an_assignment_carries_the_distance():
    found = distribute([an_order()], [an_empty(400100, "PWR")], STATIONS)
    assert found.assignments[0].tenths == 336


def test_the_empty_mileage_adds_up():
    empties = [an_empty(400100, "PWR"), an_empty(400101, "PWR")]
    found = distribute([an_order(cars=2)], empties, STATIONS)
    assert found.empty_tenths() == 672


def test_an_assignment_prints_where_it_goes():
    found = distribute([an_order()], [an_empty(400100, "PWR")], STATIONS)
    assert str(found.assignments[0]) == "CN 400100 PWR to ESC for order 1"


def test_the_assignments_for_one_order_come_back():
    orders = [an_order(1, cars=2), an_order(2)]
    empties = [an_empty(number) for number in (1, 2, 3)]
    found = distribute(orders, empties, STATIONS)
    assert len(found.for_order(1)) == 2


def test_a_track_with_no_station_is_refused():
    with pytest.raises(DistributionError):
        distribute([an_order(industry="NOWHERE")], [an_empty(400100)], STATIONS)


def test_a_complete_distribution_prints_as_complete():
    found = distribute([an_order()], [an_empty(400100)], STATIONS)
    assert str(found) == "1 cars assigned, every order filled"


def test_a_short_distribution_says_how_short():
    found = distribute([an_order(cars=3)], [an_empty(400100)], STATIONS)
    assert str(found) == "1 cars assigned, 2 short"


def test_an_empty_car_on_our_rail_is_on_hand():
    register = a_fleet("CN 400100")
    log = EventLog()
    received(log, "CN 400100", 6, waybill=None)
    assert [str(one.car) for one in empties_on_hand(register, log, NOW)] == ["CN 400100"]


def test_a_loaded_car_is_not_an_empty():
    register = a_fleet("CN 400100")
    log = EventLog()
    received(log, "CN 400100", 6, waybill=41207)
    assert empties_on_hand(register, log, NOW) == []


def test_a_placed_car_is_not_on_hand():
    register = a_fleet("CN 400100")
    log = EventLog()
    received(log, "CN 400100", 6, waybill=None)
    placed(log, "CN 400100", 7)
    assert empties_on_hand(register, log, NOW) == []


def test_a_bad_ordered_car_is_not_offered():
    register = a_fleet("CN 400100")
    log = EventLog()
    received(log, "CN 400100", 6, waybill=None)
    bad_ordered(log, "CN 400100", 7)
    assert empties_on_hand(register, log, NOW) == []


def test_a_car_with_no_equipment_record_is_left_out():
    register = a_fleet()
    log = EventLog()
    received(log, "CN 400100", 6, waybill=None)
    assert empties_on_hand(register, log, NOW) == []


def test_an_empty_carries_its_kind_and_station():
    register = a_fleet()
    register.add(a_car("CN 400100"))
    log = EventLog()
    received(log, "CN 400100", 6, waybill=None)
    found = empties_on_hand(register, log, NOW)[0]
    assert found.kind is HOPPER
    assert found.station == "ESC"
    assert str(found) == "CN 400100 at ESC"
