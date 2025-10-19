from datetime import date

import pytest
from sqlalchemy.exc import IntegrityError

from drawbarline.customers import Industry, Shipper
from drawbarline.distribution import CarOrder, DistributionError
from drawbarline.fleet.aar import CarKind
from drawbarline.persistence.base import create_all, make_engine, table_names
from drawbarline.persistence.models import CarOrderRow
from drawbarline.persistence.uow import UnitOfWork

MILL = Shipper("ESCPAP", "Escanaba Paper")
ELEVATOR = Shipper("PWRGRN", "Powers Grain")


def an_order(number=1, **changed):
    fields = {
        "number": number,
        "shipper": "ESCPAP",
        "industry": "ESCPAP1",
        "kind": CarKind.COVERED_HOPPER,
        "cars": 4,
        "wanted_on": date(2025, 1, 14),
    }
    fields.update(changed)
    return CarOrder(**fields)


@pytest.fixture
def engine():
    engine = make_engine()
    create_all(engine)
    with UnitOfWork(engine) as work:
        work.customers.add_shipper(MILL)
        work.customers.add_shipper(ELEVATOR)
        work.customers.add_industry(Industry("ESCPAP1", "mill house", "ESC", MILL, 11))
        work.customers.add_industry(Industry("PWRGRN1", "elevator", "PWR", ELEVATOR, 20))
        work.commit()
    return engine


@pytest.fixture
def placed(engine):
    with UnitOfWork(engine) as work:
        work.orders.place(an_order(), date(2025, 1, 10))
        work.commit()
    return engine


def test_the_order_table_is_on_the_metadata():
    assert "car_order" in table_names()


def test_a_row_repr_names_the_order_and_shipper():
    row = CarOrderRow(
        number=1,
        shipper_code="ESCPAP",
        industry_code="ESCPAP1",
        kind="L",
        cars=4,
        wanted_on=date(2025, 1, 14),
        placed_on=date(2025, 1, 10),
    )
    assert repr(row) == "<CarOrderRow 1 ESCPAP 4>"


def test_an_order_goes_in_and_comes_back_whole(placed):
    with UnitOfWork(placed) as work:
        assert work.orders.get(1) == an_order()


def test_the_kind_survives_the_trip(placed):
    with UnitOfWork(placed) as work:
        assert work.orders.get(1).kind is CarKind.COVERED_HOPPER


def test_the_same_number_twice_is_refused(placed):
    with UnitOfWork(placed) as work, pytest.raises(DistributionError):
        work.orders.place(an_order(), date(2025, 1, 10))


def test_an_order_for_a_shipper_nobody_filed_is_refused(engine):
    with UnitOfWork(engine) as work, pytest.raises(DistributionError):
        work.orders.place(an_order(shipper="NOBODY"), date(2025, 1, 10))


def test_an_order_for_a_track_nobody_filed_is_refused(engine):
    with UnitOfWork(engine) as work, pytest.raises(DistributionError):
        work.orders.place(an_order(industry="NOWHERE"), date(2025, 1, 10))


def test_an_order_placed_after_the_cars_are_wanted_is_refused(engine):
    with UnitOfWork(engine) as work, pytest.raises(DistributionError):
        work.orders.place(an_order(), date(2025, 1, 20))


def test_an_order_placed_on_the_day_it_is_wanted_is_allowed(engine):
    with UnitOfWork(engine) as work:
        work.orders.place(an_order(), date(2025, 1, 14))
        work.commit()
    with UnitOfWork(engine) as work:
        assert len(work.orders) == 1


def test_an_order_nobody_placed_is_refused(placed):
    with UnitOfWork(placed) as work, pytest.raises(DistributionError):
        work.orders.get(99)


def test_an_open_order_shows_up_on_its_day(placed):
    with UnitOfWork(placed) as work:
        assert [one.number for one in work.orders.open_on(date(2025, 1, 12))] == [1]


def test_an_order_is_not_open_before_it_was_placed(placed):
    with UnitOfWork(placed) as work:
        assert work.orders.open_on(date(2025, 1, 9)) == []


def test_an_order_is_not_open_after_the_day_it_wanted(placed):
    with UnitOfWork(placed) as work:
        assert work.orders.open_on(date(2025, 1, 15)) == []


def test_open_orders_come_back_oldest_first(engine):
    with UnitOfWork(engine) as work:
        work.orders.place(an_order(1, wanted_on=date(2025, 1, 20)), date(2025, 1, 10))
        work.orders.place(an_order(2, wanted_on=date(2025, 1, 12)), date(2025, 1, 10))
        work.commit()
    with UnitOfWork(engine) as work:
        assert [one.number for one in work.orders.open_on(date(2025, 1, 11))] == [2, 1]


def test_a_cancelled_order_is_not_open(placed):
    with UnitOfWork(placed) as work:
        work.orders.cancel(1, date(2025, 1, 12))
        work.commit()
    with UnitOfWork(placed) as work:
        assert work.orders.open_on(date(2025, 1, 13)) == []
        assert work.orders.cancelled_on(1) == date(2025, 1, 12)


def test_cancelling_twice_is_refused(placed):
    with UnitOfWork(placed) as work:
        work.orders.cancel(1, date(2025, 1, 12))
        with pytest.raises(DistributionError):
            work.orders.cancel(1, date(2025, 1, 13))


def test_cancelling_before_it_was_placed_is_refused(placed):
    with UnitOfWork(placed) as work, pytest.raises(DistributionError):
        work.orders.cancel(1, date(2025, 1, 1))


def test_cancelling_something_never_placed_is_refused(placed):
    with UnitOfWork(placed) as work, pytest.raises(DistributionError):
        work.orders.cancel(99, date(2025, 1, 12))


def test_an_open_order_has_no_cancellation_date(placed):
    with UnitOfWork(placed) as work:
        assert work.orders.cancelled_on(1) is None


def test_orders_come_back_for_one_shipper(engine):
    with UnitOfWork(engine) as work:
        work.orders.place(an_order(1), date(2025, 1, 10))
        work.orders.place(an_order(2, shipper="PWRGRN", industry="PWRGRN1"), date(2025, 1, 10))
        work.commit()
    with UnitOfWork(engine) as work:
        assert [one.number for one in work.orders.for_shipper("PWRGRN")] == [2]


def test_an_order_for_no_cars_is_refused_by_the_table(engine):
    with pytest.raises(IntegrityError), UnitOfWork(engine) as work:
        work.session.add(
            CarOrderRow(
                number=9,
                shipper_code="ESCPAP",
                industry_code="ESCPAP1",
                kind="L",
                cars=0,
                wanted_on=date(2025, 1, 14),
                placed_on=date(2025, 1, 10),
            )
        )
        work.commit()


def test_the_stations_come_back_for_the_distribution(placed):
    with UnitOfWork(placed) as work:
        assert work.orders.stations() == {"ESCPAP1": "ESC", "PWRGRN1": "PWR"}
