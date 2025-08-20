from datetime import date

import pytest
from sqlalchemy.exc import IntegrityError

from drawbarline.errors import BillingError
from drawbarline.marks import ReportingMark
from drawbarline.money import Money
from drawbarline.persistence.base import create_all, make_engine, table_names
from drawbarline.persistence.models import DepreciationRow, PriceLineRow
from drawbarline.persistence.uow import UnitOfWork
from drawbarline.repairs.codes import JobCode
from drawbarline.repairs.pricing import Depreciation, LaborRate, PriceLine

WHEELSET = JobCode("4312")
COUPLER = JobCode("5104")
CN = ReportingMark("CN")
ELS = ReportingMark("ELS")


@pytest.fixture
def engine():
    engine = make_engine()
    create_all(engine)
    return engine


@pytest.fixture
def filed(engine):
    with UnitOfWork(engine) as work:
        work.repair_pricing.add_price(PriceLine(WHEELSET, date(2024, 7, 1), 250, 128000))
        work.repair_pricing.add_price(PriceLine(WHEELSET, date(2025, 7, 1), 250, 139000))
        work.repair_pricing.add_price(PriceLine(COUPLER, date(2024, 7, 1), 125, 84000))
        work.repair_pricing.set_depreciation(Depreciation("43", 4, 20))
        work.repair_pricing.file_labor(LaborRate(CN, date(2024, 7, 1), 4200))
        work.repair_pricing.file_labor(LaborRate(ELS, date(2024, 7, 1), 3900))
        work.commit()
    return engine


def test_the_repair_tables_are_on_the_metadata():
    for name in ("price_line", "depreciation", "labor_rate"):
        assert name in table_names()


def test_a_price_row_repr_names_the_job_and_date():
    row = PriceLineRow(job="4312", effective=date(2024, 7, 1), labor_hundredths=1, material_cents=1)
    assert repr(row) == "<PriceLineRow 4312 from 2024-07-01>"


def test_the_master_loads_back(filed):
    with UnitOfWork(filed) as work:
        master = work.repair_pricing.master()
    assert master.line_for(WHEELSET, date(2025, 1, 14)).material == Money(128000)
    assert master.line_for(WHEELSET, date(2025, 7, 1)).material == Money(139000)


def test_the_depreciation_rules_load_with_it(filed):
    with UnitOfWork(filed) as work:
        master = work.repair_pricing.master()
    assert master.material_for(WHEELSET, date(2025, 1, 14), 5) == Money(102400)
    assert master.material_for(COUPLER, date(2025, 1, 14), 20) == Money(84000)


def test_pricing_a_job_twice_from_one_date_is_refused(filed):
    with UnitOfWork(filed) as work, pytest.raises(BillingError):
        work.repair_pricing.add_price(PriceLine(WHEELSET, date(2024, 7, 1), 1, 1))


def test_a_depreciation_rule_is_replaced_in_place(filed):
    with UnitOfWork(filed) as work:
        work.repair_pricing.set_depreciation(Depreciation("43", 6, 10))
        work.commit()
    with UnitOfWork(filed) as work:
        master = work.repair_pricing.master()
        assert master.depreciation_for(WHEELSET).percent_a_year == 6
        rows = work.session.query(DepreciationRow).all()
    assert len(rows) == 1


def test_a_percentage_over_a_hundred_is_refused_by_the_table(engine):
    with pytest.raises(IntegrityError), UnitOfWork(engine) as work:
        work.session.add(DepreciationRow(family="43", percent_a_year=140, floor_percent=20))
        work.commit()


def test_a_negative_material_price_is_refused_by_the_table(engine):
    with pytest.raises(IntegrityError), UnitOfWork(engine) as work:
        work.session.add(
            PriceLineRow(
                job="4312",
                effective=date(2024, 7, 1),
                labor_hundredths=1,
                material_cents=-1,
            )
        )
        work.commit()


def test_the_labor_rates_load_back(filed):
    with UnitOfWork(filed) as work:
        rates = work.repair_pricing.labor_rates()
    assert rates.rate_for(CN, date(2025, 1, 14)).cents_an_hour == 4200
    assert [str(one) for one in rates.roads()] == ["CN", "ELS"]


def test_a_rate_can_be_asked_for_directly(filed):
    with UnitOfWork(filed) as work:
        assert work.repair_pricing.rate_for(ELS, date(2025, 1, 14)).cents_an_hour == 3900


def test_a_road_filing_twice_on_one_day_is_refused(filed):
    with UnitOfWork(filed) as work, pytest.raises(BillingError):
        work.repair_pricing.file_labor(LaborRate(CN, date(2024, 7, 1), 4300))


def test_a_later_rate_takes_over(filed):
    with UnitOfWork(filed) as work:
        work.repair_pricing.file_labor(LaborRate(CN, date(2025, 7, 1), 4550))
        work.commit()
    with UnitOfWork(filed) as work:
        rates = work.repair_pricing.labor_rates()
    assert rates.rate_for(CN, date(2025, 7, 1)).cents_an_hour == 4550


def test_a_card_prices_the_same_way_out_of_the_database(filed):
    from drawbarline.marks import CarMark
    from drawbarline.repairs.card import Defect, RepairCard, price
    from drawbarline.repairs.codes import WhyMade

    card = RepairCard(
        number=88104,
        car=CarMark.parse("DBL 104207"),
        performed_by=CN,
        on=date(2025, 1, 14),
        station="ESC",
        defects=(Defect(WHEELSET, WhyMade("01"), part_years=5),),
    )
    with UnitOfWork(filed) as work:
        priced = price(card, work.repair_pricing.master(), work.repair_pricing.labor_rates())
    assert priced.total == Money(10500 + 102400)


def test_the_repository_counts_its_price_lines(filed):
    with UnitOfWork(filed) as work:
        assert len(work.repair_pricing) == 3


def test_an_empty_master_prices_nothing(engine):
    with UnitOfWork(engine) as work, pytest.raises(BillingError):
        work.repair_pricing.master().line_for(WHEELSET, date(2025, 1, 14))
