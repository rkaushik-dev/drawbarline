import pytest

from drawbarline.measure import ZERO, Length, MeasureError, longest, total


def test_feet_and_inches_go_in_and_come_back():
    car = Length.of(89, 4)
    assert car.inches == 1072
    assert car.feet == 89
    assert car.inches_part == 4


def test_inches_over_eleven_roll_into_feet():
    assert Length.of(0, 25) == Length.of(2, 1)


def test_a_whole_number_of_feet_prints_without_inches():
    assert str(Length.of(50)) == "50'"


def test_feet_and_inches_print_the_way_a_stencil_reads():
    assert str(Length.of(89, 4)) == "89'4\""


@pytest.mark.parametrize(
    "text",
    ["89'4\"", "89' 4\"", "89ft 4in", "89 FT 4 IN", "89-4"],
)
def test_parse_reads_the_forms_the_register_uses(text):
    assert Length.parse(text) == Length.of(89, 4)


def test_parse_reads_feet_with_no_inches():
    assert Length.parse("50'") == Length.of(50)


def test_parse_reads_bare_inches():
    assert Length.parse('9"') == Length(9)


@pytest.mark.parametrize("bad", ["", "long", "89", "-4'", "89'99\""])
def test_parse_refuses_what_it_cannot_read(bad):
    with pytest.raises(MeasureError):
        Length.parse(bad)


def test_a_negative_length_is_refused():
    with pytest.raises(MeasureError):
        Length(-1)


def test_a_float_is_not_a_length():
    with pytest.raises(MeasureError):
        Length(12.5)


def test_a_boolean_is_not_a_length():
    with pytest.raises(MeasureError):
        Length(True)


def test_lengths_add():
    assert Length.of(4) + Length.of(3, 6) == Length.of(7, 6)


def test_lengths_subtract():
    assert Length.of(89, 4) - Length.of(50) == Length.of(39, 4)


def test_a_subtraction_that_runs_past_zero_is_refused():
    with pytest.raises(MeasureError) as caught:
        Length.of(3) - Length.of(4)
    assert caught.value.context["left"] == "3'"


def test_a_length_scales_by_a_count():
    assert Length.of(50) * 3 == Length.of(150)
    assert 3 * Length.of(50) == Length.of(150)


def test_a_length_does_not_scale_by_a_negative():
    with pytest.raises(MeasureError):
        Length.of(50) * -1


def test_a_length_does_not_scale_by_a_float():
    with pytest.raises(TypeError):
        Length.of(50) * 1.5


def test_lengths_order_and_sort():
    assert Length.of(40) < Length.of(50)
    assert sorted([Length.of(50), Length.of(40)]) == [Length.of(40), Length.of(50)]


def test_decimal_feet_is_available_for_a_report():
    assert Length.of(89, 6).in_feet() == 89.5


def test_the_longest_of_several():
    assert longest([Length.of(40), Length.of(89, 4), Length.of(60)]) == Length.of(89, 4)


def test_the_longest_of_none_is_refused():
    with pytest.raises(MeasureError):
        longest([])


def test_a_total_over_nothing_is_zero():
    assert total([]) == ZERO


def test_a_total_adds_them_all():
    assert total([Length.of(50), Length.of(40, 6), Length.of(0, 6)]) == Length.of(91)
