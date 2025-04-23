from datetime import date

import pytest
from sqlalchemy.exc import IntegrityError

from drawbarline.errors import SettlementError
from drawbarline.marks import ReportingMark
from drawbarline.money import Money
from drawbarline.persistence.base import create_all, make_engine, table_names
from drawbarline.persistence.models import ClosedPeriodRow, PostingRow
from drawbarline.persistence.uow import UnitOfWork
from drawbarline.settlement.ledger import Posting, PostingKind
from drawbarline.settlement.period import Period

CN = ReportingMark("CN")
ELS = ReportingMark("ELS")
JANUARY = Period(2025, 1)
FEBRUARY = Period(2025, 2)


def a_posting(**changed):
    fields = {
        "road": CN,
        "period": JANUARY,
        "kind": PostingKind.CAR_HIRE,
        "amount": Money(120000),
        "on": JANUARY.last_day,
        "reference": "cn january",
    }
    fields.update(changed)
    return Posting(**fields)


@pytest.fixture
def engine():
    engine = make_engine()
    create_all(engine)
    return engine


def test_the_settlement_tables_are_on_the_metadata():
    for name in ("posting", "closed_period", "reopening"):
        assert name in table_names()


def test_a_posting_goes_in_and_comes_back_whole(engine):
    with UnitOfWork(engine) as work:
        work.ledger.post(a_posting())
        work.commit()
    with UnitOfWork(engine) as work:
        assert work.ledger.postings() == [a_posting()]


def test_a_posting_row_repr_names_the_road_and_month():
    row = PostingRow(
        road=CN,
        period_year=2025,
        period_month=1,
        kind="CAR_HIRE",
        amount=Money(1),
        posted_on=date(2025, 1, 31),
    )
    assert repr(row) == "<PostingRow CN 2025-01 CAR_HIRE>"


def test_a_month_starts_open(engine):
    with UnitOfWork(engine) as work:
        assert work.periods.is_open(JANUARY) is True


def test_closing_a_month_shuts_it(engine):
    with UnitOfWork(engine) as work:
        work.periods.close(JANUARY, date(2025, 2, 10))
        work.commit()
    with UnitOfWork(engine) as work:
        assert work.periods.is_open(JANUARY) is False


def test_closing_a_month_twice_is_refused(engine):
    with UnitOfWork(engine) as work:
        work.periods.close(JANUARY, date(2025, 2, 10))
        with pytest.raises(SettlementError):
            work.periods.close(JANUARY, date(2025, 2, 11))


def test_the_table_refuses_a_second_closing_row(engine):
    with pytest.raises(IntegrityError), UnitOfWork(engine) as work:
        for _ in range(2):
            work.session.add(
                ClosedPeriodRow(period_year=2025, period_month=1, closed_on=date(2025, 2, 10))
            )
        work.commit()


def test_a_month_that_is_not_a_month_is_refused_by_the_table(engine):
    with pytest.raises(IntegrityError), UnitOfWork(engine) as work:
        work.session.add(
            ClosedPeriodRow(period_year=2025, period_month=13, closed_on=date(2025, 2, 10))
        )
        work.commit()


def test_posting_into_a_closed_month_is_refused(engine):
    with UnitOfWork(engine) as work:
        work.periods.close(JANUARY, date(2025, 2, 10))
        work.commit()
    with UnitOfWork(engine) as work, pytest.raises(SettlementError):
        work.ledger.post(a_posting())


def test_reopening_lets_a_posting_in_again(engine):
    with UnitOfWork(engine) as work:
        work.periods.close(JANUARY, date(2025, 2, 10))
        work.periods.reopen(JANUARY, date(2025, 3, 1), "corrected sheet from cn")
        work.commit()
    with UnitOfWork(engine) as work:
        assert work.ledger.post(a_posting()).amount == Money(120000)


def test_reopening_without_a_reason_is_refused(engine):
    with UnitOfWork(engine) as work:
        work.periods.close(JANUARY, date(2025, 2, 10))
        with pytest.raises(SettlementError):
            work.periods.reopen(JANUARY, date(2025, 3, 1), "   ")


def test_reopening_a_month_that_is_open_is_refused(engine):
    with UnitOfWork(engine) as work, pytest.raises(SettlementError):
        work.periods.reopen(JANUARY, date(2025, 3, 1), "a reason")


def test_a_reopening_stays_on_file(engine):
    with UnitOfWork(engine) as work:
        work.periods.close(JANUARY, date(2025, 2, 10))
        work.periods.reopen(JANUARY, date(2025, 3, 1), "corrected sheet from cn")
        work.periods.close(JANUARY, date(2025, 3, 5))
        work.commit()
    with UnitOfWork(engine) as work:
        found = work.periods.reopenings()
    assert found == [(JANUARY, date(2025, 3, 1), "corrected sheet from cn")]


def test_the_closed_months_come_back_in_order(engine):
    with UnitOfWork(engine) as work:
        work.periods.close(FEBRUARY, date(2025, 3, 10))
        work.periods.close(JANUARY, date(2025, 2, 10))
        work.commit()
    with UnitOfWork(engine) as work:
        assert work.periods.closed_periods() == [JANUARY, FEBRUARY]


def test_postings_come_back_for_one_road(engine):
    with UnitOfWork(engine) as work:
        work.ledger.post(a_posting())
        work.ledger.post(a_posting(road=ELS, amount=Money(-8000)))
        work.commit()
    with UnitOfWork(engine) as work:
        assert len(work.ledger.postings(CN)) == 1
        assert work.ledger.roads() == [CN, ELS]


def test_the_whole_ledger_loads_back(engine):
    with UnitOfWork(engine) as work:
        work.ledger.post(a_posting())
        work.ledger.post(a_posting(kind=PostingKind.REPAIRS, amount=Money(-45000)))
        work.ledger.post(a_posting(road=ELS, amount=Money(-8000)))
        work.commit()
    with UnitOfWork(engine) as work:
        ledger = work.ledger.ledger()
    assert ledger.net() == {CN: Money(75000), ELS: Money(-8000)}


def test_a_loaded_ledger_holds_the_closed_months(engine):
    with UnitOfWork(engine) as work:
        work.ledger.post(a_posting())
        work.periods.close(JANUARY, date(2025, 2, 10))
        work.commit()
    with UnitOfWork(engine) as work:
        ledger = work.ledger.ledger()
    assert ledger.net()[CN] == Money(120000)
    with pytest.raises(SettlementError):
        ledger.post(a_posting())


def test_a_closed_month_still_loads_its_postings(engine):
    with UnitOfWork(engine) as work:
        work.ledger.post(a_posting())
        work.periods.close(JANUARY, date(2025, 2, 10))
        work.commit()
    with UnitOfWork(engine) as work:
        assert len(work.ledger) == 1


def test_the_period_register_loads_back(engine):
    with UnitOfWork(engine) as work:
        work.periods.close(JANUARY, date(2025, 2, 10))
        work.commit()
    with UnitOfWork(engine) as work:
        register = work.periods.register()
    assert register.is_open(JANUARY) is False
    assert register.is_open(FEBRUARY) is True
