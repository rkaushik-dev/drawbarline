from datetime import date

import pytest
from sqlalchemy.exc import IntegrityError

from drawbarline.customers import CustomerError, Industry, Shipper
from drawbarline.errors import ConfigurationError
from drawbarline.persistence.base import create_all, make_engine, table_names
from drawbarline.persistence.models import IndustryRow
from drawbarline.persistence.repositories.customers import days_from_text, days_to_text
from drawbarline.persistence.uow import UnitOfWork

MILL = Shipper("ESCPAP", "Escanaba Paper")
ELEVATOR = Shipper("PWRGRN", "Powers Grain", average_agreement=True)


def a_track(code="ESCPAP1", shipper=MILL, station="ESC", **changed):
    fields = {
        "code": code,
        "name": "mill house track",
        "station": station,
        "shipper": shipper,
        "spots": 11,
    }
    fields.update(changed)
    return Industry(**fields)


@pytest.fixture
def engine():
    engine = make_engine()
    create_all(engine)
    return engine


@pytest.fixture
def stocked(engine):
    with UnitOfWork(engine) as work:
        work.customers.add_shipper(MILL)
        work.customers.add_shipper(ELEVATOR)
        work.customers.add_industry(a_track())
        work.customers.add_industry(a_track("ESCPAP2", spots=6))
        work.customers.add_industry(a_track("PWRGRN1", ELEVATOR, "PWR", spots=20))
        work.commit()
    return engine


def test_the_customer_tables_are_on_the_metadata():
    assert "shipper" in table_names()
    assert "industry" in table_names()


def test_a_row_repr_names_the_track_and_station():
    row = IndustryRow(
        code="ESCPAP1",
        name="x",
        station="ESC",
        shipper_code="ESCPAP",
        spots=1,
        switched="mon",
    )
    assert repr(row) == "<IndustryRow ESCPAP1 at ESC>"


def test_the_switching_days_go_out_in_week_order():
    assert days_to_text(frozenset({"fri", "mon", "wed"})) == "mon,wed,fri"


def test_the_switching_days_come_back_as_a_set():
    assert days_from_text("mon,wed,fri") == frozenset({"mon", "wed", "fri"})


def test_a_day_that_is_not_a_day_is_refused_on_the_way_back():
    with pytest.raises(ConfigurationError):
        days_from_text("mon,funday")


def test_a_shipper_goes_in_and_comes_back(stocked):
    with UnitOfWork(stocked) as work:
        assert work.customers.shipper("PWRGRN").average_agreement is True


def test_the_same_shipper_twice_is_refused(stocked):
    with UnitOfWork(stocked) as work, pytest.raises(CustomerError):
        work.customers.add_shipper(MILL)


def test_an_unknown_shipper_is_refused(stocked):
    with UnitOfWork(stocked) as work, pytest.raises(CustomerError):
        work.customers.shipper("NOBODY")


def test_an_industry_goes_in_and_comes_back_whole(stocked):
    with UnitOfWork(stocked) as work:
        found = work.customers.industry("ESCPAP1")
    assert found == a_track()


def test_an_industry_keeps_its_switching_days(stocked):
    with UnitOfWork(stocked) as work:
        work.customers.add_industry(a_track("ESCPAP3", switched=frozenset({"tue", "thu"})))
        work.commit()
    with UnitOfWork(stocked) as work:
        assert work.customers.industry("ESCPAP3").switched == frozenset({"tue", "thu"})


def test_an_industry_whose_shipper_is_not_filed_is_refused(engine):
    with UnitOfWork(engine) as work, pytest.raises(CustomerError):
        work.customers.add_industry(a_track())


def test_the_same_industry_twice_is_refused(stocked):
    with UnitOfWork(stocked) as work, pytest.raises(CustomerError):
        work.customers.add_industry(a_track())


def test_an_unknown_industry_is_refused(stocked):
    with UnitOfWork(stocked) as work, pytest.raises(CustomerError):
        work.customers.industry("NOWHERE")


def test_a_track_with_no_spots_is_refused_by_the_table(engine):
    with pytest.raises(IntegrityError), UnitOfWork(engine) as work:
        work.customers.add_shipper(MILL)
        work.session.add(
            IndustryRow(
                code="X1",
                name="x",
                station="ESC",
                shipper_code="ESCPAP",
                spots=0,
                switched="mon",
            )
        )
        work.commit()


def test_a_track_nobody_switches_is_refused_by_the_table(engine):
    with pytest.raises(IntegrityError), UnitOfWork(engine) as work:
        work.customers.add_shipper(MILL)
        work.session.add(
            IndustryRow(
                code="X1",
                name="x",
                station="ESC",
                shipper_code="ESCPAP",
                spots=1,
                switched="",
            )
        )
        work.commit()


def test_industries_come_back_by_station(stocked):
    with UnitOfWork(stocked) as work:
        assert [one.code for one in work.customers.at_station("ESC")] == [
            "ESCPAP1",
            "ESCPAP2",
        ]


def test_shippers_come_back_in_code_order(stocked):
    with UnitOfWork(stocked) as work:
        assert [one.code for one in work.customers.shippers()] == ["ESCPAP", "PWRGRN"]


def test_the_whole_register_loads_back(stocked):
    with UnitOfWork(stocked) as work:
        register = work.customers.register()
    assert len(register) == 3
    assert register.industry("PWRGRN1").shipper.code == "PWRGRN"
    assert [one.code for one in register.on_average_agreement()] == ["PWRGRN"]


def test_a_loaded_register_still_knows_the_switching_days(stocked):
    with UnitOfWork(stocked) as work:
        register = work.customers.register()
    assert register.industry("ESCPAP1").switched_on(date(2025, 1, 14)) is True
    assert register.industry("ESCPAP1").switched_on(date(2025, 1, 18)) is False


def test_the_repository_counts_its_industries(stocked):
    with UnitOfWork(stocked) as work:
        assert len(work.customers) == 3
