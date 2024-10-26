from datetime import UTC, datetime, timedelta

import pytest

from drawbarline.clock import Interval, to_local, to_utc, utc
from drawbarline.errors import DrawbarlineError
from drawbarline.reference import drawbar_line

LINE = drawbar_line()
ESC = LINE.station("ESC")
MRN = LINE.station("MRN")


def test_a_reading_at_escanaba_is_eastern():
    assert to_utc(ESC, datetime(2025, 1, 14, 9, 0)) == utc(2025, 1, 14, 14, 0)


def test_the_same_reading_at_marinette_is_an_hour_later():
    assert to_utc(MRN, datetime(2025, 1, 14, 9, 0)) == utc(2025, 1, 14, 15, 0)


def test_a_car_across_the_seam_at_the_same_clock_reading_ran_an_hour():
    received = to_utc(ESC, datetime(2025, 1, 14, 9, 0))
    delivered = to_utc(MRN, datetime(2025, 1, 14, 9, 0))
    assert Interval(received, delivered).minutes == 60


def test_summer_readings_move_with_the_clocks():
    assert to_utc(ESC, datetime(2025, 7, 14, 9, 0)) == utc(2025, 7, 14, 13, 0)


def test_a_reading_that_carries_a_zone_is_refused():
    with pytest.raises(DrawbarlineError):
        to_utc(ESC, datetime(2025, 1, 14, 9, 0, tzinfo=UTC))


def test_a_reading_that_never_happened_is_refused():
    with pytest.raises(DrawbarlineError) as caught:
        to_utc(MRN, datetime(2025, 3, 9, 2, 30))
    assert "clocks went forward" in caught.value.message


def test_an_hour_that_happened_twice_needs_saying_which():
    first = to_utc(MRN, datetime(2025, 11, 2, 1, 30))
    second = to_utc(MRN, datetime(2025, 11, 2, 1, 30), second_pass=True)
    assert second - first == timedelta(hours=1)


def test_asking_for_a_second_pass_on_an_ordinary_day_is_refused():
    with pytest.raises(DrawbarlineError):
        to_utc(MRN, datetime(2025, 1, 14, 9, 0), second_pass=True)


def test_a_moment_reads_back_as_the_local_clock():
    assert to_local(ESC, utc(2025, 1, 14, 14, 0)).hour == 9
    assert to_local(MRN, utc(2025, 1, 14, 14, 0)).hour == 8


def test_an_interval_counts_whole_minutes():
    assert Interval(utc(2025, 1, 1, 0, 0), utc(2025, 1, 1, 3, 30)).minutes == 210


def test_an_interval_reports_hours_to_two_places():
    assert Interval(utc(2025, 1, 1, 0, 0), utc(2025, 1, 1, 3, 30)).hundredth_hours == 350


def test_an_odd_number_of_minutes_rounds_to_the_nearest_hundredth():
    assert Interval(utc(2025, 1, 1, 0, 0), utc(2025, 1, 1, 0, 10)).hundredth_hours == 17


def test_an_interval_of_no_time_is_empty():
    assert Interval(utc(2025, 1, 1), utc(2025, 1, 1)).empty is True


def test_an_interval_that_ends_before_it_starts_is_refused():
    with pytest.raises(DrawbarlineError):
        Interval(utc(2025, 1, 2), utc(2025, 1, 1))


def test_an_interval_of_naive_moments_is_refused():
    with pytest.raises(DrawbarlineError):
        Interval(datetime(2025, 1, 1), datetime(2025, 1, 2))


def test_an_interval_is_closed_at_the_start_and_open_at_the_end():
    span = Interval(utc(2025, 1, 1), utc(2025, 1, 2))
    assert span.contains(utc(2025, 1, 1)) is True
    assert span.contains(utc(2025, 1, 2)) is False


def test_intervals_that_touch_do_not_overlap():
    first = Interval(utc(2025, 1, 1), utc(2025, 1, 2))
    second = Interval(utc(2025, 1, 2), utc(2025, 1, 3))
    assert first.overlaps(second) is False


def test_intervals_that_share_time_overlap():
    first = Interval(utc(2025, 1, 1), utc(2025, 1, 3))
    second = Interval(utc(2025, 1, 2), utc(2025, 1, 4))
    assert first.overlaps(second) is True


def test_clipping_keeps_only_the_part_inside_the_window():
    span = Interval(utc(2025, 1, 1), utc(2025, 1, 10))
    window = Interval(utc(2025, 1, 3), utc(2025, 1, 5))
    assert span.clip(window) == window


def test_clipping_something_wholly_outside_gives_nothing():
    span = Interval(utc(2025, 1, 1), utc(2025, 1, 2))
    window = Interval(utc(2025, 2, 1), utc(2025, 2, 5))
    assert span.clip(window).empty is True


def test_an_interval_shifts_whole():
    span = Interval(utc(2025, 1, 1), utc(2025, 1, 2))
    assert span.shift(timedelta(days=1)) == Interval(utc(2025, 1, 2), utc(2025, 1, 3))


def test_an_interval_prints_both_ends_in_utc():
    span = Interval(utc(2025, 1, 1, 6, 0), utc(2025, 1, 2, 7, 30))
    assert str(span) == "2025-01-01 06:00Z to 2025-01-02 07:30Z"
