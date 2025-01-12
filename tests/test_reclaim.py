from datetime import date

import pytest

from drawbarline.carhire.accrual import hourly
from drawbarline.carhire.rates import Band, Rate, RateTable
from drawbarline.carhire.reclaim import (
    Reclaim,
    ReclaimReason,
    embargo_holds,
    net_of_reclaims,
    paired_spans,
    reclaims_for,
    repair_holds,
)
from drawbarline.clock import Interval, utc
from drawbarline.errors import EventError
from drawbarline.events.kinds import EventKind
from drawbarline.events.record import Event
from drawbarline.events.stream import CarStream
from drawbarline.fleet.aar import CarTypeCode, MechanicalDesignation
from drawbarline.fleet.car import Car
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.measure import Length
from drawbarline.money import Money
from drawbarline.weight import Weight

CAR = CarMark.parse("CN 400100")
CN = ReportingMark("CN")
HOPPER = CarTypeCode("L351")
JANUARY = Interval(utc(2025, 1, 1, 6), utc(2025, 2, 1, 6))


def a_car():
    return Car(
        mark=CAR,
        designation=MechanicalDesignation("LO"),
        type_code=HOPPER,
        built=date(2020, 6, 1),
        light_weight=Weight(63500),
        gross_rail_load=Weight(286000),
        outside_length=Length.of(59, 6),
    )


def a_table():
    table = RateTable()
    table.add(Band(HOPPER, 0, 9, date(2024, 7, 1), Rate(4630, 1210)))
    return table


def stream_of(*events):
    stream = CarStream(CAR)
    for event in events:
        stream.record(event)
    return stream


def at(day, hour, kind, **extra):
    return Event(kind, CAR, utc(2025, 1, day, hour), "ESC", **extra)


def a_visit(*inner):
    return stream_of(
        at(5, 12, EventKind.RECEIVED, road=CN),
        *inner,
        at(25, 12, EventKind.DELIVERED, road=CN),
    )


def test_a_reason_carries_its_rule_number():
    assert ReclaimReason.REPAIRS.rule == "rule 5"
    assert ReclaimReason.EMBARGO.rule == "rule 24"


def test_every_reason_has_readable_text():
    assert all(reason.text for reason in ReclaimReason)


def test_the_rule_numbers_are_all_different():
    assert len({reason.value for reason in ReclaimReason}) == len(ReclaimReason)


def test_a_bad_order_and_a_repair_make_one_hold():
    stream = a_visit(at(10, 8, EventKind.BAD_ORDERED), at(14, 8, EventKind.REPAIRED))
    assert repair_holds(stream, JANUARY.end) == [Interval(utc(2025, 1, 10, 8), utc(2025, 1, 14, 8))]


def test_a_car_still_bad_ordered_holds_to_the_cut_off():
    stream = stream_of(at(5, 12, EventKind.RECEIVED, road=CN), at(10, 8, EventKind.BAD_ORDERED))
    assert repair_holds(stream, JANUARY.end)[0].end == JANUARY.end


def test_a_car_never_bad_ordered_has_no_hold():
    assert repair_holds(a_visit(), JANUARY.end) == []


def test_an_embargo_makes_its_own_hold():
    stream = a_visit(at(10, 8, EventKind.HELD), at(12, 8, EventKind.FREED))
    assert len(embargo_holds(stream, JANUARY.end)) == 1


def test_two_holds_of_the_same_kind_come_back_separately():
    stream = a_visit(
        at(10, 8, EventKind.BAD_ORDERED),
        at(11, 8, EventKind.REPAIRED),
        at(15, 8, EventKind.BAD_ORDERED),
        at(16, 8, EventKind.REPAIRED),
    )
    assert len(repair_holds(stream, JANUARY.end)) == 2


def test_opening_the_same_state_twice_is_refused():
    stream = a_visit(at(10, 8, EventKind.BAD_ORDERED), at(11, 8, EventKind.BAD_ORDERED))
    with pytest.raises(EventError):
        repair_holds(stream, JANUARY.end)


def test_closing_a_state_that_was_never_opened_is_refused():
    stream = a_visit(at(10, 8, EventKind.REPAIRED))
    with pytest.raises(EventError):
        repair_holds(stream, JANUARY.end)


def test_a_pair_of_kinds_can_be_asked_for_directly():
    stream = a_visit(at(10, 8, EventKind.HELD), at(12, 8, EventKind.FREED))
    found = paired_spans(stream, EventKind.HELD, EventKind.FREED, JANUARY.end, "the hold")
    assert found[0].minutes == 2 * 24 * 60


