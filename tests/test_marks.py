import pytest

from drawbarline.errors import MarkError
from drawbarline.marks import CarMark, ReportingMark, check_digit


def test_a_mark_of_two_letters_is_fine():
    assert str(ReportingMark("CN")) == "CN"


def test_a_mark_of_four_letters_is_fine():
    assert str(ReportingMark("DBLX")) == "DBLX"


@pytest.mark.parametrize("bad", ["", "D", "TOOLONG", "DB1", "db", "DB "])
def test_a_mark_that_is_not_two_to_four_capitals_is_refused(bad):
    with pytest.raises(MarkError):
        ReportingMark(bad)


def test_a_mark_that_is_not_a_string_is_refused():
    with pytest.raises(MarkError):
        ReportingMark(42)


def test_parse_takes_lower_case_and_surrounding_space():
    assert ReportingMark.parse("  wc ") == ReportingMark("WC")


def test_a_trailing_x_means_the_owner_is_not_a_railroad():
    assert ReportingMark("PLMX").is_private is True
    assert ReportingMark("WC").is_private is False


def test_the_road_drops_a_private_suffix():
    assert ReportingMark("PLMX").road == "PLM"
    assert ReportingMark("WC").road == "WC"


def test_marks_sort_alphabetically():
    assert sorted([ReportingMark("WC"), ReportingMark("CN")]) == [
        ReportingMark("CN"),
        ReportingMark("WC"),
    ]


def test_marks_are_hashable_and_compare_by_value():
    assert {ReportingMark("CN"), ReportingMark("CN")} == {ReportingMark("CN")}


@pytest.mark.parametrize(
    ("number", "digit"),
    [(1, 8), (104207, 6), (999999, 6), (450018, 7), (0, 0)],
)
def test_the_check_digit_matches_worked_examples(number, digit):
    assert check_digit(number) == digit


def test_the_check_digit_catches_a_transposition():
    assert check_digit(104207) != check_digit(140207)


def test_a_number_past_six_digits_has_no_check_digit():
    with pytest.raises(MarkError):
        check_digit(1000000)


def test_a_car_reads_back_padded():
    car = CarMark(ReportingMark("DBLX"), 4207)
    assert str(car) == "DBLX 004207"


def test_the_stencil_carries_the_check_digit():
    car = CarMark(ReportingMark("DBLX"), 104207)
    assert car.stencilled() == "DBLX 104207-6"


@pytest.mark.parametrize("text", ["DBLX 104207", "dblx104207", "DBLX.104207", "DBLX 104207-6"])
def test_parse_reads_the_forms_a_clerk_writes(text):
    assert CarMark.parse(text) == CarMark(ReportingMark("DBLX"), 104207)


def test_parse_refuses_a_stencil_whose_check_digit_disagrees():
    with pytest.raises(MarkError) as caught:
        CarMark.parse("DBLX 104207-4")
    assert caught.value.context["expected"] == 6


def test_parse_refuses_something_that_is_not_a_car():
    with pytest.raises(MarkError):
        CarMark.parse("westbound local")


@pytest.mark.parametrize("bad", [0, -1, 1000000])
def test_a_car_number_out_of_range_is_refused(bad):
    with pytest.raises(MarkError):
        CarMark(ReportingMark("WC"), bad)


def test_a_boolean_is_not_a_car_number():
    with pytest.raises(MarkError):
        CarMark(ReportingMark("WC"), True)


def test_a_car_knows_its_owner_is_private():
    assert CarMark.parse("PLMX 1").is_private is True


def test_cars_sort_by_mark_then_number():
    cars = [CarMark.parse("WC 100"), CarMark.parse("CN 900"), CarMark.parse("CN 100")]
    assert [str(c) for c in sorted(cars)] == ["CN 000100", "CN 000900", "WC 000100"]
