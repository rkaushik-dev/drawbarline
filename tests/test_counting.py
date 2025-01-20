from datetime import date

import pytest

from drawbarline.clock import utc
from drawbarline.demurrage.counting import clock_starts_on, count, free_time_ends
from drawbarline.demurrage.tariff import Step, Tariff
from drawbarline.errors import TariffError
from drawbarline.events.kinds import EventKind
from drawbarline.events.record import Event
from drawbarline.events.spans import on_track_spans
from drawbarline.events.stream import CarStream
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.money import Money
from drawbarline.reference import drawbar_line

CAR = CarMark.parse("CN 400100")
CN = ReportingMark("CN")
LINE = drawbar_line()
ESC = LINE.station("ESC")
CUTOFF = utc(2025, 2, 1, 6)

TARIFF = Tariff(
    code="4200",
    effective=date(2024, 7, 1),
    free_days_loaded=2,
    free_days_empty=1,
    steps=(Step(2, 10000), Step(4, 15000), Step(0, 20000)),
)


def track_span(placed, released=None, pulled=None, kind=EventKind.PLACED):
    stream = CarStream(CAR)
    stream.record(Event(EventKind.RECEIVED, CAR, utc(2025, 1, 2, 12), "ESC", road=CN))
    stream.record(Event(kind, CAR, placed, "ESC", industry="ESCPAP1"))
    if released:
        stream.record(Event(EventKind.RELEASED, CAR, released, "ESC", industry="ESCPAP1"))
    if pulled:
        stream.record(Event(EventKind.PULLED, CAR, pulled, "ESC", industry="ESCPAP1"))
    return on_track_spans(stream, CUTOFF)[0]


def test_the_clock_starts_the_next_chargeable_day():
    assert clock_starts_on(date(2025, 1, 14)) == date(2025, 1, 15)


def test_a_friday_placement_starts_the_clock_on_monday():
    assert clock_starts_on(date(2025, 1, 17)) == date(2025, 1, 20)


def test_a_placement_before_a_holiday_skips_it():
    assert clock_starts_on(date(2025, 12, 24)) == date(2025, 12, 26)


def test_two_free_days_run_out_on_the_second_one():
    assert free_time_ends(date(2025, 1, 15), 2) == date(2025, 1, 16)


def test_free_days_walk_over_the_weekend():
    assert free_time_ends(date(2025, 1, 16), 2) == date(2025, 1, 17)


def test_free_days_walk_past_a_weekend_when_they_have_to():
    assert free_time_ends(date(2025, 1, 17), 2) == date(2025, 1, 20)


def test_a_tariff_with_no_free_time_has_no_last_free_day():
    assert free_time_ends(date(2025, 1, 15), 0) is None


def test_negative_free_time_is_refused():
    with pytest.raises(TariffError):
        free_time_ends(date(2025, 1, 15), -1)


def test_a_car_released_inside_free_time_owes_nothing():
    span = track_span(utc(2025, 1, 14, 20), released=utc(2025, 1, 16, 15))
    detention = count(span, TARIFF, ESC, loaded=True)
    assert detention.chargeable == 0
    assert detention.still_free is True


def test_a_car_released_the_day_after_free_time_owes_one_day():
    span = track_span(utc(2025, 1, 14, 20), released=utc(2025, 1, 17, 15))
    assert count(span, TARIFF, ESC, loaded=True).chargeable == 1


def test_a_weekend_in_the_middle_is_not_charged():
    span = track_span(utc(2025, 1, 14, 20), released=utc(2025, 1, 21, 15))
    assert count(span, TARIFF, ESC, loaded=True).chargeable == 3


def test_an_empty_gets_less_free_time():
    span = track_span(utc(2025, 1, 14, 20), released=utc(2025, 1, 17, 15))
    assert count(span, TARIFF, ESC, loaded=False).chargeable == 2


def test_the_placement_day_reads_local_at_the_station():
    span = track_span(utc(2025, 1, 15, 4), released=utc(2025, 1, 20, 15))
    assert count(span, TARIFF, ESC, loaded=True).placed_on == date(2025, 1, 14)


def test_a_car_still_on_the_track_counts_to_the_cut_off():
    span = track_span(utc(2025, 1, 14, 20))
    detention = count(span, TARIFF, ESC, loaded=True)
    assert detention.counted_to == date(2025, 2, 1)


def test_a_pull_without_a_release_counts_to_the_pull():
    span = track_span(utc(2025, 1, 14, 20), pulled=utc(2025, 1, 20, 15))
    assert count(span, TARIFF, ESC, loaded=True).counted_to == date(2025, 1, 20)


def test_a_release_stops_the_clock_even_though_the_car_sits_on():
    span = track_span(
        utc(2025, 1, 14, 20), released=utc(2025, 1, 17, 15), pulled=utc(2025, 1, 24, 15)
    )
    assert count(span, TARIFF, ESC, loaded=True).counted_to == date(2025, 1, 17)


def test_a_constructive_placement_is_carried_through():
    span = track_span(utc(2025, 1, 14, 20), kind=EventKind.CONSTRUCTIVE)
    assert count(span, TARIFF, ESC, loaded=True).constructive is True


def test_the_detention_carries_the_free_time_it_allowed():
    span = track_span(utc(2025, 1, 14, 20), released=utc(2025, 1, 21, 15))
    detention = count(span, TARIFF, ESC, loaded=True)
    assert detention.clock_starts == date(2025, 1, 15)
    assert detention.free_until == date(2025, 1, 16)


def test_a_tariff_with_no_free_time_charges_from_the_first_day():
    hard = Tariff("4900", date(2024, 7, 1), 0, 0, (Step(0, 20000),))
    span = track_span(utc(2025, 1, 14, 20), released=utc(2025, 1, 16, 15))
    assert count(span, hard, ESC, loaded=True).chargeable == 2


def test_the_charge_comes_off_the_tariff():
    span = track_span(utc(2025, 1, 14, 20), released=utc(2025, 1, 21, 15))
    assert count(span, TARIFF, ESC, loaded=True).charge(TARIFF) == Money(35000)


def test_a_detention_inside_free_time_prints_as_free():
    span = track_span(utc(2025, 1, 14, 20), released=utc(2025, 1, 16, 15))
    assert str(count(span, TARIFF, ESC, loaded=True)).endswith("inside free time")


def test_a_charged_detention_prints_its_days():
    span = track_span(utc(2025, 1, 14, 20), released=utc(2025, 1, 21, 15))
    assert str(count(span, TARIFF, ESC, loaded=True)).endswith("3 chargeable days")


def test_a_car_released_on_a_saturday_is_counted_to_that_saturday():
    span = track_span(utc(2025, 1, 14, 20), released=utc(2025, 1, 18, 15))
    detention = count(span, TARIFF, ESC, loaded=True)
    assert detention.counted_to == date(2025, 1, 18)
    assert detention.chargeable == 1
