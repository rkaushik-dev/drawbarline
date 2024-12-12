import pytest

from drawbarline.clock import Interval, utc
from drawbarline.errors import EventError
from drawbarline.events.kinds import EventKind
from drawbarline.events.record import Event
from drawbarline.events.stream import CarStream, EventLog
from drawbarline.marks import CarMark, ReportingMark

CAR = CarMark.parse("DBL 104207")
OTHER = CarMark.parse("CN 400100")
CN = ReportingMark("CN")


def received(when, car=CAR, station="ESC"):
    return Event(EventKind.RECEIVED, car, when, station, road=CN)


def delivered(when, car=CAR, station="MRN"):
    return Event(EventKind.DELIVERED, car, when, station, road=CN)


def placed(when, car=CAR, industry="ESCPAP1"):
    return Event(EventKind.PLACED, car, when, "ESC", industry=industry)


def test_an_empty_stream_holds_nothing():
    stream = CarStream(CAR)
    assert len(stream) == 0
    assert stream.first is None
    assert stream.last is None


def test_an_event_goes_on_and_comes_back():
    stream = CarStream(CAR)
    event = stream.record(received(utc(2025, 1, 14, 14, 0)))
    assert list(stream) == [event]


def test_events_come_back_in_the_order_they_happened():
    stream = CarStream(CAR)
    stream.record(delivered(utc(2025, 1, 16, 9, 0)))
    stream.record(received(utc(2025, 1, 14, 14, 0)))
    stream.record(placed(utc(2025, 1, 15, 8, 0)))
    assert [e.kind for e in stream] == [
        EventKind.RECEIVED,
        EventKind.PLACED,
        EventKind.DELIVERED,
    ]


def test_a_late_arrival_lands_in_its_own_place():
    stream = CarStream(CAR)
    stream.record(received(utc(2025, 1, 14, 14, 0)))
    stream.record(delivered(utc(2025, 1, 16, 9, 0)))
    stream.record(placed(utc(2025, 1, 15, 8, 0)))
    assert stream.first.kind is EventKind.RECEIVED
    assert stream.last.kind is EventKind.DELIVERED


def test_an_event_for_another_car_is_refused():
    stream = CarStream(CAR)
    with pytest.raises(EventError):
        stream.record(received(utc(2025, 1, 14, 14, 0), car=OTHER))


def test_the_same_event_twice_is_refused():
    stream = CarStream(CAR)
    event = received(utc(2025, 1, 14, 14, 0))
    stream.record(event)
    with pytest.raises(EventError):
        stream.record(event)


def test_a_second_copy_of_a_sheet_is_refused_even_when_rebuilt():
    stream = CarStream(CAR)
    stream.record(received(utc(2025, 1, 14, 14, 0)))
    with pytest.raises(EventError):
        stream.record(received(utc(2025, 1, 14, 14, 0)))


def test_the_same_kind_at_a_different_minute_is_a_different_event():
    stream = CarStream(CAR)
    stream.record(received(utc(2025, 1, 14, 14, 0)))
    stream.record(received(utc(2025, 1, 20, 14, 0)))
    assert len(stream) == 2


def test_events_come_back_by_kind():
    stream = CarStream(CAR)
    stream.record(received(utc(2025, 1, 14, 14, 0)))
    stream.record(received(utc(2025, 2, 1, 14, 0)))
    stream.record(delivered(utc(2025, 1, 16, 9, 0)))
    assert len(stream.of_kind(EventKind.RECEIVED)) == 2


def test_the_last_of_a_kind_is_the_latest_one():
    stream = CarStream(CAR)
    stream.record(received(utc(2025, 1, 14, 14, 0)))
    stream.record(received(utc(2025, 2, 1, 14, 0)))
    assert stream.last_of(EventKind.RECEIVED).when == utc(2025, 2, 1, 14, 0)


def test_the_first_of_a_kind_is_the_earliest_one():
    stream = CarStream(CAR)
    stream.record(received(utc(2025, 2, 1, 14, 0)))
    stream.record(received(utc(2025, 1, 14, 14, 0)))
    assert stream.first_of(EventKind.RECEIVED).when == utc(2025, 1, 14, 14, 0)


def test_a_kind_that_never_happened_has_no_last():
    assert CarStream(CAR).last_of(EventKind.BAD_ORDERED) is None


def test_events_come_back_for_a_window():
    stream = CarStream(CAR)
    stream.record(received(utc(2025, 1, 14, 14, 0)))
    stream.record(delivered(utc(2025, 2, 16, 9, 0)))
    january = Interval(utc(2025, 1, 1), utc(2025, 2, 1))
    assert [e.kind for e in stream.during(january)] == [EventKind.RECEIVED]


def test_everything_up_to_a_window_end_comes_back():
    stream = CarStream(CAR)
    stream.record(received(utc(2024, 12, 30, 14, 0)))
    stream.record(placed(utc(2025, 1, 15, 8, 0)))
    stream.record(delivered(utc(2025, 2, 16, 9, 0)))
    january = Interval(utc(2025, 1, 1), utc(2025, 2, 1))
    assert len(stream.up_to(january)) == 2


def test_the_log_starts_empty():
    assert len(EventLog()) == 0
    assert EventLog().cars() == []


def test_the_log_opens_a_stream_for_a_new_car():
    log = EventLog()
    log.record(received(utc(2025, 1, 14, 14, 0)))
    assert log.has(CAR) is True
    assert len(log.stream(CAR)) == 1


def test_a_car_with_nothing_recorded_reads_as_an_empty_stream():
    log = EventLog()
    assert len(log.stream(CAR)) == 0
    assert log.has(CAR) is False


def test_the_log_keeps_cars_apart():
    log = EventLog()
    log.record(received(utc(2025, 1, 14, 14, 0)))
    log.record(received(utc(2025, 1, 14, 14, 0), car=OTHER))
    assert log.cars() == [OTHER, CAR]
    assert len(log) == 2


def test_a_batch_records_in_one_call():
    log = EventLog()
    assert log.record_all([received(utc(2025, 1, 14, 14, 0)), placed(utc(2025, 1, 15, 8, 0))]) == 2
    assert len(log) == 2


def test_the_log_gathers_a_window_across_cars():
    log = EventLog()
    log.record(received(utc(2025, 1, 14, 14, 0)))
    log.record(received(utc(2025, 1, 14, 12, 0), car=OTHER))
    january = Interval(utc(2025, 1, 1), utc(2025, 2, 1))
    assert [e.car for e in log.during(january)] == [OTHER, CAR]


def test_the_log_gathers_by_station():
    log = EventLog()
    log.record(received(utc(2025, 1, 14, 14, 0)))
    log.record(delivered(utc(2025, 1, 16, 9, 0)))
    assert [e.station for e in log.at_station("ESC")] == ["ESC"]


def test_the_log_gathers_by_kind():
    log = EventLog()
    log.record(received(utc(2025, 1, 14, 14, 0)))
    log.record(received(utc(2025, 1, 14, 12, 0), car=OTHER))
    assert len(log.of_kind(EventKind.RECEIVED)) == 2


def test_iterating_a_stream_does_not_expose_the_list_it_keeps():
    stream = CarStream(CAR)
    stream.record(received(utc(2025, 1, 14, 14, 0)))
    taken = list(stream)
    taken.clear()
    assert len(stream) == 1
