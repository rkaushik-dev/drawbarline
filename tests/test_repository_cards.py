from datetime import date

import pytest
from sqlalchemy.exc import IntegrityError

from drawbarline.errors import BillingError, UnknownCar
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.money import Money
from drawbarline.persistence.base import create_all, make_engine, table_names
from drawbarline.persistence.models import ObjectionRow, RepairCardRow
from drawbarline.persistence.uow import UnitOfWork
from drawbarline.repairs.card import Defect, RepairCard
from drawbarline.repairs.codes import JobCode, Responsibility, WhyMade
from drawbarline.repairs.objection import Ground, Objection, Outcome, Resolution
from support import a_car

WHEELSET = JobCode("4312")
COUPLER = JobCode("5104")
CN = ReportingMark("CN")
ELS = ReportingMark("ELS")


def a_card(number=88104, **changed):
    fields = {
        "number": number,
        "car": CarMark.parse("DBL 104207"),
        "performed_by": CN,
        "on": date(2025, 1, 14),
        "station": "ESC",
        "defects": (
            Defect(WHEELSET, WhyMade("01"), part_years=5),
            Defect(COUPLER, WhyMade("03")),
        ),
    }
    fields.update(changed)
    return RepairCard(**fields)


def an_objection(**changed):
    fields = {
        "card_number": 88104,
        "line": 0,
        "ground": Ground.DEPRECIATION,
        "disputed": Money(20000),
        "filed_on": date(2025, 2, 3),
    }
    fields.update(changed)
    return Objection(**fields)


@pytest.fixture
def engine():
    engine = make_engine()
    create_all(engine)
    with UnitOfWork(engine) as work:
        work.fleet.add(a_car("DBL 104207"))
        work.fleet.add(a_car("DBL 104208"))
        work.commit()
    return engine


@pytest.fixture
def filed(engine):
    with UnitOfWork(engine) as work:
        work.cards.file(a_card())
        work.commit()
    return engine


def test_the_card_tables_are_on_the_metadata():
    for name in ("repair_card", "defect", "objection", "resolution"):
        assert name in table_names()


def test_a_card_row_repr_names_the_number_and_car():
    row = RepairCardRow(
        number=88104,
        car_mark=CarMark.parse("DBL 104207"),
        performed_by=CN,
        performed_on=date(2025, 1, 14),
        station="ESC",
    )
    assert repr(row) == "<RepairCardRow 88104 DBL 104207>"


def test_a_card_goes_in_and_comes_back_whole(filed):
    with UnitOfWork(filed) as work:
        assert work.cards.get(88104) == a_card()


def test_the_lines_keep_their_order(filed):
    with UnitOfWork(filed) as work:
        assert work.cards.get(88104).jobs == [WHEELSET, COUPLER]


def test_a_part_age_survives_the_trip(filed):
    with UnitOfWork(filed) as work:
        assert work.cards.get(88104).defects[0].part_years == 5


def test_an_overridden_responsibility_survives_the_trip(engine):
    card = a_card(
        defects=(
            Defect(
                WHEELSET,
                WhyMade("01"),
                overridden=Responsibility.HANDLING_LINE,
                note="flat spot from a hand brake",
            ),
        )
    )
    with UnitOfWork(engine) as work:
        work.cards.file(card)
        work.commit()
    with UnitOfWork(engine) as work:
        found = work.cards.get(88104).defects[0]
    assert found.answered_by is Responsibility.HANDLING_LINE
    assert found.note == "flat spot from a hand brake"


def test_the_same_card_twice_is_refused(filed):
    with UnitOfWork(filed) as work, pytest.raises(BillingError):
        work.cards.file(a_card())


def test_a_card_against_a_car_nobody_filed_is_refused(engine):
    with UnitOfWork(engine) as work, pytest.raises(UnknownCar):
        work.cards.file(a_card(car=CarMark.parse("WC 000001")))


def test_a_card_nobody_filed_is_refused(filed):
    with UnitOfWork(filed) as work, pytest.raises(BillingError):
        work.cards.get(99999)


