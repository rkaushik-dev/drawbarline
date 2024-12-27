import pytest

from drawbarline.clock import Interval, utc
from drawbarline.errors import EventError
from drawbarline.events.kinds import EventKind
from drawbarline.events.record import Event
from drawbarline.events.spans import on_line_spans, on_track_spans, within
from drawbarline.events.stream import CarStream
from drawbarline.marks import CarMark, ReportingMark

CAR = CarMark.parse("DBL 104207")
CN = ReportingMark("CN")
ELS = ReportingMark("ELS")
CUTOFF = utc(2025, 2, 1)


def stream_of(*events):
    stream = CarStream(CAR)
    for event in events:
        stream.record(event)
    return stream


def received(day, road=CN):
    return Event(EventKind.RECEIVED, CAR, utc(2025, 1, day, 14), "ESC", road=road)


def delivered(day, road=CN):
    return Event(EventKind.DELIVERED, CAR, utc(2025, 1, day, 9), "MRN", road=road)


def placed(day, kind=EventKind.PLACED, industry="ESCPAP1"):
    return Event(kind, CAR, utc(2025, 1, day, 8), "ESC", industry=industry)


def released(day):
    return Event(EventKind.RELEASED, CAR, utc(2025, 1, day, 15), "ESC", industry="ESCPAP1")


def pulled(day):
    return Event(EventKind.PULLED, CAR, utc(2025, 1, day, 17), "ESC", industry="ESCPAP1")


def test_a_car_that_never_came_has_no_span():
    assert on_line_spans(CarStream(CAR), CUTOFF) == []


def test_a_receipt_and_a_delivery_make_one_span():
    spans = on_line_spans(stream_of(received(14), delivered(16)), CUTOFF)
    assert len(spans) == 1
    assert spans[0].interval == Interval(utc(2025, 1, 14, 14), utc(2025, 1, 16, 9))
    assert spans[0].open is False


def test_a_closed_span_remembers_both_roads():
    spans = on_line_spans(stream_of(received(14, CN), delivered(16, ELS)), CUTOFF)
    assert spans[0].received_from == CN
    assert spans[0].delivered_to == ELS


def test_a_car_still_here_has_an_open_span_to_the_cut_off():
    spans = on_line_spans(stream_of(received(14)), CUTOFF)
    assert spans[0].open is True
    assert spans[0].interval.end == CUTOFF


def test_two_visits_make_two_spans():
    stream = stream_of(
        received(14),
        delivered(16),
        Event(EventKind.RECEIVED, CAR, utc(2025, 1, 20, 14), "ESC", road=CN),
        Event(EventKind.DELIVERED, CAR, utc(2025, 1, 22, 9), "MRN", road=CN),
    )
    assert len(on_line_spans(stream, CUTOFF)) == 2


def test_a_second_receipt_with_no_delivery_between_is_refused():
    stream = stream_of(
        received(14),
        Event(EventKind.RECEIVED, CAR, utc(2025, 1, 20, 14), "ESC", road=CN),
    )
    with pytest.raises(EventError):
        on_line_spans(stream, CUTOFF)


def test_a_delivery_with_no_receipt_is_refused():
    with pytest.raises(EventError):
        on_line_spans(stream_of(delivered(16)), CUTOFF)


def test_a_cut_off_before_the_delivery_leaves_the_span_open():
    spans = on_line_spans(stream_of(received(14), delivered(16)), utc(2025, 1, 15))
    assert spans[0].open is True
    assert spans[0].interval.end == utc(2025, 1, 15)


def test_a_span_prints_its_ends_and_whether_it_is_open():
    span = on_line_spans(stream_of(received(14)), CUTOFF)[0]
    assert str(span).endswith("open")


def test_the_two_halves_of_a_split_span_add_to_the_whole():
    stream = stream_of(received(14), delivered(30))
    whole = on_line_spans(stream, CUTOFF)[0]
    first = Interval(utc(2025, 1, 1), utc(2025, 1, 20))
    second = Interval(utc(2025, 1, 20), utc(2025, 2, 1))
    halves = whole.clipped(first).minutes + whole.clipped(second).minutes
    assert halves == whole.minutes


def test_a_car_never_placed_has_no_track_span():
    assert on_track_spans(stream_of(received(14)), CUTOFF) == []


def test_a_placement_and_a_pull_make_one_track_span():
    stream = stream_of(received(14), placed(15), released(16), pulled(17))
    spans = on_track_spans(stream, CUTOFF)
    assert len(spans) == 1
    assert spans[0].industry == "ESCPAP1"
    assert spans[0].released_at == utc(2025, 1, 16, 15)


def test_a_car_still_on_the_track_has_an_open_span():
    spans = on_track_spans(stream_of(received(14), placed(15)), CUTOFF)
    assert spans[0].open is True
    assert spans[0].released_at is None


def test_a_constructive_placement_is_marked_as_one():
    stream = stream_of(received(14), placed(15, kind=EventKind.CONSTRUCTIVE))
    assert on_track_spans(stream, CUTOFF)[0].constructive is True


def test_an_actual_placement_is_not_constructive():
    stream = stream_of(received(14), placed(15))
    assert on_track_spans(stream, CUTOFF)[0].constructive is False


def test_a_car_placed_twice_running_is_refused():
    stream = stream_of(
        received(14),
        placed(15),
        Event(EventKind.PLACED, CAR, utc(2025, 1, 16, 8), "ESC", industry="ESCPAP2"),
    )
    with pytest.raises(EventError):
        on_track_spans(stream, CUTOFF)


def test_a_release_before_any_placement_is_ignored():
    assert on_track_spans(stream_of(received(14), released(15)), CUTOFF) == []


def test_two_placements_at_different_tracks_make_two_spans():
    stream = stream_of(
        received(14),
        placed(15),
        pulled(16),
        Event(EventKind.PLACED, CAR, utc(2025, 1, 17, 8), "ESC", industry="ESCPAP2"),
        Event(EventKind.PULLED, CAR, utc(2025, 1, 19, 17), "ESC", industry="ESCPAP2"),
    )
    assert [s.industry for s in on_track_spans(stream, CUTOFF)] == ["ESCPAP1", "ESCPAP2"]


def test_only_the_spans_touching_a_window_come_back():
    stream = stream_of(received(14), delivered(16))
    spans = on_line_spans(stream, CUTOFF)
    february = Interval(utc(2025, 2, 1), utc(2025, 3, 1))
    assert within(spans, february) == []


def test_a_span_that_touches_a_window_is_cut_down_to_it():
    stream = stream_of(received(14), delivered(30))
    spans = on_line_spans(stream, CUTOFF)
    week = Interval(utc(2025, 1, 20), utc(2025, 1, 27))
    assert within(spans, week)[0].interval == week
