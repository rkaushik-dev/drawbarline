from datetime import datetime

import pytest

from drawbarline.clock import ClockError, utc
from drawbarline.errors import EventError
from drawbarline.events.kinds import EventKind
from drawbarline.events.record import WITHIN_A_MINUTE, Event
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.mileage import MileageError

CAR = CarMark.parse("DBL 104207")
CN = ReportingMark("CN")


def an_event(**changed):
    fields = {
        "kind": EventKind.RECEIVED,
        "car": CAR,
        "when": utc(2025, 1, 14, 14, 30),
        "station": "ESC",
        "road": CN,
    }
    fields.update(changed)
    return Event(**fields)


def test_an_interchange_event_prints_the_road():
    assert str(an_event()) == "2025-01-14 14:30Z DBL 104207 received at ESC CN"


def test_a_placement_prints_the_track_instead_of_the_station():
    event = an_event(kind=EventKind.PLACED, road=None, industry="ESCPAP1")
    assert str(event).endswith("placed at ESCPAP1")


def test_a_naive_moment_is_refused():
    with pytest.raises(ClockError):
        an_event(when=datetime(2025, 1, 14, 14, 30))


def test_a_station_we_do_not_serve_is_refused():
    with pytest.raises(MileageError):
        an_event(station="CHI")


def test_an_interchange_without_a_road_is_refused():
    with pytest.raises(EventError):
        an_event(road=None)


def test_a_road_on_an_event_that_has_no_road_is_refused():
    with pytest.raises(EventError):
        an_event(kind=EventKind.WEIGHED)


def test_a_placement_without_a_track_is_refused():
    with pytest.raises(EventError):
        an_event(kind=EventKind.PLACED, road=None)


def test_a_track_on_an_event_that_has_no_track_is_refused():
    with pytest.raises(EventError):
        an_event(industry="ESCPAP1")


def test_a_movement_carries_its_distance():
    event = an_event(kind=EventKind.MOVED, road=None, tenths=336)
    assert event.tenths == 336


def test_a_movement_without_a_distance_is_refused():
    with pytest.raises(EventError):
        an_event(kind=EventKind.MOVED, road=None)


def test_a_movement_that_covers_no_ground_is_refused():
    with pytest.raises(EventError):
        an_event(kind=EventKind.MOVED, road=None, tenths=0)


def test_a_distance_on_something_that_is_not_a_movement_is_refused():
    with pytest.raises(EventError):
        an_event(tenths=336)


def test_a_waybill_number_below_one_is_refused():
    with pytest.raises(EventError):
        an_event(waybill=0)


def test_a_waybill_number_rides_along():
    assert an_event(waybill=41207).waybill == 41207


def test_a_note_longer_than_the_field_is_refused():
    with pytest.raises(EventError):
        an_event(note="x" * 121)


def test_a_note_at_the_limit_is_allowed():
    assert len(an_event(note="x" * 120).note) == 120


def test_a_note_can_be_added_to_a_copy():
    noted = an_event().with_note("keyed from the wrong sheet")
    assert noted.note == "keyed from the wrong sheet"
    assert noted.when == an_event().when


def test_adding_a_note_leaves_the_original_alone():
    original = an_event()
    original.with_note("something")
    assert original.note == ""


def test_the_sort_key_leads_with_the_moment():
    assert an_event().order[0] == utc(2025, 1, 14, 14, 30)


def test_every_kind_has_a_place_in_the_minute():
    assert set(WITHIN_A_MINUTE) == set(EventKind)


def test_the_ranks_within_a_minute_are_all_different():
    assert len(set(WITHIN_A_MINUTE.values())) == len(EventKind)


def test_a_release_sorts_before_a_pull_in_the_same_minute():
    released = an_event(
        kind=EventKind.RELEASED, road=None, industry="ESCPAP1", when=utc(2025, 1, 14, 8, 0)
    )
    pulled = an_event(
        kind=EventKind.PULLED, road=None, industry="ESCPAP1", when=utc(2025, 1, 14, 8, 0)
    )
    assert sorted([pulled, released], key=lambda e: e.order) == [released, pulled]


def test_a_receipt_sorts_before_a_delivery_in_the_same_minute():
    received = an_event(when=utc(2025, 1, 14, 8, 0))
    delivered = an_event(kind=EventKind.DELIVERED, when=utc(2025, 1, 14, 8, 0))
    assert sorted([delivered, received], key=lambda e: e.order) == [received, delivered]


def test_two_cars_at_the_same_moment_sort_by_mark():
    first = an_event(car=CarMark.parse("CN 1"), when=utc(2025, 1, 14, 8, 0))
    second = an_event(car=CarMark.parse("DBL 1"), when=utc(2025, 1, 14, 8, 0))
    assert sorted([second, first], key=lambda e: e.order) == [first, second]


def test_two_records_of_the_same_event_compare_equal():
    assert an_event() == an_event()
