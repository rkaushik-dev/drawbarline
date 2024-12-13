import pytest

from drawbarline.clock import utc
from drawbarline.errors import EventError
from drawbarline.events.kinds import EventKind
from drawbarline.events.record import Event
from drawbarline.events.replay import CarState, replay, state_of
from drawbarline.events.stream import CarStream
from drawbarline.marks import CarMark, ReportingMark

CAR = CarMark.parse("DBL 104207")
CN = ReportingMark("CN")


def stream_of(*events):
    stream = CarStream(CAR)
    for event in events:
        stream.record(event)
    return stream


def received(hour, station="ESC", waybill=41207):
    return Event(EventKind.RECEIVED, CAR, utc(2025, 1, 14, hour), station, road=CN, waybill=waybill)


def delivered(day, station="MRN"):
    return Event(EventKind.DELIVERED, CAR, utc(2025, 1, day, 9), station, road=CN)


def moved(day, station, tenths):
    return Event(EventKind.MOVED, CAR, utc(2025, 1, day, 6), station, tenths=tenths)


def placed(day, hour=8, industry="ESCPAP1", kind=EventKind.PLACED):
    return Event(kind, CAR, utc(2025, 1, day, hour), "ESC", industry=industry)


def released(day, hour=15, industry="ESCPAP1"):
    return Event(EventKind.RELEASED, CAR, utc(2025, 1, day, hour), "ESC", industry=industry)


def pulled(day, hour=17, industry="ESCPAP1"):
    return Event(EventKind.PULLED, CAR, utc(2025, 1, day, hour), "ESC", industry=industry)


def simple(day, kind, station="ESC"):
    return Event(kind, CAR, utc(2025, 1, day, 10), station)


def test_a_car_nothing_has_happened_to_is_off_line():
    state = replay(CarStream(CAR))
    assert state.on_line is False
    assert state.applied == 0
    assert str(state) == "DBL 104207 off line"


def test_a_received_car_is_on_our_rail():
    state = replay(stream_of(received(14)))
    assert state.on_line is True
    assert state.station == "ESC"
    assert state.received_at == utc(2025, 1, 14, 14)
    assert state.waybill == 41207


def test_a_car_received_twice_is_refused():
    with pytest.raises(EventError):
        replay(stream_of(received(14), received(16)))


def test_an_event_before_the_car_arrives_is_refused():
    with pytest.raises(EventError) as caught:
        replay(stream_of(simple(15, EventKind.NOTIFIED)))
    assert "not on our rail" in caught.value.message


def test_a_delivered_car_goes_off_line():
    state = replay(stream_of(received(14), delivered(16)))
    assert state.on_line is False
    assert state.waybill is None
    assert state.station == "MRN"


def test_a_car_delivered_twice_is_refused():
    with pytest.raises(EventError):
        replay(stream_of(received(14), delivered(16), delivered(17)))


def test_a_placed_car_carries_its_track():
    state = replay(stream_of(received(14), placed(15)))
    assert state.industry == "ESCPAP1"
    assert state.placed is True
    assert str(state) == "DBL 104207 at ESCPAP1"


def test_a_constructive_placement_places_the_car_too():
    state = replay(stream_of(received(14), placed(15, kind=EventKind.CONSTRUCTIVE)))
    assert state.placed is True
    assert state.placed_at == utc(2025, 1, 15, 8)


def test_a_car_placed_twice_is_refused():
    with pytest.raises(EventError):
        replay(stream_of(received(14), placed(15), placed(16)))


def test_a_release_without_a_placement_is_refused():
    with pytest.raises(EventError):
        replay(stream_of(received(14), released(15)))


def test_a_released_car_is_waiting_to_be_pulled():
    state = replay(stream_of(received(14), placed(15), released(16)))
    assert state.awaiting_pull is True
    assert state.released_at == utc(2025, 1, 16, 15)


def test_a_car_released_twice_is_refused():
    with pytest.raises(EventError):
        replay(stream_of(received(14), placed(15), released(16), released(17)))