def test_the_label_a_hold_is_scanned_under_reaches_the_message():
    stream = a_visit(at(10, 8, EventKind.HELD), at(11, 8, EventKind.HELD))
    with pytest.raises(EventError) as caught:
        embargo_holds(stream, JANUARY.end)
    assert "the hold" in caught.value.message


def test_a_bad_order_scans_under_its_own_label():
    stream = a_visit(at(10, 8, EventKind.BAD_ORDERED), at(11, 8, EventKind.BAD_ORDERED))
    with pytest.raises(EventError) as caught:
        repair_holds(stream, JANUARY.end)
    assert "the bad order" in caught.value.message


def test_a_repair_hold_is_reclaimed_at_the_hourly_rate():
    stream = a_visit(at(10, 8, EventKind.BAD_ORDERED), at(14, 8, EventKind.REPAIRED))
    found = reclaims_for(a_car(), stream, a_table(), JANUARY)
    assert found[0].amount == Money(463 * 96)
    assert found[0].reason is ReclaimReason.REPAIRS


def test_a_reclaim_prices_the_same_way_the_charge_does():
    stream = a_visit(at(10, 8, EventKind.BAD_ORDERED), at(14, 8, EventKind.REPAIRED))
    span = Interval(utc(2025, 1, 10, 8), utc(2025, 1, 14, 8))
    found = reclaims_for(a_car(), stream, a_table(), JANUARY)
    assert found[0].amount == hourly(a_car(), a_table(), span)


def test_repairs_we_answer_for_are_not_reclaimed():
    stream = a_visit(at(10, 8, EventKind.BAD_ORDERED), at(14, 8, EventKind.REPAIRED))
    assert reclaims_for(a_car(), stream, a_table(), JANUARY, owner_repairs=False) == []


def test_an_embargo_is_reclaimed_whoever_caused_the_repair():
    stream = a_visit(at(10, 8, EventKind.HELD), at(12, 8, EventKind.FREED))
    found = reclaims_for(a_car(), stream, a_table(), JANUARY, owner_repairs=False)
    assert [one.reason for one in found] == [ReclaimReason.EMBARGO]


def test_a_hold_outside_the_window_reclaims_nothing():
    stream = stream_of(
        at(5, 12, EventKind.RECEIVED, road=CN),
        Event(EventKind.BAD_ORDERED, CAR, utc(2025, 2, 10, 8), "ESC"),
        Event(EventKind.REPAIRED, CAR, utc(2025, 2, 14, 8), "ESC"),
    )
    assert reclaims_for(a_car(), stream, a_table(), JANUARY) == []


def test_a_hold_straddling_the_window_is_cut_to_it():
    stream = stream_of(
        at(5, 12, EventKind.RECEIVED, road=CN),
        Event(EventKind.BAD_ORDERED, CAR, utc(2025, 1, 30, 6), "ESC"),
        Event(EventKind.REPAIRED, CAR, utc(2025, 2, 3, 6), "ESC"),
    )
    found = reclaims_for(a_car(), stream, a_table(), JANUARY)
    assert found[0].interval.end == JANUARY.end


def test_a_reclaim_reports_its_hours():
    stream = a_visit(at(10, 8, EventKind.BAD_ORDERED), at(14, 8, EventKind.REPAIRED))
    assert reclaims_for(a_car(), stream, a_table(), JANUARY)[0].hundredth_hours == 9600


def test_a_reclaim_prints_the_car_the_rule_and_the_money():
    reclaim = Reclaim(CAR, ReclaimReason.REPAIRS, JANUARY, Money(1234))
    assert str(reclaim) == "CN 400100 rule 5 $12.34"


def test_a_charge_comes_down_by_its_reclaims():
    reclaim = Reclaim(CAR, ReclaimReason.REPAIRS, JANUARY, Money(400))
    assert net_of_reclaims(Money(1000), [reclaim]) == Money(600)


def test_reclaims_bigger_than_the_charge_leave_nothing_owed():
    reclaim = Reclaim(CAR, ReclaimReason.REPAIRS, JANUARY, Money(4000))
    assert net_of_reclaims(Money(1000), [reclaim]) == Money(0)


def test_a_charge_with_no_reclaims_stands():
    assert net_of_reclaims(Money(1000), []) == Money(1000)
