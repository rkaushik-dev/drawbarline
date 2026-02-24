from datetime import timedelta

import pytest

from drawbarline.clock import utc
from drawbarline.events.kinds import EventKind
from drawbarline.events.record import Event
from drawbarline.interchange.reconcile import compare, key_of
from drawbarline.marks import CarMark, ReportingMark

CN = ReportingMark("CN")


def received(number=400100, hour=12, minute=0, station="ESC"):
    return Event(
        EventKind.RECEIVED,
        CarMark.parse(f"CN {number}"),
        utc(2025, 1, 6, hour, minute),
        station,
        road=CN,
    )


def delivered(number=400100, hour=9):
    return Event(
        EventKind.DELIVERED,
        CarMark.parse(f"CN {number}"),
        utc(2025, 1, 16, hour),
        "MRN",
        road=CN,
    )


def test_the_key_leaves_the_clock_out():
    assert key_of(received(hour=12)) == key_of(received(hour=13))


def test_the_key_separates_two_cars():
    assert key_of(received(400100)) != key_of(received(400101))


def test_the_key_separates_two_kinds():
    assert key_of(received()) != key_of(delivered())


def test_the_key_separates_two_days():
    later = Event(
        EventKind.RECEIVED, CarMark.parse("CN 400100"), utc(2025, 1, 7, 12), "ESC", road=CN
    )
    assert key_of(received()) != key_of(later)


def test_two_matching_records_agree():
    found = compare([received()], [received()])
    assert found.clean is True
    assert len(found.agreed) == 1


def test_a_record_only_we_have_shows_up():
    found = compare([received(), received(400101)], [received()])
    assert [str(one.car) for one in found.only_ours] == ["CN 400101"]


def test_a_record_only_they_have_shows_up():
    found = compare([received()], [received(), received(400101)])
    assert [str(one.car) for one in found.only_theirs] == ["CN 400101"]


def test_a_small_difference_in_time_still_agrees():
    found = compare([received(hour=12)], [received(hour=12, minute=20)])
    assert found.clean is True


def test_a_difference_past_the_tolerance_is_reported():
    found = compare([received(hour=12)], [received(hour=14)])
    assert len(found.timings) == 1
    assert found.timings[0].minutes_apart == 120


def test_a_timing_says_which_way_round_it_is():
    found = compare([received(hour=14)], [received(hour=12)])
    assert found.timings[0].we_are_later is True
    assert "later" in str(found.timings[0])


def test_a_timing_the_other_way_says_earlier():
    found = compare([received(hour=12)], [received(hour=14)])
    assert found.timings[0].we_are_later is False
    assert "earlier" in str(found.timings[0])


def test_the_tolerance_can_be_tightened():
    found = compare(
        [received(hour=12)], [received(hour=12, minute=20)], tolerance=timedelta(minutes=5)
    )
    assert len(found.timings) == 1


def test_a_negative_tolerance_is_refused():
    from drawbarline.interchange.reconcile import ReconcileError

    with pytest.raises(ReconcileError):
        compare([], [], tolerance=timedelta(minutes=-1))


def test_the_worst_timing_comes_back():
    found = compare(
        [received(400100, hour=12), received(400101, hour=12)],
        [received(400100, hour=13), received(400101, hour=18)],
    )
    assert str(found.worst_timing().ours.car) == "CN 400101"


def test_nothing_out_of_time_has_no_worst():
    assert compare([received()], [received()]).worst_timing() is None


def test_two_records_of_the_same_handoff_pair_up_one_for_one():
    ours = [received(hour=12), received(hour=13)]
    theirs = [received(hour=12), received(hour=13)]
    found = compare(ours, theirs)
    assert len(found.agreed) == 2
    assert found.only_ours == []


def test_a_duplicate_on_one_side_shows_as_only_theirs():
    found = compare([received()], [received(), received()])
    assert len(found.agreed) == 1
    assert len(found.only_theirs) == 1


def test_a_clean_comparison_prints_as_agreeing():
    assert str(compare([received()], [received()])) == "1 records, both sides agree"


def test_a_dirty_comparison_prints_the_counts():
    found = compare([received(), received(400101)], [received(), received(400102)])
    printed = str(found)
    assert "1 only ours" in printed
    assert "1 only theirs" in printed


def test_the_difference_count_adds_the_three_kinds():
    found = compare(
        [received(), received(400101), received(400103, hour=12)],
        [received(), received(400102), received(400103, hour=20)],
    )
    assert found.differences == 3


def test_two_empty_records_agree_on_nothing():
    found = compare([], [])
    assert found.clean is True
    assert found.differences == 0


def test_records_only_we_have_come_back_in_order():
    found = compare([received(400103), received(400101)], [])
    assert [str(one.car) for one in found.only_ours] == ["CN 400101", "CN 400103"]
