from datetime import date

import pytest
from sqlalchemy.exc import IntegrityError

from drawbarline.carhire.rates import Band, Rate
from drawbarline.errors import RateError
from drawbarline.fleet.aar import CarTypeCode
from drawbarline.persistence.base import create_all, make_engine, table_names
from drawbarline.persistence.models import RateBandRow
from drawbarline.persistence.uow import UnitOfWork

HOPPER = CarTypeCode("L351")
BOX = CarTypeCode("X202")


def some_bands():
    return [
        Band(HOPPER, 0, 9, date(2024, 7, 1), Rate(4630, 1210)),
        Band(HOPPER, 10, 19, date(2024, 7, 1), Rate(3180, 1210)),
        Band(HOPPER, 0, 9, date(2025, 7, 1), Rate(4880, 1290)),
        Band(BOX, 0, 99, date(2024, 7, 1), Rate(2240, 980)),
    ]


@pytest.fixture
def engine():
    engine = make_engine()
    create_all(engine)
    return engine


@pytest.fixture
def filed(engine):
    with UnitOfWork(engine) as work:
        work.rates.file_all(some_bands())
        work.commit()
    return engine


def test_the_band_table_is_on_the_metadata():
    assert "car_hire_band" in table_names()


def test_a_row_repr_names_the_type_ages_and_date():
    row = RateBandRow(
        type_code=HOPPER,
        from_age=0,
        to_age=9,
        effective=date(2024, 7, 1),
        hourly_tenths=1,
        mileage_mills=1,
    )
    assert repr(row) == "<RateBandRow L351 0-9 2024-07-01>"


def test_a_band_goes_in_and_comes_back_whole(filed):
    with UnitOfWork(filed) as work:
        found = work.rates.bands_for(BOX)
    assert found == [Band(BOX, 0, 99, date(2024, 7, 1), Rate(2240, 980))]


def test_filing_the_same_band_twice_is_refused(filed):
    with UnitOfWork(filed) as work, pytest.raises(RateError):
        work.rates.file(Band(HOPPER, 0, 9, date(2024, 7, 1), Rate(1, 1)))


def test_the_table_refuses_a_duplicate_the_repository_did_not_see(engine):
    from drawbarline.persistence.mappers.rates import row_from_band

    band = Band(HOPPER, 0, 9, date(2024, 7, 1), Rate(4630, 1210))
    with pytest.raises(IntegrityError), UnitOfWork(engine) as work:
        work.session.add(row_from_band(band))
        work.session.add(row_from_band(band))
        work.commit()


def test_a_band_that_runs_backwards_is_refused_by_the_table(engine):
    with pytest.raises(IntegrityError), UnitOfWork(engine) as work:
        work.session.add(
            RateBandRow(
                type_code=HOPPER,
                from_age=19,
                to_age=10,
                effective=date(2024, 7, 1),
                hourly_tenths=1,
                mileage_mills=1,
            )
        )
        work.commit()


def test_a_negative_rate_is_refused_by_the_table(engine):
    with pytest.raises(IntegrityError), UnitOfWork(engine) as work:
        work.session.add(
            RateBandRow(
                type_code=HOPPER,
                from_age=0,
                to_age=9,
                effective=date(2024, 7, 1),
                hourly_tenths=-1,
                mileage_mills=1,
            )
        )
        work.commit()


def test_bands_come_back_in_date_and_age_order(filed):
    with UnitOfWork(filed) as work:
        found = work.rates.bands_for(HOPPER)
    assert [(one.effective, one.from_age) for one in found] == [
        (date(2024, 7, 1), 0),
        (date(2024, 7, 1), 10),
        (date(2025, 7, 1), 0),
    ]


def test_a_type_with_no_bands_comes_back_empty(filed):
    with UnitOfWork(filed) as work:
        assert work.rates.bands_for(CarTypeCode("T104")) == []


def test_the_effective_dates_come_back_once_each(filed):
    with UnitOfWork(filed) as work:
        assert work.rates.effective_dates(HOPPER) == [
            date(2024, 7, 1),
            date(2025, 7, 1),
        ]


def test_the_whole_schedule_loads_into_the_domain_table(filed):
    with UnitOfWork(filed) as work:
        table = work.rates.table()
    assert table.lookup(HOPPER, 3, date(2025, 1, 14)) == Rate(4630, 1210)
    assert table.lookup(HOPPER, 3, date(2025, 7, 1)) == Rate(4880, 1290)
    assert table.lookup(BOX, 40, date(2025, 1, 14)) == Rate(2240, 980)


def test_a_loaded_schedule_reports_where_the_rate_steps(filed):
    with UnitOfWork(filed) as work:
        table = work.rates.table()
    assert table.changes_between(HOPPER, date(2025, 6, 1), date(2025, 8, 1)) == [date(2025, 7, 1)]


def test_the_repository_counts_its_bands(filed):
    with UnitOfWork(filed) as work:
        assert len(work.rates) == 4


def test_an_empty_schedule_loads_as_an_empty_table(engine):
    with UnitOfWork(engine) as work:
        assert len(work.rates.table()) == 0
