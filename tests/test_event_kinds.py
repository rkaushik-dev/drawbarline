import pytest

from drawbarline.errors import EventError
from drawbarline.events.kinds import EventKind, kind_named


def test_every_kind_carries_readable_text():
    assert all(kind.text for kind in EventKind)


def test_a_kind_prints_as_its_lower_case_name():
    assert str(EventKind.BAD_ORDERED) == "bad_ordered"


def test_kind_texts_are_all_different():
    texts = [kind.text for kind in EventKind]
    assert len(texts) == len(set(texts))


def test_a_kind_reads_back_off_its_name():
    assert kind_named("received") is EventKind.RECEIVED


def test_a_kind_reads_back_with_surrounding_space():
    assert kind_named("  BAD_ORDERED ") is EventKind.BAD_ORDERED


def test_a_name_no_kind_carries_is_refused():
    with pytest.raises(EventError):
        kind_named("shunted")


def test_only_interchange_names_a_road():
    assert {k for k in EventKind if k.needs_road} == {
        EventKind.RECEIVED,
        EventKind.DELIVERED,
    }


def test_the_industry_events_name_a_track():
    assert {k for k in EventKind if k.needs_industry} == {
        EventKind.PLACED,
        EventKind.CONSTRUCTIVE,
        EventKind.RELEASED,
        EventKind.PULLED,
    }


def test_only_a_movement_carries_a_distance():
    assert {k for k in EventKind if k.needs_distance} == {EventKind.MOVED}


def test_car_hire_starts_when_we_take_the_car():
    assert EventKind.RECEIVED.starts_car_hire is True
    assert EventKind.DELIVERED.starts_car_hire is False


def test_car_hire_stops_when_we_hand_it_on():
    assert EventKind.DELIVERED.ends_car_hire is True
    assert EventKind.RECEIVED.ends_car_hire is False


def test_exactly_one_kind_starts_car_hire():
    assert len([k for k in EventKind if k.starts_car_hire]) == 1


def test_exactly_one_kind_ends_car_hire():
    assert len([k for k in EventKind if k.ends_car_hire]) == 1


def test_both_placements_start_demurrage():
    assert EventKind.PLACED.starts_demurrage is True
    assert EventKind.CONSTRUCTIVE.starts_demurrage is True


def test_a_pull_does_not_start_demurrage():
    assert EventKind.PULLED.starts_demurrage is False


def test_only_a_release_ends_demurrage():
    assert {k for k in EventKind if k.ends_demurrage} == {EventKind.RELEASED}


def test_the_placements_are_the_two_that_start_demurrage():
    placements = {k for k in EventKind if k.is_placement}
    assert placements == {k for k in EventKind if k.starts_demurrage}


def test_interchange_events_are_the_ones_that_name_a_road():
    assert {k for k in EventKind if k.is_interchange} == {k for k in EventKind if k.needs_road}


def test_a_hold_neither_starts_nor_stops_anything_billable():
    held = EventKind.HELD
    assert not held.starts_car_hire
    assert not held.ends_car_hire
    assert not held.starts_demurrage
    assert not held.ends_demurrage


def test_the_list_of_kinds_is_short_enough_to_read():
    assert len(EventKind) == 13
