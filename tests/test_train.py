from datetime import date

import pytest

from drawbarline.api.errors import STATUS_FOR
from drawbarline.lading import WHEAT, Lading
from drawbarline.measure import Length
from drawbarline.mileage import BRANCH, MAIN
from drawbarline.train import (
    Consist,
    ConsistError,
    District,
    Position,
    check,
    clears,
    district,
    wheel_report,
)
from drawbarline.weight import Weight
from support import a_car

GRAIN = Lading(WHEAT, Weight(198000))


def a_consist(count=3, loaded=True, symbol="L51"):
    positions = tuple(
        Position(a_car(f"CN {400100 + at}"), GRAIN if loaded else None) for at in range(count)
    )
    return Consist(symbol=symbol, day=date(2025, 1, 14), positions=positions)


def test_a_train_prints_its_symbol_cars_and_tons():
    assert str(a_consist()) == "L51 2025-01-14 3 cars 392 tons"


def test_a_train_with_no_symbol_is_refused():
    with pytest.raises(ConsistError):
        Consist(symbol="  ", day=date(2025, 1, 14), positions=())


def test_the_same_car_twice_in_one_train_is_refused():
    car = a_car("CN 400100")
    with pytest.raises(ConsistError):
        Consist(
            symbol="L51",
            day=date(2025, 1, 14),
            positions=(Position(car), Position(car)),
        )


def test_a_loaded_car_weighs_its_light_weight_and_its_lading():
    assert Position(a_car("CN 400100"), GRAIN).weight == Weight(63500 + 198000)


def test_an_empty_car_weighs_only_itself():
    assert Position(a_car("CN 400100")).weight == Weight(63500)


def test_a_position_prints_what_it_carries():
    assert str(Position(a_car("CN 400100"), GRAIN)) == "CN 400100 wheat"
    assert str(Position(a_car("CN 400100"))) == "CN 400100 empty"


def test_the_train_counts_loads_and_empties():
    positions = (
        Position(a_car("CN 400100"), GRAIN),
        Position(a_car("CN 400101")),
        Position(a_car("CN 400102"), GRAIN),
    )
    found = Consist("L51", date(2025, 1, 14), positions)
    assert found.loads == 2
    assert found.empties == 1


def test_the_tonnage_is_the_whole_train():
    assert a_consist(count=2).weight == Weight(2 * (63500 + 198000))


def test_the_tonnage_rounds_down_the_way_a_rating_is_read():
    assert a_consist(count=1).tons == 130


def test_the_length_counts_the_slack_at_every_coupling():
    found = a_consist(count=3)
    assert found.length == Length(3 * 714 + 2 * 6)


def test_a_train_of_one_car_has_no_slack():
    assert a_consist(count=1).length == Length(714)


def test_a_train_of_no_cars_is_no_length():
    empty = Consist("L51", date(2025, 1, 14), ())
    assert empty.length == Length(0)
    assert empty.tons == 0


def test_a_train_knows_which_cars_it_carries():
    from drawbarline.marks import CarMark

    found = a_consist()
    assert found.carries(CarMark.parse("CN 400100")) is True
    assert found.carries(CarMark.parse("WC 000001")) is False


def test_the_main_and_the_branch_have_their_own_ratings():
    assert district(MAIN).max_tons == 6800
    assert district(BRANCH).max_tons == 3400


def test_a_line_this_road_does_not_have_is_refused():
    with pytest.raises(ConsistError):
        district("C")


def test_a_district_of_no_tonnage_is_refused():
    with pytest.raises(ConsistError):
        District(MAIN, "nowhere", 0, Length.of(100))


def test_a_district_on_a_line_we_do_not_have_is_refused():
    with pytest.raises(ConsistError):
        District("C", "nowhere", 100, Length.of(100))


def test_a_train_inside_the_rating_clears():
    assert clears(a_consist(count=10), district(MAIN)) is True


def test_a_train_over_the_tonnage_does_not_clear():
    found = a_consist(count=60)
    assert clears(found, district(MAIN)) is False
    assert any("tons is over" in one for one in check(found, district(MAIN)))


def test_a_train_over_the_length_does_not_clear():
    found = a_consist(count=80, loaded=False)
    assert any("longer than" in one for one in check(found, district(MAIN)))


def test_the_branch_stops_a_train_the_main_would_take():
    found = a_consist(count=30)
    assert clears(found, district(MAIN)) is True
    assert clears(found, district(BRANCH)) is False


def test_a_train_that_clears_draws_no_findings():
    assert check(a_consist(count=3), district(MAIN)) == []


def test_the_wheel_report_lists_the_cars_in_order():
    lines = wheel_report(a_consist())
    rows = [line for line in lines if "CN 4001" in line]
    assert [line.split()[0] for line in rows] == ["1", "2", "3"]


def test_the_wheel_report_names_the_train():
    assert wheel_report(a_consist())[0] == "WHEEL REPORT L51"


def test_the_wheel_report_totals_the_train():
    lines = wheel_report(a_consist())
    total = [line for line in lines if line.strip().startswith("3 cars")][0]
    assert "3 loads 0 empties" in total
    assert "392" in total


def test_the_wheel_report_shows_an_empty_as_empty():
    lines = wheel_report(a_consist(count=1, loaded=False))
    assert any("empty" in line for line in lines)


def test_the_consist_error_has_a_decided_status():
    assert ConsistError in STATUS_FOR
