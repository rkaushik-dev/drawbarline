from datetime import date

import pytest
from sqlalchemy.exc import IntegrityError

from drawbarline.customers import Shipper
from drawbarline.demurrage.average import AverageAgreement
from drawbarline.demurrage.tariff import Step, Tariff
from drawbarline.errors import TariffError
from drawbarline.money import Money
from drawbarline.persistence.base import create_all, make_engine, table_names
from drawbarline.persistence.models import TariffRow, TariffStepRow
from drawbarline.persistence.uow import UnitOfWork

ELEVATOR = Shipper("PWRGRN", "Powers Grain", average_agreement=True)
FOUR_TWO = Tariff("4200", date(2024, 7, 1), 2, 1, (Step(2, 10000), Step(0, 20000)))
FOUR_THREE = Tariff("4300", date(2025, 7, 1), 1, 1, (Step(2, 12000), Step(0, 24000)))


@pytest.fixture
def engine():
    engine = make_engine()
    create_all(engine)
    with UnitOfWork(engine) as work:
        work.customers.add_shipper(ELEVATOR)
        work.commit()
    return engine


@pytest.fixture
def published(engine):
    with UnitOfWork(engine) as work:
        work.tariffs.publish(FOUR_TWO)
        work.tariffs.publish(FOUR_THREE)
        work.commit()
    return engine


def test_the_tariff_tables_are_on_the_metadata():
    for name in ("tariff", "tariff_step", "average_agreement"):
        assert name in table_names()


def test_a_tariff_row_repr_names_its_number_and_date():
    row = TariffRow(code="4200", effective=date(2024, 7, 1), free_days_loaded=2, free_days_empty=1)
    assert repr(row) == "<TariffRow 4200 from 2024-07-01>"


def test_a_step_row_repr_names_the_day_it_runs_through():
    row = TariffStepRow(tariff_code="4200", position=0, through=2, cents=1)
    assert repr(row) == "<TariffStepRow 4200 through 2>"


def test_a_tariff_goes_in_and_comes_back_whole(published):
    with UnitOfWork(published) as work:
        assert work.tariffs.tariff("4200") == FOUR_TWO


def test_the_steps_keep_their_order(published):
    with UnitOfWork(published) as work:
        found = work.tariffs.tariff("4200")
    assert found.steps == (Step(2, 10000), Step(0, 20000))


def test_a_stored_tariff_prices_the_same_way(published):
    with UnitOfWork(published) as work:
        assert work.tariffs.tariff("4200").charge(3) == Money(40000)


def test_publishing_the_same_number_twice_is_refused(published):
    with UnitOfWork(published) as work, pytest.raises(TariffError):
        work.tariffs.publish(FOUR_TWO)


def test_two_tariffs_from_the_same_day_are_refused(published):
    other = Tariff("4400", date(2024, 7, 1), 2, 1, (Step(0, 1),))
    with UnitOfWork(published) as work, pytest.raises(TariffError):
        work.tariffs.publish(other)


def test_an_unknown_tariff_is_refused(published):
    with UnitOfWork(published) as work, pytest.raises(TariffError):
        work.tariffs.tariff("9999")


def test_the_tariff_in_force_is_the_latest_one_published(published):
    with UnitOfWork(published) as work:
        assert work.tariffs.in_effect_on(date(2025, 1, 14)).code == "4200"
        assert work.tariffs.in_effect_on(date(2025, 7, 1)).code == "4300"


def test_a_day_before_anything_was_published_is_refused(published):
    with UnitOfWork(published) as work, pytest.raises(TariffError):
        work.tariffs.in_effect_on(date(2024, 1, 1))


def test_the_whole_book_loads_back(published):
    with UnitOfWork(published) as work:
        book = work.tariffs.book()
    assert len(book) == 2
    assert book.in_effect_on(date(2025, 8, 1)).code == "4300"


def test_a_negative_step_is_refused_by_the_table(engine):
    with pytest.raises(IntegrityError), UnitOfWork(engine) as work:
        row = TariffRow(
            code="4900", effective=date(2024, 7, 1), free_days_loaded=0, free_days_empty=0
        )
        row.steps = [TariffStepRow(position=0, through=0, cents=-1)]
        work.session.add(row)
        work.commit()


def test_negative_free_time_is_refused_by_the_table(engine):
    with pytest.raises(IntegrityError), UnitOfWork(engine) as work:
        work.session.add(
            TariffRow(
                code="4900",
                effective=date(2024, 7, 1),
                free_days_loaded=-1,
                free_days_empty=0,
            )
        )
        work.commit()


def test_an_agreement_goes_in_and_comes_back(engine):
    agreement = AverageAgreement("PWRGRN", 12000, date(2024, 7, 1))
    with UnitOfWork(engine) as work:
        work.tariffs.sign(agreement)
        work.commit()
    with UnitOfWork(engine) as work:
        assert work.tariffs.agreement_for("PWRGRN", date(2025, 1, 14)) == agreement


def test_signing_the_same_agreement_twice_is_refused(engine):
    agreement = AverageAgreement("PWRGRN", 12000, date(2024, 7, 1))
    with UnitOfWork(engine) as work:
        work.tariffs.sign(agreement)
        with pytest.raises(TariffError):
            work.tariffs.sign(agreement)


def test_an_expired_agreement_is_not_in_force(engine):
    with UnitOfWork(engine) as work:
        work.tariffs.sign(AverageAgreement("PWRGRN", 12000, date(2024, 7, 1), date(2024, 12, 31)))
        work.commit()
    with UnitOfWork(engine) as work, pytest.raises(TariffError):
        work.tariffs.agreement_for("PWRGRN", date(2025, 1, 14))


def test_a_second_agreement_takes_over_when_the_first_expires(engine):
    with UnitOfWork(engine) as work:
        work.tariffs.sign(AverageAgreement("PWRGRN", 12000, date(2024, 7, 1), date(2024, 12, 31)))
        work.tariffs.sign(AverageAgreement("PWRGRN", 13500, date(2025, 1, 1)))
        work.commit()
    with UnitOfWork(engine) as work:
        found = work.tariffs.agreement_for("PWRGRN", date(2025, 1, 14))
    assert found.daily_cents == 13500


def test_a_shipper_with_no_agreement_is_refused(engine):
    with UnitOfWork(engine) as work, pytest.raises(TariffError):
        work.tariffs.agreement_for("ESCPAP", date(2025, 1, 14))


def test_the_agreements_come_back_in_shipper_and_date_order(engine):
    with UnitOfWork(engine) as work:
        work.tariffs.sign(AverageAgreement("PWRGRN", 13500, date(2025, 1, 1)))
        work.tariffs.sign(AverageAgreement("PWRGRN", 12000, date(2024, 7, 1), date(2024, 12, 31)))
        work.commit()
    with UnitOfWork(engine) as work:
        assert [one.effective for one in work.tariffs.agreements()] == [
            date(2024, 7, 1),
            date(2025, 1, 1),
        ]
