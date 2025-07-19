from datetime import date

import click
import pytest

from drawbarline.cli.readers import (
    a_date,
    a_moment,
    a_month,
    require_columns,
    rows_of,
)
from drawbarline.clock import utc
from drawbarline.errors import DrawbarlineError
from drawbarline.settlement.period import Period


def test_a_day_reads_back():
    assert a_date("2025-01-14") == date(2025, 1, 14)


def test_a_day_with_space_around_it_reads_back():
    assert a_date("  2025-01-14 ") == date(2025, 1, 14)


@pytest.mark.parametrize("bad", ["", "january", "2025-13-01", "14-01-2025"])
def test_a_day_that_cannot_be_read_is_refused(bad):
    with pytest.raises(click.ClickException) as caught:
        a_date(bad)
    assert "yyyy-mm-dd" in caught.value.format_message()


def test_a_month_reads_back():
    assert a_month("2025-01") == Period(2025, 1)


@pytest.mark.parametrize("bad", ["", "january", "2025-13", "2025-01-14"])
def test_a_month_that_cannot_be_read_is_refused(bad):
    with pytest.raises(click.ClickException) as caught:
        a_month(bad)
    assert "yyyy-mm" in caught.value.format_message()


def test_a_reading_is_read_at_the_station():
    assert a_moment("ESC", "2025-01-14 09:00") == utc(2025, 1, 14, 14, 0)


def test_the_same_reading_at_marinette_is_an_hour_later():
    assert a_moment("MRN", "2025-01-14 09:00") == utc(2025, 1, 14, 15, 0)


def test_a_reading_that_cannot_be_read_is_refused():
    with pytest.raises(click.ClickException) as caught:
        a_moment("ESC", "yesterday afternoon")
    assert "yyyy-mm-dd hh:mm" in caught.value.format_message()


def test_a_reading_that_never_happened_is_refused():
    with pytest.raises(DrawbarlineError):
        a_moment("PWR", "2025-03-09 02:30")


def test_the_second_pass_takes_the_later_hour():
    first = a_moment("PWR", "2025-11-02 01:30")
    second = a_moment("PWR", "2025-11-02 01:30", second_pass=True)
    assert (second - first).total_seconds() == 3600


def test_a_station_we_do_not_serve_is_refused():
    with pytest.raises(DrawbarlineError):
        a_moment("CHI", "2025-01-14 09:00")


def test_a_sheet_reads_back_as_rows(tmp_path):
    sheet = tmp_path / "one.csv"
    sheet.write_text("a,b\n1,2\n3,4\n", encoding="utf-8")
    assert rows_of(sheet) == [{"a": "1", "b": "2"}, {"a": "3", "b": "4"}]


def test_an_empty_sheet_reads_back_as_nothing(tmp_path):
    sheet = tmp_path / "empty.csv"
    sheet.write_text("a,b\n", encoding="utf-8")
    assert rows_of(sheet) == []


def test_a_sheet_with_the_columns_it_needs_passes():
    require_columns([{"a": "1", "b": "2"}], {"a", "b"}, "one.csv")


def test_a_sheet_missing_a_column_is_refused():
    with pytest.raises(click.ClickException) as caught:
        require_columns([{"a": "1"}], {"a", "b"}, "one.csv")
    assert "one.csv" in caught.value.format_message()
    assert "'b'" in caught.value.format_message()


def test_a_sheet_with_no_rows_at_all_is_refused():
    with pytest.raises(click.ClickException):
        require_columns([], {"a"}, "empty.csv")