def test_cards_come_back_for_one_car(engine):
    with UnitOfWork(engine) as work:
        work.cards.file(a_card())
        work.cards.file(a_card(88105, car=CarMark.parse("DBL 104208")))
        work.commit()
    with UnitOfWork(engine) as work:
        found = work.cards.for_car(CarMark.parse("DBL 104207"))
    assert [one.number for one in found] == [88104]


def test_cards_come_back_by_road(engine):
    with UnitOfWork(engine) as work:
        work.cards.file(a_card())
        work.cards.file(a_card(88105, performed_by=ELS))
        work.commit()
    with UnitOfWork(engine) as work:
        assert [one.number for one in work.cards.by_road(ELS)] == [88105]


def test_cards_come_back_for_a_span_of_days(engine):
    with UnitOfWork(engine) as work:
        work.cards.file(a_card())
        work.cards.file(a_card(88105, on=date(2025, 3, 4)))
        work.commit()
    with UnitOfWork(engine) as work:
        found = work.cards.between(date(2025, 1, 1), date(2025, 1, 31))
    assert [one.number for one in found] == [88104]


def test_an_exception_is_filed_and_stays_open(filed):
    with UnitOfWork(filed) as work:
        work.cards.take_exception(an_objection())
        work.commit()
    with UnitOfWork(filed) as work:
        assert [one.line for one in work.cards.open_objections()] == [0]


def test_the_same_line_twice_is_refused(filed):
    with UnitOfWork(filed) as work:
        work.cards.take_exception(an_objection())
        with pytest.raises(BillingError):
            work.cards.take_exception(an_objection(ground=Ground.PRICE))


def test_an_exception_against_a_line_the_card_has_not_got_is_refused(filed):
    with UnitOfWork(filed) as work, pytest.raises(BillingError):
        work.cards.take_exception(an_objection(line=7))


def test_an_exception_against_a_card_nobody_filed_is_refused(filed):
    with UnitOfWork(filed) as work, pytest.raises(BillingError):
        work.cards.take_exception(an_objection(card_number=99999))


def test_answering_an_exception_closes_it(filed):
    with UnitOfWork(filed) as work:
        objection = work.cards.take_exception(an_objection())
        work.cards.answer(objection, Resolution(objection, Outcome.ALLOWED, Money(20000)))
        work.commit()
    with UnitOfWork(filed) as work:
        assert work.cards.open_objections() == []
        assert work.cards.outcome_of(88104, 0) is Outcome.ALLOWED


def test_answering_twice_is_refused(filed):
    with UnitOfWork(filed) as work:
        objection = work.cards.take_exception(an_objection())
        work.cards.answer(objection, Resolution(objection, Outcome.ALLOWED, Money(20000)))
        with pytest.raises(BillingError):
            work.cards.answer(objection, Resolution(objection, Outcome.DECLINED, Money(0)))


def test_answering_something_never_filed_is_refused(filed):
    objection = an_objection()
    with UnitOfWork(filed) as work, pytest.raises(BillingError):
        work.cards.answer(objection, Resolution(objection, Outcome.ALLOWED, Money(1)))


def test_a_line_with_no_exception_has_no_outcome(filed):
    with UnitOfWork(filed) as work:
        assert work.cards.outcome_of(88104, 1) is None


def test_a_disputed_amount_of_nothing_is_refused_by_the_table(filed):
    with pytest.raises(IntegrityError), UnitOfWork(filed) as work:
        work.session.add(
            ObjectionRow(
                card_number=88104,
                line=1,
                ground="PRICE",
                disputed=Money(0),
                filed_on=date(2025, 2, 3),
            )
        )
        work.commit()


def test_the_repository_counts_its_cards(engine):
    with UnitOfWork(engine) as work:
        work.cards.file(a_card())
        work.cards.file(a_card(88105))
        work.commit()
    with UnitOfWork(engine) as work:
        assert len(work.cards) == 2
