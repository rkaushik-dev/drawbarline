from datetime import date

import pytest
from sqlalchemy.exc import IntegrityError

from drawbarline.embargo import Embargo, EmbargoError
from drawbarline.lading import KRAFT_PAPER, WHEAT
from drawbarline.marks import ReportingMark
from drawbarline.persistence.base import create_all, make_engine, table_names
from drawbarline.persistence.models import EmbargoRow
from drawbarline.persistence.uow import UnitOfWork

CN = ReportingMark("CN")


def an_embargo(number=101, **changed):
    fields = {
        "number": number,
        "effective": date(2025, 1, 6),
        "reason": "bridge out at hermansville",
        "station": "HRM",
    }
    fields.update(changed)
    return Embargo(**fields)


@pytest.fixture
def engine():
    engine = make_engine()
    create_all(engine)
    return engine


@pytest.fixture
def issued(engine):
    with UnitOfWork(engine) as work:
        work.embargoes.issue(an_embargo())
        work.embargoes.issue(an_embargo(102, station=None, commodity_family="01"))
        work.commit()
    return engine


def test_the_embargo_table_is_on_the_metadata():
    assert "embargo" in table_names()


def test_a_row_repr_names_the_number_and_date():
    row = EmbargoRow(number=101, effective=date(2025, 1, 6), reason="x", station="HRM")
    assert repr(row) == "<EmbargoRow 101 from 2025-01-06>"


def test_an_embargo_goes_in_and_comes_back_whole(issued):
    with UnitOfWork(issued) as work:
        assert work.embargoes.get(101) == an_embargo()


def test_a_commodity_embargo_keeps_its_family(issued):
    with UnitOfWork(issued) as work:
        assert work.embargoes.get(102).commodity_family == "01"


def test_a_road_embargo_keeps_its_road(engine):
    with UnitOfWork(engine) as work:
        work.embargoes.issue(an_embargo(103, station=None, road=CN))
        work.commit()
    with UnitOfWork(engine) as work:
        assert work.embargoes.get(103).road == CN


def test_the_same_number_twice_is_refused(issued):
    with UnitOfWork(issued) as work, pytest.raises(EmbargoError):
        work.embargoes.issue(an_embargo())


def test_an_embargo_nobody_issued_is_refused(issued):
    with UnitOfWork(issued) as work, pytest.raises(EmbargoError):
        work.embargoes.get(999)


def test_an_embargo_that_restricts_nothing_is_refused_by_the_table(engine):
    with pytest.raises(IntegrityError), UnitOfWork(engine) as work:
        work.session.add(EmbargoRow(number=104, effective=date(2025, 1, 6), reason="everything"))
        work.commit()


def test_an_embargo_that_expires_first_is_refused_by_the_table(engine):
    with pytest.raises(IntegrityError), UnitOfWork(engine) as work:
        work.session.add(
            EmbargoRow(
                number=104,
                effective=date(2025, 6, 6),
                reason="x",
                station="HRM",
                expires=date(2025, 1, 6),
            )
        )
        work.commit()


def test_lifting_an_embargo_ends_it(issued):
    with UnitOfWork(issued) as work:
        work.embargoes.lift(101, date(2025, 1, 20))
        work.commit()
    with UnitOfWork(issued) as work:
        found = work.embargoes.get(101)
    assert found.expires == date(2025, 1, 20)
    assert found.in_force_on(date(2025, 1, 21)) is False


def test_lifting_before_it_took_effect_is_refused(issued):
    with UnitOfWork(issued) as work, pytest.raises(EmbargoError):
        work.embargoes.lift(101, date(2025, 1, 1))


def test_lifting_one_that_already_has_an_end_is_refused(issued):
    with UnitOfWork(issued) as work:
        work.embargoes.lift(101, date(2025, 1, 20))
        with pytest.raises(EmbargoError):
            work.embargoes.lift(101, date(2025, 1, 25))


def test_lifting_something_never_issued_is_refused(issued):
    with UnitOfWork(issued) as work, pytest.raises(EmbargoError):
        work.embargoes.lift(999, date(2025, 1, 20))


def test_the_live_embargoes_come_back(issued):
    with UnitOfWork(issued) as work:
        assert [one.number for one in work.embargoes.in_force_on(date(2025, 1, 10))] == [
            101,
            102,
        ]


def test_an_expired_embargo_is_not_live(issued):
    with UnitOfWork(issued) as work:
        work.embargoes.lift(101, date(2025, 1, 8))
        work.commit()
    with UnitOfWork(issued) as work:
        found = work.embargoes.in_force_on(date(2025, 1, 10))
    assert [one.number for one in found] == [102]


def test_the_whole_register_loads_back(issued):
    with UnitOfWork(issued) as work:
        register = work.embargoes.register()
    caught = register.against(date(2025, 1, 10), station="HRM", commodity=WHEAT)
    assert [one.number for one in caught] == [101, 102]
    assert register.against(date(2025, 1, 10), station="ESC", commodity=KRAFT_PAPER) == []


def test_the_repository_counts_what_it_issued(issued):
    with UnitOfWork(issued) as work:
        assert len(work.embargoes) == 2
