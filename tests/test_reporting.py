"""The reporting layer is what both interfaces call, so it is tested directly.

The property that matters is that the command line and the http side get the
same lines. Testing that through both would be testing click and fastapi. So
the layer is tested here and each interface is tested for calling it.
"""

from datetime import date, datetime

import pytest

from drawbarline import reporting
from drawbarline.clock import utc
from drawbarline.money import Money
from drawbarline.persistence.base import create_all, make_engine
from drawbarline.persistence.uow import UnitOfWork
from drawbarline.settlement.period import Period
from support import (
    ELEVATOR,
    MILL,
    a_car,
    delivered,
    moved,
    placed,
    pulled,
    received,
    released,
    some_bands,
)

JANUARY = Period(2025, 1)


@pytest.fixture
def worked():
    from drawbarline.customers import Industry
    from drawbarline.demurrage.tariff import Step, Tariff

    engine = make_engine()
    create_all(engine)
    with UnitOfWork(engine) as work:
        work.customers.add_shipper(MILL)
        work.customers.add_shipper(ELEVATOR)
        work.customers.add_industry(Industry("ESCPAP1", "mill house", "ESC", MILL, 11))
        work.tariffs.publish(
            Tariff("4200", date(2024, 7, 1), 2, 1, (Step(2, 10000), Step(0, 20000)))
        )
        for band in some_bands():
            work.rates.file(band)
        work.fleet.add(a_car("CN 400100"))
        work.commit()

    with UnitOfWork(engine) as work:
        log = work.events.log()
        received(log, "CN 400100", 6)
        moved(log, "CN 400100", 6, 336)
        placed(log, "CN 400100", 7, industry="ESCPAP1")
        released(log, "CN 400100", 14, industry="ESCPAP1")
        pulled(log, "CN 400100", 15, industry="ESCPAP1")
        delivered(log, "CN 400100", 16)
        for car in log.cars():
            for event in log.stream(car):
                work.events.record(event)
        work.commit()
    return engine


def lines_of(engine, call, *args, **kwargs):
    with UnitOfWork(engine) as work:
        return call(work, *args, **kwargs)


def test_the_car_hire_report_names_the_road(worked):
    found = lines_of(worked, reporting.car_hire, JANUARY)
    assert found[0] == "CAR HIRE LIABILITY"
    assert any(line.startswith("CN") for line in found)


def test_the_car_hire_detail_can_be_asked_for_one_road(worked):
    found = lines_of(worked, reporting.car_hire, JANUARY, "CN")
    assert found[0] == "CAR HIRE DETAIL CN"


def test_the_totals_agree_with_the_page(worked):
    page = lines_of(worked, reporting.car_hire, JANUARY)
    totals = lines_of(worked, reporting.car_hire_totals, JANUARY)
    row = [line for line in page if line.startswith("CN")][0]
    assert str(totals["CN"]) in row


def test_the_totals_come_back_as_money(worked):
    totals = lines_of(worked, reporting.car_hire_totals, JANUARY)
    assert isinstance(totals["CN"], Money)


def test_the_demurrage_report_runs(worked):
    found = lines_of(worked, reporting.demurrage, JANUARY)
    assert found[0] == "DEMURRAGE DETENTION"


def test_the_demurrage_report_summarises_by_shipper(worked):
    found = lines_of(worked, reporting.demurrage, JANUARY, by_shipper=True)
    assert found[0] == "DEMURRAGE BY SHIPPER"


def test_the_utilisation_report_runs(worked):
    found = lines_of(worked, reporting.utilisation, JANUARY)
    assert any("CN 400100" in line for line in found)


def test_the_interchange_report_runs(worked):
    found = lines_of(worked, reporting.interchange, JANUARY)
    assert found[0] == "INTERCHANGE BALANCE"


def test_the_crossings_come_back_as_data(worked):
    found = lines_of(worked, reporting.interchange_crossings, JANUARY)
    assert {one.junction for one in found} == {"ESC", "MRN"}


def test_the_on_hand_report_runs(worked):
    found = lines_of(worked, reporting.on_hand, utc(2025, 1, 10, 12))
    assert any("CN 400100" in line for line in found)


def test_a_car_already_delivered_is_not_on_hand(worked):
    found = lines_of(worked, reporting.on_hand, utc(2025, 1, 20, 12))
    assert any(line.startswith("0 cars") for line in found)


def test_the_age_report_runs(worked):
    found = lines_of(worked, reporting.fleet_age, date(2025, 7, 1))
    assert found[0] == "FLEET AGE PROFILE"


def test_the_age_profile_comes_back_as_data(worked):
    assert lines_of(worked, reporting.fleet_age_profile, date(2025, 7, 1)) == {"0 to 9": 1}


def test_the_repair_report_runs_with_no_cards(worked):
    found = lines_of(worked, reporting.repairs, JANUARY)
    assert any(line.startswith("0 roads") for line in found)


def test_the_exception_report_runs_with_nothing_open(worked):
    found = lines_of(worked, reporting.repairs, JANUARY, exceptions=True)
    assert any(line.startswith("0 open") for line in found)


def test_running_a_report_twice_gives_the_same_lines(worked):
    first = lines_of(worked, reporting.car_hire, JANUARY)
    second = lines_of(worked, reporting.car_hire, JANUARY)
    assert first == second


def test_no_report_writes_anything(worked):
    with UnitOfWork(worked) as work:
        before = len(work.events)
    lines_of(worked, reporting.car_hire, JANUARY)
    lines_of(worked, reporting.demurrage, JANUARY)
    with UnitOfWork(worked) as work:
        assert len(work.events) == before


def test_every_line_fits_the_paper(worked):
    for line in lines_of(worked, reporting.car_hire, JANUARY):
        assert len(line) <= 80


def test_the_wire_and_the_terminal_read_a_moment_the_same_way():
    from drawbarline.api.deps import moment_or_now
    from drawbarline.cli.readers import a_moment_utc

    day = datetime(2025, 1, 14, tzinfo=utc(2025, 1, 1).tzinfo)
    assert a_moment_utc("2025-01-14") == moment_or_now(day)


def test_a_moment_of_nothing_is_now_on_both_sides():
    from drawbarline.api.deps import moment_or_now
    from drawbarline.cli.readers import a_moment_utc

    assert a_moment_utc(None) is not None
    assert moment_or_now(None) is not None
