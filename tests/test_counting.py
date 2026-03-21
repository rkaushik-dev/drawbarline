import pytest
from sqlalchemy import event

from drawbarline.clock import utc
from drawbarline.events.kinds import EventKind
from drawbarline.events.record import Event
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.persistence.base import create_all, make_engine
from drawbarline.persistence.models.events import EventRow
from drawbarline.persistence.repositories.counting import count_of, count_where
from drawbarline.persistence.uow import UnitOfWork
from support import a_car

CN = ReportingMark("CN")
CARS = 6


@pytest.fixture
def stocked():
    engine = make_engine()
    create_all(engine)
    with UnitOfWork(engine) as work:
        for at in range(CARS):
            mark = f"CN {400100 + at}"
            work.fleet.add(a_car(mark))
            car = CarMark.parse(mark)
            work.events.record(Event(EventKind.RECEIVED, car, utc(2025, 1, 6, 12), "ESC", road=CN))
        work.commit()
    return engine


def statements(engine):
    seen: list[str] = []

    def watch(conn, cursor, statement, parameters, context, executemany):
        seen.append(statement)

    event.listen(engine, "before_cursor_execute", watch)
    return seen, lambda: event.remove(engine, "before_cursor_execute", watch)


def test_a_count_comes_back_as_a_number(stocked):
    with UnitOfWork(stocked) as work:
        assert count_of(work.session, EventRow.id) == CARS


def test_an_empty_table_counts_nothing():
    engine = make_engine()
    create_all(engine)
    with UnitOfWork(engine) as work:
        assert count_of(work.session, EventRow.id) == 0


def test_a_count_does_not_read_the_rows(stocked):
    seen, stop = statements(stocked)
    try:
        with UnitOfWork(stocked) as work:
            count_of(work.session, EventRow.id)
    finally:
        stop()
    assert len(seen) == 1
    assert "count(" in seen[0].lower()


def test_a_filtered_count_matches(stocked):
    with UnitOfWork(stocked) as work:
        found = count_where(work.session, EventRow.id, EventRow.kind == EventKind.RECEIVED.name)
    assert found == CARS


def test_a_filter_that_matches_nothing_counts_nothing(stocked):
    with UnitOfWork(stocked) as work:
        found = count_where(work.session, EventRow.id, EventRow.kind == EventKind.PULLED.name)
    assert found == 0


def test_two_filters_are_taken_together(stocked):
    with UnitOfWork(stocked) as work:
        found = count_where(
            work.session,
            EventRow.id,
            EventRow.kind == EventKind.RECEIVED.name,
            EventRow.station == "ESC",
        )
    assert found == CARS


def test_the_repositories_agree_with_the_count(stocked):
    with UnitOfWork(stocked) as work:
        assert len(work.events) == CARS
        assert len(work.fleet) == CARS


def test_counting_the_fleet_does_not_read_the_cars(stocked):
    seen, stop = statements(stocked)
    try:
        with UnitOfWork(stocked) as work:
            len(work.fleet)
    finally:
        stop()
    assert len(seen) == 1
