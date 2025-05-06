from datetime import date

import pytest

from drawbarline.carhire.rates import Band, Rate
from drawbarline.clock import utc
from drawbarline.customers import Industry
from drawbarline.demurrage.tariff import Step, Tariff
from drawbarline.errors import SettlementError
from drawbarline.events.kinds import EventKind
from drawbarline.events.record import Event
from drawbarline.marks import CarMark
from drawbarline.money import Money
from drawbarline.persistence.base import create_all, make_engine
from drawbarline.persistence.numbering import INVOICES, STATEMENTS
from drawbarline.persistence.uow import UnitOfWork
from drawbarline.service import books_of, run_close
from drawbarline.settlement.ledger import PostingKind
from drawbarline.settlement.period import Period
from drawbarline.settlement.statement import cut
from support import (
    CN,
    ELEVATOR,
    HOPPER,
    MILL,
    a_car,
    delivered,
    moved,
    placed,
    received,
    released,
)

JANUARY = Period(2025, 1)
ISSUED = date(2025, 2, 10)


@pytest.fixture
def engine():
    engine = make_engine()
    create_all(engine)
    with UnitOfWork(engine) as work:
        work.numbering.start(INVOICES, 5001)
        work.numbering.start(STATEMENTS, 9001)
        work.customers.add_shipper(MILL)
        work.customers.add_shipper(ELEVATOR)
        work.customers.add_industry(Industry("ESCPAP1", "mill house", "ESC", MILL, 11))
        work.tariffs.publish(
            Tariff("4200", date(2024, 7, 1), 2, 1, (Step(2, 10000), Step(0, 20000)))
        )
        work.rates.file(Band(HOPPER, 0, 9, date(2024, 7, 1), Rate(4630, 1210)))
        work.rates.file(Band(HOPPER, 10, 99, date(2024, 7, 1), Rate(3180, 1210)))
        for mark in ("CN 400100", "DBL 104207"):
            work.fleet.add(a_car(mark))
        work.commit()
    return engine


@pytest.fixture
def worked(engine):
    with UnitOfWork(engine) as work:
        log = work.events.log()
        received(log, "CN 400100", 6)
        moved(log, "CN 400100", 6, 336)
        placed(log, "CN 400100", 7)
        released(log, "CN 400100", 14)
        delivered(log, "CN 400100", 16)
        for car in log.cars():
            for event in log.stream(car):
                work.events.record(event)
        work.commit()
    return engine


def test_the_books_load_every_register(worked):
    with UnitOfWork(worked) as work:
        books = books_of(work)
    assert len(books.fleet) == 2
    assert books.log.cars() == [CarMark.parse("CN 400100")]
    assert len(books.customers) == 1


def test_the_books_know_which_road_is_home(worked):
    with UnitOfWork(worked) as work:
        assert str(books_of(work).home) == "DBL"


def test_a_close_writes_its_postings(worked):
    with UnitOfWork(worked) as work:
        result = run_close(work, JANUARY, issued=ISSUED)
        work.commit()
    assert result.posted == 1
    with UnitOfWork(worked) as work:
        assert len(work.ledger.postings(CN)) == 1


def test_a_close_shuts_the_month_in_the_database(worked):
    with UnitOfWork(worked) as work:
        run_close(work, JANUARY, issued=ISSUED)
        work.commit()
    with UnitOfWork(worked) as work:
        assert work.periods.is_open(JANUARY) is False


def test_closing_the_same_month_twice_is_refused(worked):
    with UnitOfWork(worked) as work:
        run_close(work, JANUARY, issued=ISSUED)
        work.commit()
    with UnitOfWork(worked) as work, pytest.raises(SettlementError):
        run_close(work, JANUARY, issued=ISSUED)


def test_a_close_that_is_not_committed_leaves_nothing_behind(worked):
    with UnitOfWork(worked) as work:
        run_close(work, JANUARY, issued=ISSUED)
    with UnitOfWork(worked) as work:
        assert work.periods.is_open(JANUARY) is True
        assert work.ledger.postings() == []


def test_the_documents_are_numbered_from_the_counters(worked):
    with UnitOfWork(worked) as work:
        result = run_close(work, JANUARY, issued=ISSUED)
        work.commit()
    assert result.invoices[0].number == 5001
    assert result.statements[0].number == 9001


def test_the_next_close_numbers_on_from_there(worked):
    with UnitOfWork(worked) as work:
        run_close(work, JANUARY, issued=ISSUED)
        work.commit()
    with UnitOfWork(worked) as work:
        assert work.numbering.peek(STATEMENTS) == 9002


def test_the_car_hire_reaches_the_ledger(worked):
    with UnitOfWork(worked) as work:
        result = run_close(work, JANUARY, issued=ISSUED)
        work.commit()
    with UnitOfWork(worked) as work:
        ledger = work.ledger.ledger()
    assert ledger.account(CN).by_kind(JANUARY)[PostingKind.CAR_HIRE] == result.car_hire


def test_a_statement_can_be_cut_from_the_stored_ledger(worked):
    with UnitOfWork(worked) as work:
        run_close(work, JANUARY, issued=ISSUED)
        work.commit()
    with UnitOfWork(worked) as work:
        statement = cut(work.ledger.ledger(), CN, JANUARY, number=9001, issued=ISSUED)
    assert statement.closing.cents > 0


def test_a_month_with_no_traffic_closes_quietly(engine):
    with UnitOfWork(engine) as work:
        result = run_close(work, JANUARY, issued=ISSUED)
        work.commit()
    assert result.posted == 0
    assert result.car_hire == Money(0)


def test_the_demurrage_invoice_reaches_the_result(worked):
    with UnitOfWork(worked) as work:
        result = run_close(work, JANUARY, issued=ISSUED)
        work.commit()
    assert [one.shipper for one in result.invoices] == ["ESCPAP"]


def test_an_event_recorded_after_the_close_does_not_move_the_postings(worked):
    with UnitOfWork(worked) as work:
        first = run_close(work, JANUARY, issued=ISSUED)
        work.commit()
    with UnitOfWork(worked) as work:
        work.events.record(
            Event(
                EventKind.RECEIVED,
                CarMark.parse("DBL 104207"),
                utc(2025, 1, 20, 12),
                "ESC",
                road=CN,
            )
        )
        work.commit()
    with UnitOfWork(worked) as work:
        posted = work.ledger.postings(CN)
    assert len(posted) == len(first.postings)
