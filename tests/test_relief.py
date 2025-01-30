from datetime import date

import pytest

from drawbarline.customers import Industry, Shipper
from drawbarline.demurrage.counting import Detention
from drawbarline.demurrage.relief import (
    Relief,
    bunching,
    check_constructive,
    constructive_stands,
    recount,
    relieve,
)
from drawbarline.demurrage.tariff import Step, Tariff
from drawbarline.errors import TariffError
from drawbarline.marks import CarMark

TARIFF = Tariff("4200", date(2024, 7, 1), 2, 1, (Step(2, 10000), Step(0, 20000)))
MILL = Shipper("ESCPAP", "Escanaba Paper")
TRACK = Industry("ESCPAP1", "mill house track", "ESC", MILL, 4)


def a_detention(number, chargeable=4, placed_on=date(2025, 1, 14), industry="ESCPAP1", **more):
    fields = {
        "car": CarMark.parse(f"CN {number}"),
        "industry": industry,
        "placed_on": placed_on,
        "clock_starts": date(2025, 1, 15),
        "free_until": date(2025, 1, 16),
        "counted_to": date(2025, 1, 22),
        "chargeable": chargeable,
        "loaded": True,
        "constructive": False,
    }
    fields.update(more)
    return Detention(**fields)


def test_no_relief_leaves_the_detention_alone():
    one = a_detention(1)
    assert recount(one, TARIFF, 0) is one


def test_relief_pushes_the_clock_forward():
    moved = recount(a_detention(1), TARIFF, 2)
    assert moved.clock_starts == date(2025, 1, 17)


def test_relief_pushes_the_free_time_with_it():
    moved = recount(a_detention(1), TARIFF, 2)
    assert moved.free_until == date(2025, 1, 20)


def test_relief_takes_days_off_the_charge():
    assert recount(a_detention(1), TARIFF, 2).chargeable == 2


def test_enough_relief_takes_the_charge_to_nothing():
    assert recount(a_detention(1), TARIFF, 10).chargeable == 0


def test_relief_walks_the_calendar_not_the_week():
    moved = recount(a_detention(1), TARIFF, 3)
    assert moved.clock_starts == date(2025, 1, 20)


def test_negative_relief_is_refused():
    with pytest.raises(TariffError):
        recount(a_detention(1), TARIFF, -1)


def test_a_delivery_inside_the_working_rate_gets_no_relief():
    found = bunching([a_detention(n) for n in range(1, 5)], per_day=4)
    assert found == []


def test_the_cars_past_the_working_rate_are_pushed_back():
    found = bunching([a_detention(n) for n in range(1, 9)], per_day=4)
    assert [one.days for one in found] == [1, 1, 1, 1]


def test_a_second_batch_is_pushed_back_further():
    found = bunching([a_detention(n) for n in range(1, 13)], per_day=4)
    assert sorted({one.days for one in found}) == [1, 2]


def test_relief_is_handed_out_in_car_order_so_two_runs_agree():
    cars = [a_detention(n) for n in (7, 3, 5, 1, 9, 2)]
    first = [str(one.car) for one in bunching(cars, per_day=2)]
    second = [str(one.car) for one in bunching(list(reversed(cars)), per_day=2)]
    assert first == second


def test_two_tracks_are_worked_independently():
    cars = [a_detention(n) for n in range(1, 5)]
    cars += [a_detention(n, industry="ESCPAP2") for n in range(10, 14)]
    assert bunching(cars, per_day=4) == []


def test_two_days_of_deliveries_are_counted_apart():
    cars = [a_detention(n) for n in range(1, 5)]
    cars += [a_detention(n, placed_on=date(2025, 1, 15)) for n in range(10, 14)]
    assert bunching(cars, per_day=4) == []


def test_a_customer_who_works_no_cars_is_refused():
    with pytest.raises(TariffError):
        bunching([a_detention(1)], per_day=0)


def test_relief_prints_the_car_and_the_days():
    relief = Relief(CarMark.parse("CN 000001"), "ESCPAP1", 2)
    assert str(relief) == "CN 000001 2 days relief for bunching"


def test_relieving_a_set_recounts_the_ones_that_earned_it():
    cars = [a_detention(n) for n in range(1, 9)]
    relieved = relieve(cars, TARIFF, per_day=4)
    charged = sorted(one.chargeable for one in relieved)
    assert charged == [3, 3, 3, 3, 4, 4, 4, 4]


def test_relieving_a_set_that_earned_none_changes_nothing():
    cars = [a_detention(n) for n in range(1, 5)]
    assert [one.chargeable for one in relieve(cars, TARIFF, per_day=4)] == [4, 4, 4, 4]


def test_a_full_track_supports_a_constructive_placement():
    assert constructive_stands(TRACK, 4) is True


def test_a_track_with_room_does_not():
    assert constructive_stands(TRACK, 3) is False


def test_a_constructive_placement_onto_a_track_with_room_is_refused():
    one = a_detention(1, constructive=True)
    with pytest.raises(TariffError):
        check_constructive(one, TRACK, 2)


def test_a_constructive_placement_onto_a_full_track_stands():
    one = a_detention(1, constructive=True)
    assert check_constructive(one, TRACK, 4) is one


def test_an_actual_placement_is_never_questioned():
    one = a_detention(1)
    assert check_constructive(one, TRACK, 0) is one
