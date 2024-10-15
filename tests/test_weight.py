import pytest

from drawbarline.weight import (
    NOTHING,
    NominalCapacity,
    Weight,
    WeightError,
    capacity_for,
    load_limit,
    overload,
)


def test_pounds_go_in_and_come_back():
    assert Weight(63500).pounds == 63500


def test_tons_are_short_tons():
    assert Weight.tons(100) == Weight(200000)


def test_fractional_tons_are_refused_rather_than_rounded():
    with pytest.raises(WeightError):
        Weight.tons(100.5)


def test_a_negative_weight_is_refused():
    with pytest.raises(WeightError):
        Weight(-1)


def test_a_float_is_not_a_weight():
    with pytest.raises(WeightError):
        Weight(63500.5)


def test_a_boolean_is_not_a_weight():
    with pytest.raises(WeightError):
        Weight(True)


def test_a_weight_prints_grouped_and_in_pounds():
    assert str(Weight(263000)) == "263,000 lb"


def test_decimal_tons_are_available_for_a_report():
    assert Weight(263000).in_tons() == 131.5


def test_weights_add_and_subtract():
    assert Weight(1000) + Weight(500) == Weight(1500)
    assert Weight(1000) - Weight(500) == Weight(500)


def test_a_subtraction_past_zero_is_refused():
    with pytest.raises(WeightError) as caught:
        Weight(500) - Weight(1000)
    assert caught.value.context["right"] == "1,000 lb"


def test_a_weight_scales_by_a_count():
    assert Weight(1000) * 4 == Weight(4000)
    assert 4 * Weight(1000) == Weight(4000)


def test_a_weight_does_not_scale_by_a_negative():
    with pytest.raises(WeightError):
        Weight(1000) * -2


def test_weights_order_and_sort():
    assert Weight(100) < Weight(200)
    assert max([Weight(100), Weight(300), Weight(200)]) == Weight(300)


@pytest.mark.parametrize(
    ("capacity", "pounds"),
    [
        (NominalCapacity.SEVENTY, 220000),
        (NominalCapacity.HUNDRED, 263000),
        (NominalCapacity.ONE_TEN, 286000),
        (NominalCapacity.ONE_TWENTY_FIVE, 315000),
    ],
)
def test_a_capacity_class_names_a_real_gross_rail_load(capacity, pounds):
    assert capacity.gross_rail_load == Weight(pounds)


def test_a_hundred_ton_car_does_not_weigh_a_hundred_tons():
    assert NominalCapacity.HUNDRED.gross_rail_load != Weight.tons(100)


def test_a_capacity_class_labels_itself():
    assert NominalCapacity.ONE_TWENTY_FIVE.label == "125 ton"


def test_the_class_reads_back_off_a_gross_rail_load():
    assert capacity_for(Weight(286000)) is NominalCapacity.ONE_TEN


def test_an_unknown_gross_rail_load_has_no_class():
    with pytest.raises(WeightError):
        capacity_for(Weight(250000))


def test_the_load_limit_is_the_gross_rail_load_less_the_light_weight():
    assert load_limit(Weight(286000), Weight(63500)) == Weight(222500)


def test_a_car_that_weighs_its_own_gross_rail_load_is_refused():
    with pytest.raises(WeightError):
        load_limit(Weight(220000), Weight(220000))


def test_a_lading_inside_the_limit_is_no_overload():
    assert overload(Weight(200000), Weight(222500)) == NOTHING


def test_a_lading_exactly_on_the_limit_is_no_overload():
    assert overload(Weight(222500), Weight(222500)) == NOTHING


def test_an_overload_reports_how_far_past():
    assert overload(Weight(225000), Weight(222500)) == Weight(2500)
