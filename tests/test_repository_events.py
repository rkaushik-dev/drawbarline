from datetime import date

import pytest
from sqlalchemy.exc import IntegrityError

from drawbarline.clock import Interval, utc
from drawbarline.errors import EventError
from drawbarline.events.kinds import EventKind
from drawbarline.events.record import Event
from drawbarline.events.replay import replay
from drawbarline.fleet.aar import CarTypeCode, MechanicalDesignation
from drawbarline.fleet.car import Car
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.measure import Length
from drawbarline.persistence.base import create_all, make_engine, table_names
from drawbarline.persistence.models import EventRow
from drawbarline.persistence.uow import UnitOfWork
from drawbarline.weight import Weight

CAR = CarMark.parse("CN 400100")
OTHER = CarMark.parse("DBL 104207")
CN = ReportingMark("CN")


def a_car(mark):
    return Car(
        mark=CarMark.parse(mark),
        designation=MechanicalDesignation("LO"),
        type_code=CarTypeCode("L351"),
        built=date(2020, 6, 1),
        light_weight=Weight(63500),
        gross_rail_load=Weight(286000),
        outside_length=Length.of(59, 6),
    )


def received(car=CAR, day=6):
    return Event(EventKind.RECEIVED, car, utc(2025, 1, day, 12), "ESC", road=CN, waybill=41207)


def delivered(car=CAR, day=9):
    return Event(EventKind.DELIVERED, car, utc(2025, 1, day, 12), "MRN", road=CN)


def moved(car=CAR, day=7, tenths=336):
    return Event(EventKind.MOVED, car, utc(2025, 1, day, 18), "PWR", tenths=tenths)


@pytest.fixture
def engine():
    engine = make_engine()
    create_all(engine)
    with UnitOfWork(engine) as work:
        work.fleet.add(a_car("CN 400100"))
        work.fleet.add(a_car("DBL 104207"))
        work.commit()
    return engine


def test_the_event_table_is_on_the_metadata():
    assert "car_event" in table_names()


def test_an_event_goes_in_and_comes_back_whole(engine):
    with UnitOfWork(engine) as work:
        work.events.record(received())
        work.commit()
    with UnitOfWork(engine) as work:
        found = list(work.events.stream(CAR))
    assert found == [received()]


def test_a_row_repr_names_the_car_and_the_day(engine):
    row = EventRow(car_mark=CAR, kind="RECEIVED", happened=utc(2025, 1, 6, 12), station="ESC")
    assert repr(row) == "<EventRow CN 400100 RECEIVED 2025-01-06>"


def test_the_same_event_twice_is_refused_by_the_repository(engine):
    with UnitOfWork(engine) as work:
        work.events.record(received())
        with pytest.raises(EventError):
            work.events.record(received())


def test_the_table_refuses_a_duplicate_the_repository_did_not_see(engine):
    from drawbarline.persistence.mappers.events import row_from_event

    with pytest.raises(IntegrityError), UnitOfWork(engine) as work:
        work.session.add(row_from_event(received()))
        work.session.add(row_from_event(received()))
        work.commit()


def test_an_event_against_a_car_nobody_filed_is_refused(engine):
    with pytest.raises(IntegrityError), UnitOfWork(engine) as work:
        work.events.record(received(car=CarMark.parse("WC 000001")))
        work.commit()


def test_a_stream_comes_back_in_the_order_it_happened(engine):
    with UnitOfWork(engine) as work:
        work.events.record(delivered())
        work.events.record(received())
        work.events.record(moved())
        work.commit()
    with UnitOfWork(engine) as work:
        kinds = [event.kind for event in work.events.stream(CAR)]
    assert kinds == [EventKind.RECEIVED, EventKind.MOVED, EventKind.DELIVERED]


def test_a_stored_stream_replays_the_same_way(engine):
    with UnitOfWork(engine) as work:
        work.events.record_all([received(), moved(), delivered()])
        work.commit()
    with UnitOfWork(engine) as work:
        state = replay(work.events.stream(CAR))
    assert state.on_line is False
    assert state.tenths_run == 336


def test_a_car_with_nothing_recorded_reads_as_an_empty_stream(engine):
    with UnitOfWork(engine) as work:
        assert len(work.events.stream(CAR)) == 0


def test_events_come_back_for_a_window(engine):
    with UnitOfWork(engine) as work:
        work.events.record(received())
        work.events.record(Event(EventKind.DELIVERED, CAR, utc(2025, 2, 9, 12), "MRN", road=CN))
        work.commit()
    january = Interval(utc(2025, 1, 1), utc(2025, 2, 1))
    with UnitOfWork(engine) as work:
        assert [one.kind for one in work.events.during(january)] == [EventKind.RECEIVED]


def test_events_come_back_by_kind(engine):
    with UnitOfWork(engine) as work:
        work.events.record(received())
        work.events.record(received(car=OTHER))
        work.commit()
    with UnitOfWork(engine) as work:
        assert len(work.events.of_kind(EventKind.RECEIVED)) == 2
        assert work.events.of_kind(EventKind.PULLED) == []


def test_the_cars_with_events_come_back_in_order(engine):
    with UnitOfWork(engine) as work:
        work.events.record(received(car=OTHER))
        work.events.record(received())
        work.commit()
    with UnitOfWork(engine) as work:
        assert work.events.cars() == [CAR, OTHER]


def test_the_last_moment_is_the_latest_event(engine):
    with UnitOfWork(engine) as work:
        work.events.record_all([received(), delivered()])
        work.commit()
    with UnitOfWork(engine) as work:
        assert work.events.last_moment() == utc(2025, 1, 9, 12)


def test_an_empty_table_has_no_last_moment(engine):
    with UnitOfWork(engine) as work:
        assert work.events.last_moment() is None


def test_the_repository_counts_its_rows(engine):
    with UnitOfWork(engine) as work:
        work.events.record_all([received(), moved(), delivered()])
        work.commit()
    with UnitOfWork(engine) as work:
        assert len(work.events) == 3


def test_the_repository_builds_the_in_memory_log(engine):
    with UnitOfWork(engine) as work:
        work.events.record_all([received(), moved(), delivered()])
        work.events.record(received(car=OTHER))
        work.commit()
    with UnitOfWork(engine) as work:
        log = work.events.log()
    assert log.cars() == [CAR, OTHER]
    assert len(log) == 4


def test_a_movement_of_no_distance_is_refused_by_the_table(engine):
    from drawbarline.persistence.models.events import EventRow as Row

    with pytest.raises(IntegrityError), UnitOfWork(engine) as work:
        work.session.add(
            Row(
                car_mark=CAR,
                kind="MOVED",
                happened=utc(2025, 1, 7, 18),
                station="PWR",
                tenths=0,
            )
        )
        work.commit()
