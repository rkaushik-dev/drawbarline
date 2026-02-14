from datetime import date, time

import pytest
from sqlalchemy.exc import IntegrityError

from drawbarline.agreements import AgreementError, InterchangeAgreement
from drawbarline.fleet.aar import CarKind
from drawbarline.marks import ReportingMark
from drawbarline.persistence.base import create_all, make_engine, table_names
from drawbarline.persistence.models import AgreementRow
from drawbarline.persistence.repositories.agreements import (
    kinds_from_text,
    kinds_to_text,
)
from drawbarline.persistence.uow import UnitOfWork

CN = ReportingMark("CN")
ELS = ReportingMark("ELS")
HOPPER = CarKind.COVERED_HOPPER
BOX = CarKind.BOX


def an_agreement(**changed):
    fields = {
        "road": CN,
        "junction": "ESC",
        "effective": date(2024, 7, 1),
        "kinds": frozenset({HOPPER, BOX}),
        "daily_cap": 40,
        "opens": time(7, 0),
        "closes": time(19, 0),
    }
    fields.update(changed)
    return InterchangeAgreement(**fields)


@pytest.fixture
def engine():
    engine = make_engine()
    create_all(engine)
    return engine


@pytest.fixture
def signed(engine):
    with UnitOfWork(engine) as work:
        work.agreements.sign(an_agreement())
        work.agreements.sign(an_agreement(road=ELS, kinds=frozenset({HOPPER})))
        work.commit()
    return engine


def test_the_agreement_table_is_on_the_metadata():
    assert "interchange_agreement" in table_names()


def test_a_row_repr_names_the_junction_and_road():
    row = AgreementRow(
        road=CN,
        junction="ESC",
        effective=date(2024, 7, 1),
        kinds="XL",
        daily_cap=40,
        opens=time(7),
        closes=time(19),
    )
    assert repr(row) == "<AgreementRow ESC/CN from 2024-07-01>"


def test_the_kinds_pack_in_the_published_order():
    assert kinds_to_text(frozenset({BOX, HOPPER})) == "XL"


def test_the_kinds_read_back_as_a_set():
    assert kinds_from_text("XL") == frozenset({BOX, HOPPER})


def test_a_letter_nobody_publishes_is_refused_on_the_way_back():
    from drawbarline.fleet.aar import EquipmentError

    with pytest.raises(EquipmentError):
        kinds_from_text("XZ")


def test_an_agreement_goes_in_and_comes_back_whole(signed):
    with UnitOfWork(signed) as work:
        assert work.agreements.at("ESC", CN, date(2025, 1, 14)) == an_agreement()


def test_the_hours_survive_the_trip(signed):
    with UnitOfWork(signed) as work:
        found = work.agreements.at("ESC", CN, date(2025, 1, 14))
    assert found.opens == time(7, 0)
    assert found.closes == time(19, 0)


def test_the_same_agreement_twice_is_refused(signed):
    with UnitOfWork(signed) as work, pytest.raises(AgreementError):
        work.agreements.sign(an_agreement())


def test_a_junction_with_no_agreement_is_refused(signed):
    with UnitOfWork(signed) as work, pytest.raises(AgreementError):
        work.agreements.at("GRB", CN, date(2025, 1, 14))


def test_an_agreement_before_it_takes_effect_is_not_in_force(signed):
    with UnitOfWork(signed) as work, pytest.raises(AgreementError):
        work.agreements.at("ESC", CN, date(2024, 1, 1))


def test_a_later_agreement_takes_over(engine):
    with UnitOfWork(engine) as work:
        work.agreements.sign(an_agreement())
        work.agreements.sign(an_agreement(effective=date(2025, 7, 1), daily_cap=60))
        work.commit()
    with UnitOfWork(engine) as work:
        assert work.agreements.at("ESC", CN, date(2025, 8, 1)).daily_cap == 60
        assert work.agreements.at("ESC", CN, date(2025, 1, 1)).daily_cap == 40


def test_an_expired_agreement_is_skipped(engine):
    with UnitOfWork(engine) as work:
        work.agreements.sign(an_agreement(expires=date(2024, 12, 31)))
        work.commit()
    with UnitOfWork(engine) as work, pytest.raises(AgreementError):
        work.agreements.at("ESC", CN, date(2025, 1, 14))


def test_the_live_agreements_come_back(signed):
    with UnitOfWork(signed) as work:
        found = work.agreements.in_force_on(date(2025, 1, 14))
    assert [str(one.road) for one in found] == ["CN", "ELS"]


def test_nothing_is_live_before_anything_was_signed(signed):
    with UnitOfWork(signed) as work:
        assert work.agreements.in_force_on(date(2024, 1, 1)) == []


def test_a_cap_of_nothing_is_refused_by_the_table(engine):
    with pytest.raises(IntegrityError), UnitOfWork(engine) as work:
        work.session.add(
            AgreementRow(
                road=CN,
                junction="ESC",
                effective=date(2024, 7, 1),
                kinds="XL",
                daily_cap=0,
                opens=time(7),
                closes=time(19),
            )
        )
        work.commit()


def test_a_junction_that_closes_first_is_refused_by_the_table(engine):
    with pytest.raises(IntegrityError), UnitOfWork(engine) as work:
        work.session.add(
            AgreementRow(
                road=CN,
                junction="ESC",
                effective=date(2024, 7, 1),
                kinds="XL",
                daily_cap=40,
                opens=time(19),
                closes=time(7),
            )
        )
        work.commit()


def test_the_repository_counts_what_it_signed(signed):
    with UnitOfWork(signed) as work:
        assert len(work.agreements) == 2
        assert len(work.agreements.all()) == 2
