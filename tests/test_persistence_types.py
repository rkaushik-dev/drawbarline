from datetime import UTC, datetime

import pytest
from sqlalchemy import Column, Integer, MetaData, Table, insert, select
from sqlalchemy.exc import StatementError

from drawbarline.clock import utc
from drawbarline.errors import ConfigurationError
from drawbarline.fleet.aar import CarTypeCode, MechanicalDesignation
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.measure import Length
from drawbarline.money import Money
from drawbarline.persistence.base import make_engine, opened, table_names
from drawbarline.persistence.types import (
    CarMarkText,
    DesignationText,
    LengthInches,
    MarkText,
    MoneyCents,
    TypeCodeText,
    Utc,
    WeightPounds,
)
from drawbarline.weight import Weight

# This table exists only to exercise the column types. It is deliberately on
# its own metadata: a scratch table on the application metadata would be built
# by create_all, absent from the migration, and would fail the check that the
# two agree.
SCRATCH = MetaData()

SAMPLE = Table(
    "sample_of_every_type",
    SCRATCH,
    Column("id", Integer, primary_key=True),
    Column("amount", MoneyCents),
    Column("weight", WeightPounds),
    Column("length", LengthInches),
    Column("road", MarkText),
    Column("car", CarMarkText),
    Column("type_code", TypeCodeText),
    Column("designation", DesignationText),
    Column("moment", Utc),
)


@pytest.fixture
def engine():
    engine = make_engine()
    SCRATCH.create_all(engine)
    return engine


def round_trip(engine, **values):
    with opened(engine) as session:
        session.execute(insert(SAMPLE).values(id=1, **values))
    with opened(engine) as session:
        return session.execute(select(SAMPLE).where(SAMPLE.c.id == 1)).one()


def test_money_comes_back_as_money(engine):
    assert round_trip(engine, amount=Money(-12345)).amount == Money(-12345)


def test_a_weight_comes_back_as_a_weight(engine):
    assert round_trip(engine, weight=Weight(286000)).weight == Weight(286000)


def test_a_length_comes_back_as_a_length(engine):
    assert round_trip(engine, length=Length.of(59, 6)).length == Length.of(59, 6)


def test_a_reporting_mark_comes_back_whole(engine):
    assert round_trip(engine, road=ReportingMark("ELS")).road == ReportingMark("ELS")


def test_a_car_mark_comes_back_whole(engine):
    car = CarMark.parse("DBL 104207")
    assert round_trip(engine, car=car).car == car


def test_a_type_code_comes_back_whole(engine):
    assert round_trip(engine, type_code=CarTypeCode("L351")).type_code == CarTypeCode("L351")


def test_a_designation_comes_back_whole(engine):
    found = round_trip(engine, designation=MechanicalDesignation("LO"))
    assert found.designation == MechanicalDesignation("LO")


def test_a_moment_comes_back_aware_and_in_utc(engine):
    found = round_trip(engine, moment=utc(2025, 1, 14, 14, 30))
    assert found.moment == utc(2025, 1, 14, 14, 30)
    assert found.moment.tzinfo is UTC


def test_a_moment_in_another_zone_is_stored_as_utc(engine):
    from zoneinfo import ZoneInfo

    local = datetime(2025, 1, 14, 9, 30, tzinfo=ZoneInfo("America/Detroit"))
    assert round_trip(engine, moment=local).moment == utc(2025, 1, 14, 14, 30)


def test_a_naive_moment_is_refused(engine):
    # sqlalchemy wraps whatever a bind processor raises, so the refusal
    # arrives as its own error with ours underneath.
    with pytest.raises(StatementError) as caught:
        round_trip(engine, moment=datetime(2025, 1, 14, 14, 30))
    assert isinstance(caught.value.orig, ConfigurationError)


def test_every_column_takes_nothing(engine):
    found = round_trip(engine)
    assert found.amount is None
    assert found.car is None
    assert found.moment is None


def test_a_url_of_nothing_is_refused():
    with pytest.raises(ConfigurationError):
        make_engine("   ")


def test_foreign_keys_are_on(engine):
    with opened(engine) as session:
        from sqlalchemy import text

        assert session.execute(text("PRAGMA foreign_keys")).scalar() == 1


def test_a_failed_session_rolls_back(engine):
    with pytest.raises(RuntimeError), opened(engine) as session:
        session.execute(insert(SAMPLE).values(id=2, amount=Money(1)))
        raise RuntimeError("something went wrong")
    with opened(engine) as session:
        assert session.execute(select(SAMPLE)).all() == []


def test_the_scratch_table_stays_off_the_application_metadata():
    assert "sample_of_every_type" not in table_names()
    assert "car" in table_names()
