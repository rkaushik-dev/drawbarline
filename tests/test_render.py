import pytest

from drawbarline.errors import ConfigurationError
from drawbarline.money import Money
from drawbarline.reports.render import (
    Align,
    Column,
    Table,
    heading,
    hours,
    miles,
    money,
)


def a_table():
    table = Table([Column("road"), Column("amount", Align.RIGHT)])
    table.add("CN", "$1,200.00")
    table.add("ELS", "$8.00")
    return table


def test_a_column_takes_the_width_of_its_widest_value():
    assert Column("road").sized(["escanaba"]) == 8


def test_a_column_is_never_narrower_than_its_heading():
    assert Column("amount").sized(["1"]) == 6


def test_a_column_can_be_given_a_floor_width():
    assert Column("road", width=12).sized(["CN"]) == 12


def test_text_lays_out_left():
    assert Column("road").lay("CN", 6) == "CN    "


def test_numbers_lay_out_right():
    assert Column("amount", Align.RIGHT).lay("12", 6) == "    12"


def test_a_table_prints_a_heading_and_a_rule():
    lines = a_table().lines()
    assert lines[0] == "road     amount"
    assert lines[1] == "----  ---------"


def test_a_numeric_heading_sits_over_its_numbers():
    lines = a_table().lines()
    assert lines[0].endswith("amount")
    assert lines[2].endswith("$1,200.00")


def test_the_rows_line_up_under_the_headings():
    lines = a_table().lines()
    assert lines[2] == "CN    $1,200.00"
    assert lines[3] == "ELS       $8.00"


def test_a_table_with_no_totals_stops_after_the_rows():
    assert len(a_table().lines()) == 4


def test_a_total_gets_its_own_rule():
    table = a_table()
    table.total("", "$1,208.00")
    lines = table.lines()
    assert lines[4] == "----  ---------"
    assert lines[5] == "      $1,208.00"


def test_a_row_that_does_not_fit_the_columns_is_refused():
    with pytest.raises(ConfigurationError):
        a_table().add("CN")


def test_a_total_that_does_not_fit_the_columns_is_refused():
    with pytest.raises(ConfigurationError):
        a_table().total("CN")


def test_a_wide_total_widens_the_column():
    table = a_table()
    table.total("", "$1,200,000.00")
    assert table.widths()[1] == len("$1,200,000.00")


def test_a_table_prints_as_its_lines():
    assert str(a_table()) == "\n".join(a_table().lines())


def test_an_empty_table_still_prints_its_headings():
    table = Table([Column("road")])
    assert table.lines() == ["road", "----"]


def test_a_heading_carries_a_title_and_a_blank_line():
    assert heading("car hire liability") == ["CAR HIRE LIABILITY", ""]


def test_a_heading_can_carry_a_subtitle():
    assert heading("car hire liability", "2025-01") == [
        "CAR HIRE LIABILITY",
        "2025-01",
        "",
    ]


def test_money_prints_the_way_the_domain_prints_it():
    assert money(Money(-123456)) == "-$1,234.56"


def test_hours_print_to_two_places():
    assert hours(9600) == "96.00"
    assert hours(17) == "0.17"


def test_miles_print_to_one_place():
    assert miles(336) == "33.6"
    assert miles(1089) == "108.9"
