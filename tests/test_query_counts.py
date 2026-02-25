"""How many round trips a read costs.

Nothing in this application is slow enough for anybody to notice, and that is
precisely why a query a car creeps in: it is written once, it works, and it
only shows up when the fleet is four times the size. Counting the statements a
call makes is cheap and it pins the shape of the read rather than its speed,
which is the thing that actually regresses.

The numbers here are deliberately loose. They are there to catch a read that
went from one query to a query a row, not to fail because somebody added a
column.
"""

from __future__ import annotations

from contextlib import contextmanager

import pytest
from sqlalchemy import event

from drawbarline.clock import utc
from drawbarline.events.kinds import EventKind
from drawbarline.events.record import Event
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.persistence.base import create_all, make_engine
from drawbarline.persistence.uow import UnitOfWork
from support import a_car

CN = ReportingMark("CN")
CARS = 12


@contextmanager
def counted(engine):
    """Count the statements executed inside the block."""
    seen: list[str] = []

    def watch(conn, cursor, statement, parameters, context, executemany):
        seen.append(statement)

    event.listen(engine, "before_cursor_execute", watch)
    try:
        yield seen
    finally:
        event.remove(engine, "before_cursor_execute", watch)


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
            work.events.record(Event(EventKind.MOVED, car, utc(2025, 1, 6, 18), "PWR", tenths=336))
        work.commit()
    return engine


def selects(seen):
    return [one for one in seen if one.lstrip().upper().startswith("SELECT")]


def test_the_fixture_has_cars_and_events(stocked):
    with UnitOfWork(stocked) as work:
        assert len(work.fleet) == CARS
        assert len(work.events) == CARS * 2


def test_the_whole_log_loads_in_one_query(stocked):
    with UnitOfWork(stocked) as work, counted(stocked) as seen:
        log = work.events.log()
    assert len(log) == CARS * 2
    assert len(selects(seen)) == 1


def test_loading_the_log_does_not_grow_with_the_fleet(stocked):
    with UnitOfWork(stocked) as work, counted(stocked) as seen:
        work.events.log()
    assert len(selects(seen)) < CARS


def test_one_car_stream_is_one_query(stocked):
    with UnitOfWork(stocked) as work, counted(stocked) as seen:
        work.events.stream(CarMark.parse("CN 400100"))
    assert len(selects(seen)) == 1


def test_the_rate_table_loads_in_one_query(stocked):
    with UnitOfWork(stocked) as work, counted(stocked) as seen:
        work.rates.table()
    assert len(selects(seen)) == 1


def test_the_ledger_loads_without_a_query_a_posting(stocked):
    with UnitOfWork(stocked) as work, counted(stocked) as seen:
        work.ledger.ledger()
    assert len(selects(seen)) <= 3


def test_the_fleet_register_does_not_query_per_lease(stocked):
    with UnitOfWork(stocked) as work, counted(stocked) as seen:
        work.fleet.register()
    # One for the cars, then the lazy loads for the leases and retirements
    # each car actually has. None of them has any, so this stays small.
    assert len(selects(seen)) <= 2 * CARS + 2


def test_a_car_lookup_is_one_query(stocked):
    with UnitOfWork(stocked) as work, counted(stocked) as seen:
        work.fleet.get(CarMark.parse("CN 400100"))
    assert len(selects(seen)) == 1


def test_reading_every_car_is_one_query(stocked):
    with UnitOfWork(stocked) as work, counted(stocked) as seen:
        found = work.fleet.all()
    assert len(found) == CARS
    assert len(selects(seen)) == 1


def test_reading_cars_by_owner_is_one_query(stocked):
    with UnitOfWork(stocked) as work, counted(stocked) as seen:
        work.fleet.by_owner(CN)
    assert len(selects(seen)) == 1


def test_the_counter_watches_what_it_says_it_does(stocked):
    with UnitOfWork(stocked) as work, counted(stocked) as seen:
        work.events.log()
        work.events.log()
    assert len(selects(seen)) == 2
