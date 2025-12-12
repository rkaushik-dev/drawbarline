import pytest

from drawbarline.blocking import (
    Block,
    BlockingError,
    Plan,
    classify,
    order_of,
)
from drawbarline.marks import CarMark
from drawbarline.mileage import MileageError

GREEN_BAY = Block("GRB", "green bay and beyond", 1, frozenset({"GRB", "OCO"}))
MARINETTE = Block("MRN", "marinette", 2, frozenset({"MRN", "PSH"}))
LOCAL = Block("LCL", "the local", 3, frozenset({"PWR", "SPL", "HRM"}))


def a_plan(*blocks):
    return Plan("ESC", tuple(blocks or (GREEN_BAY, MARINETTE, LOCAL)))


def a_car(number):
    return CarMark.parse(f"CN {number}")


def test_a_block_prints_its_name_and_position():
    assert str(GREEN_BAY) == "GRB green bay and beyond at 1"


def test_a_block_code_is_upper_case():
    with pytest.raises(BlockingError):
        Block("grb", "x", 1, frozenset({"GRB"}))


def test_a_block_stands_from_position_one():
    with pytest.raises(BlockingError):
        Block("GRB", "x", 0, frozenset({"GRB"}))


def test_a_block_covering_nothing_is_refused():
    with pytest.raises(BlockingError):
        Block("GRB", "x", 1, frozenset())


def test_a_block_covering_a_place_we_do_not_serve_is_refused():
    with pytest.raises(MileageError):
        Block("CHI", "x", 1, frozenset({"CHI"}))


def test_a_block_takes_the_places_it_covers():
    assert GREEN_BAY.takes("OCO") is True
    assert GREEN_BAY.takes("MRN") is False


def test_a_plan_stands_its_blocks_head_end_first():
    assert [one.code for one in a_plan().standing] == ["GRB", "MRN", "LCL"]


def test_a_plan_with_no_blocks_is_refused():
    with pytest.raises(BlockingError):
        Plan("ESC", ())


def test_a_plan_at_a_place_we_do_not_serve_is_refused():
    with pytest.raises(MileageError):
        Plan("CHI", (GREEN_BAY,))


def test_two_blocks_with_one_code_are_refused():
    other = Block("GRB", "again", 4, frozenset({"WAL"}))
    with pytest.raises(BlockingError):
        Plan("ESC", (GREEN_BAY, other))


def test_two_blocks_in_one_position_are_refused():
    other = Block("WAL", "wallace", 1, frozenset({"WAL"}))
    with pytest.raises(BlockingError):
        Plan("ESC", (GREEN_BAY, other))


def test_two_blocks_covering_one_place_are_refused():
    other = Block("OCO", "oconto", 4, frozenset({"OCO"}))
    with pytest.raises(BlockingError) as caught:
        Plan("ESC", (GREEN_BAY, other))
    assert caught.value.context["places"] == ["OCO"]


def test_a_destination_finds_its_block():
    assert a_plan().block_for("PSH").code == "MRN"


def test_a_destination_nobody_planned_for_has_no_block():
    assert a_plan().block_for("NIA") is None


def test_a_plan_lists_everything_it_covers():
    assert "SPL" in a_plan().covers()
    assert "NIA" not in a_plan().covers()


def test_every_car_goes_in_its_block():
    found = classify([(a_car(1), "GRB"), (a_car(2), "MRN"), (a_car(3), "PWR")], a_plan())
    assert [str(one) for one in found.cars_in("GRB")] == ["CN 000001"]
    assert [str(one) for one in found.cars_in("LCL")] == ["CN 000003"]


def test_two_cars_for_one_block_stand_together():
    found = classify([(a_car(1), "GRB"), (a_car(2), "OCO")], a_plan())
    assert len(found.cars_in("GRB")) == 2


def test_cars_inside_a_block_come_back_in_car_order():
    found = classify([(a_car(9), "GRB"), (a_car(2), "OCO")], a_plan())
    assert [str(one) for one in found.cars_in("GRB")] == ["CN 000002", "CN 000009"]


def test_a_car_nobody_planned_for_is_not_quietly_added():
    found = classify([(a_car(1), "NIA")], a_plan())
    assert found.unblocked == [a_car(1)]
    assert found.blocked == 0


def test_a_block_with_no_cars_is_still_on_the_plan():
    found = classify([(a_car(1), "GRB")], a_plan())
    assert found.cars_in("MRN") == []


def test_the_classification_prints_its_counts():
    found = classify([(a_car(1), "GRB"), (a_car(2), "MRN")], a_plan())
    assert str(found) == "2 cars in 3 blocks"


def test_unblocked_cars_show_up_when_it_prints():
    found = classify([(a_car(1), "GRB"), (a_car(2), "NIA")], a_plan())
    assert "1 unblocked" in str(found)


def test_the_train_stands_in_block_order():
    cars = [(a_car(3), "PWR"), (a_car(1), "GRB"), (a_car(2), "MRN")]
    found = classify(cars, a_plan())
    assert [str(one) for one in order_of(found, a_plan())] == [
        "CN 000001",
        "CN 000002",
        "CN 000003",
    ]


def test_a_train_of_nothing_orders_to_nothing():
    assert order_of(classify([], a_plan()), a_plan()) == []


def test_unblocked_cars_are_left_out_of_the_train():
    found = classify([(a_car(1), "GRB"), (a_car(2), "NIA")], a_plan())
    assert len(order_of(found, a_plan())) == 1
