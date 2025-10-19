import pytest

from drawbarline.hazmat import (
    HazardClass,
    HazmatError,
    Placard,
    Standing,
    check_standing,
    handles,
    is_hazardous,
    placard_for,
)
from drawbarline.lading import KRAFT_PAPER, SODIUM_HYDROXIDE

CORROSIVE = HazardClass.CORROSIVE


def a_car(position, placarded=False, hazard=None, occupied=False):
    return Standing(position, placarded, hazard, occupied)


def placarded(position, hazard=CORROSIVE):
    return Standing(position, True, hazard)


def test_every_class_carries_its_number_and_text():
    assert HazardClass.CORROSIVE.number == 8
    assert HazardClass.FLAMMABLE_LIQUID.text == "flammable liquid"


def test_most_classes_need_a_buffer():
    assert HazardClass.EXPLOSIVE.needs_buffer is True
    assert HazardClass.MISCELLANEOUS.needs_buffer is False


def test_a_placard_prints_its_name_number_and_class():
    found = Placard(CORROSIVE, 1824, "sodium hydroxide solution")
    assert str(found) == "sodium hydroxide solution UN1824 class 8"


def test_a_un_number_out_of_range_is_refused():
    with pytest.raises(HazmatError):
        Placard(CORROSIVE, 0, "something")


def test_a_placard_with_no_name_is_refused():
    with pytest.raises(HazmatError):
        Placard(CORROSIVE, 1824, "  ")


def test_a_hazardous_commodity_takes_a_placard():
    found = placard_for(SODIUM_HYDROXIDE, 1824, CORROSIVE)
    assert found.name == "sodium hydroxide solution"


def test_an_ordinary_commodity_takes_no_placard():
    with pytest.raises(HazmatError):
        placard_for(KRAFT_PAPER, 1824, CORROSIVE)


def test_the_hazard_is_read_off_the_commodity_code():
    assert is_hazardous(SODIUM_HYDROXIDE) is True
    assert is_hazardous(KRAFT_PAPER) is False


def test_a_placarded_car_names_its_class():
    with pytest.raises(HazmatError):
        Standing(1, placarded=True)


def test_an_unplacarded_car_carries_no_class():
    with pytest.raises(HazmatError):
        Standing(1, placarded=False, hazard=CORROSIVE)


def test_positions_count_from_one():
    with pytest.raises(HazmatError):
        Standing(0, placarded=False)


def test_a_train_of_nothing_handles():
    assert handles([]) is True


def test_a_train_with_no_placards_handles():
    assert handles([a_car(1), a_car(2), a_car(3)]) is True


def test_a_placarded_car_behind_a_buffer_handles():
    assert handles([a_car(1), placarded(2), a_car(3)]) is True


def test_a_placarded_car_against_the_engine_does_not_handle():
    found = check_standing([placarded(1), a_car(2)])
    assert len(found) == 1
    assert "against the engine" in found[0]


def test_the_finding_names_the_class():
    found = check_standing([placarded(1), a_car(2)])
    assert "corrosive" in found[0]


def test_a_miscellaneous_placard_may_sit_against_the_engine():
    assert handles([placarded(1, HazardClass.MISCELLANEOUS), a_car(2)]) is True


def test_a_placarded_car_against_an_occupied_car_does_not_handle():
    found = check_standing([a_car(1), placarded(2), a_car(3, occupied=True)])
    assert len(found) == 1
    assert "occupied car" in found[0]


def test_a_placarded_car_a_buffer_away_from_an_occupied_car_handles():
    assert handles([a_car(1), placarded(2), a_car(3), a_car(4, occupied=True)]) is True


def test_both_findings_come_back_when_both_apply():
    found = check_standing([placarded(1), a_car(2, occupied=True)])
    assert len(found) == 2


def test_two_placarded_cars_next_to_each_other_are_fine():
    assert handles([a_car(1), placarded(2), placarded(3), a_car(4)]) is True


def test_the_positions_have_to_run_without_a_gap():
    with pytest.raises(HazmatError):
        check_standing([a_car(1), a_car(3)])


def test_the_cars_may_be_given_in_any_order():
    assert handles([a_car(3), placarded(2), a_car(1)]) is True


def test_an_occupied_car_at_the_head_end_is_checked_too():
    found = check_standing([a_car(1, occupied=True), placarded(2), a_car(3)])
    assert any("occupied car" in one for one in found)
