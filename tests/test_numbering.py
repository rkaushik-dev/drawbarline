import pytest
from sqlalchemy.exc import IntegrityError

from drawbarline.errors import ConfigurationError
from drawbarline.persistence.base import create_all, make_engine, table_names
from drawbarline.persistence.models import CounterRow
from drawbarline.persistence.numbering import INVOICES, STATEMENTS
from drawbarline.persistence.uow import UnitOfWork


@pytest.fixture
def engine():
    engine = make_engine()
    create_all(engine)
    return engine


@pytest.fixture
def opened(engine):
    with UnitOfWork(engine) as work:
        work.numbering.start(INVOICES, 5001)
        work.numbering.start(STATEMENTS, 9001)
        work.commit()
    return engine


def test_the_counter_table_is_on_the_metadata():
    assert "counter" in table_names()


def test_a_counter_row_repr_names_the_series():
    assert repr(CounterRow(name=INVOICES, next_number=5001)) == (
        "<CounterRow demurrage_invoice next 5001>"
    )


def test_a_series_starts_where_it_is_told(opened):
    with UnitOfWork(opened) as work:
        assert work.numbering.peek(INVOICES) == 5001


def test_a_series_nobody_publishes_is_refused(engine):
    with UnitOfWork(engine) as work, pytest.raises(ConfigurationError):
        work.numbering.start("something else", 1)


def test_a_series_starting_below_one_is_refused(engine):
    with UnitOfWork(engine) as work, pytest.raises(ConfigurationError):
        work.numbering.start(INVOICES, 0)


def test_opening_a_series_twice_is_refused(opened):
    with UnitOfWork(opened) as work, pytest.raises(ConfigurationError):
        work.numbering.start(INVOICES, 7000)


def test_a_series_never_opened_cannot_be_read(engine):
    with UnitOfWork(engine) as work, pytest.raises(ConfigurationError):
        work.numbering.peek(INVOICES)


def test_a_series_never_opened_cannot_be_taken_from(engine):
    with UnitOfWork(engine) as work, pytest.raises(ConfigurationError):
        work.numbering.take(INVOICES)


def test_numbers_come_out_one_after_another(opened):
    with UnitOfWork(opened) as work:
        assert work.numbering.take(INVOICES) == 5001
        assert work.numbering.take(INVOICES) == 5002
        work.commit()
    with UnitOfWork(opened) as work:
        assert work.numbering.take(INVOICES) == 5003


def test_two_series_do_not_interfere(opened):
    with UnitOfWork(opened) as work:
        assert work.numbering.take(INVOICES) == 5001
        assert work.numbering.take(STATEMENTS) == 9001
        assert work.numbering.take(INVOICES) == 5002
        work.commit()


def test_a_run_of_numbers_comes_out_together(opened):
    with UnitOfWork(opened) as work:
        assert work.numbering.take_many(STATEMENTS, 3) == [9001, 9002, 9003]
        work.commit()
    with UnitOfWork(opened) as work:
        assert work.numbering.peek(STATEMENTS) == 9004


def test_asking_for_no_numbers_is_refused(opened):
    with UnitOfWork(opened) as work, pytest.raises(ConfigurationError):
        work.numbering.take_many(INVOICES, 0)


def test_a_number_taken_and_rolled_back_is_given_up(opened):
    with UnitOfWork(opened) as work:
        assert work.numbering.take(INVOICES) == 5001
    with UnitOfWork(opened) as work:
        assert work.numbering.take(INVOICES) == 5001


def test_a_failed_block_leaves_no_gap(opened):
    with pytest.raises(RuntimeError), UnitOfWork(opened) as work:
        work.numbering.take(INVOICES)
        raise RuntimeError("the close blew up half way")
    with UnitOfWork(opened) as work:
        assert work.numbering.peek(INVOICES) == 5001


def test_the_open_series_come_back_in_order(opened):
    with UnitOfWork(opened) as work:
        assert work.numbering.open_series() == [INVOICES, STATEMENTS]


def test_a_counter_below_one_is_refused_by_the_table(engine):
    with pytest.raises(IntegrityError), UnitOfWork(engine) as work:
        work.session.add(CounterRow(name=INVOICES, next_number=0))
        work.commit()


def test_peeking_does_not_take_a_number(opened):
    with UnitOfWork(opened) as work:
        work.numbering.peek(INVOICES)
        work.numbering.peek(INVOICES)
        assert work.numbering.take(INVOICES) == 5001