def test_a_pull_clears_the_track():
    state = replay(stream_of(received(14), placed(15), released(16), pulled(17)))
    assert state.placed is False
    assert state.industry is None
    assert state.awaiting_pull is False


def test_a_pull_with_nothing_on_the_track_is_refused():
    with pytest.raises(EventError):
        replay(stream_of(received(14), pulled(15)))


def test_a_car_still_on_a_track_cannot_be_delivered():
    with pytest.raises(EventError) as caught:
        replay(stream_of(received(14), placed(15), delivered(16)))
    assert "pulled before it goes across" in caught.value.message


def test_a_car_still_on_a_track_cannot_be_moved():
    with pytest.raises(EventError):
        replay(stream_of(received(14), placed(15), moved(16, "PWR", 336)))


def test_a_movement_adds_up_the_miles():
    state = replay(stream_of(received(14), moved(15, "PWR", 336), moved(16, "IMT", 286)))
    assert state.tenths_run == 622
    assert state.station == "IMT"


def test_a_bad_ordered_car_does_not_move():
    state = replay(stream_of(received(14), simple(15, EventKind.BAD_ORDERED)))
    assert state.bad_ordered is True
    assert state.movable is False


def test_a_bad_ordered_car_cannot_be_delivered():
    with pytest.raises(EventError):
        replay(stream_of(received(14), simple(15, EventKind.BAD_ORDERED), delivered(16)))


def test_a_car_bad_ordered_twice_is_refused():
    with pytest.raises(EventError):
        replay(
            stream_of(
                received(14),
                simple(15, EventKind.BAD_ORDERED),
                Event(EventKind.BAD_ORDERED, CAR, utc(2025, 1, 16, 11), "ESC"),
            )
        )


def test_a_repair_puts_the_car_back_in_service():
    state = replay(
        stream_of(
            received(14),
            simple(15, EventKind.BAD_ORDERED),
            simple(16, EventKind.REPAIRED),
        )
    )
    assert state.bad_ordered is False
    assert state.movable is True


def test_a_repair_on_a_car_that_was_not_bad_ordered_is_refused():
    with pytest.raises(EventError):
        replay(stream_of(received(14), simple(15, EventKind.REPAIRED)))


def test_a_held_car_does_not_move():
    state = replay(stream_of(received(14), simple(15, EventKind.HELD)))
    assert state.held is True
    assert state.movable is False


def test_freeing_a_car_that_was_not_held_is_refused():
    with pytest.raises(EventError):
        replay(stream_of(received(14), simple(15, EventKind.FREED)))


def test_a_freed_car_moves_again():
    state = replay(stream_of(received(14), simple(15, EventKind.HELD), simple(16, EventKind.FREED)))
    assert state.held is False
    assert state.movable is True


def test_a_replay_can_stop_part_way():
    stream = stream_of(received(14), placed(15), released(16), pulled(17))
    state = replay(stream, until=utc(2025, 1, 15, 12))
    assert state.placed is True
    assert state.applied == 2


def test_a_second_cycle_starts_clean():
    stream = stream_of(
        received(14),
        placed(15),
        released(16),
        pulled(17),
        delivered(18),
        Event(EventKind.RECEIVED, CAR, utc(2025, 1, 20, 14), "ESC", road=CN, waybill=41300),
    )
    state = replay(stream)
    assert state.on_line is True
    assert state.waybill == 41300
    assert state.placed is False


def test_the_state_counts_what_it_applied():
    assert replay(stream_of(received(14), placed(15))).applied == 2


def test_asking_the_wrong_stream_for_a_car_is_refused():
    with pytest.raises(EventError):
        state_of(stream_of(received(14)), CarMark.parse("CN 400100"))


def test_asking_the_right_stream_replays_it():
    assert state_of(stream_of(received(14)), CAR).on_line is True


def test_a_state_is_frozen():
    state = CarState(CAR)
    with pytest.raises(AttributeError):
        state.on_line = True
