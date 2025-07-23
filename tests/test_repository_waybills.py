from datetime import date

import pytest
from sqlalchemy.exc import IntegrityError

from drawbarline.errors import UnknownCar
from drawbarline.lading import WHEAT, Lading
from drawbarline.marks import CarMark
from drawbarline.persistence.base import create_all, make_engine, table_names
from drawbarline.persistence.models import WaybillRow
from drawbarline.persistence.uow import UnitOfWork
from drawbarline.waybill import Waybill, WaybillError
from drawbarline.weight import Weight
from support import a_car

GRAIN = Lading(WHEAT, Weight(198000))


def a_waybill(number=41207, **changed):
    fields = {
        "number": number,
        "dated": date(2025, 1, 14),
        "car": CarMark.parse("CN 400100"),
        "shipper": "PWRGRN",
        "consignee": "ESCPAP",
        "route": ("PWR", "HRM", "BKR", "ESC"),
        "lading": GRAIN,
    }
    fields.update(changed)
    return Waybill(**fields)


@pytest.fixture
def engine():
    engine = make_engine()
    create_all(engine)
    with UnitOfWork(engine) as work:
        work.fleet.add(a_car("CN 400100"))
        work.fleet.add(a_car("CN 400101"))
        work.commit()
    return engine


def test_the_waybill_tables_are_on_the_metadata():
    assert "waybill" in table_names()
    assert "route_stop" in table_names()


def test_a_row_repr_names_the_number_and_car():
    row = WaybillRow(
        number=41207,
        dated=date(2025, 1, 14),
        car_mark=CarMark.parse("CN 400100"),
        shipper="a",
        consignee="b",
    )
    assert repr(row) == "<WaybillRow 41207 CN 400100>"


def test_a_waybill_goes_in_and_comes_back_whole(engine):
    with UnitOfWork(engine) as work:
        work.waybills.issue(a_waybill())
        work.commit()
    with UnitOfWork(engine) as work:
        assert work.waybills.get(41207) == a_waybill()


def test_the_route_keeps_its_order(engine):
    with UnitOfWork(engine) as work:
        work.waybills.issue(a_waybill())
        work.commit()
    with UnitOfWork(engine) as work:
        assert work.waybills.get(41207).route == ("PWR", "HRM", "BKR", "ESC")


def test_a_stored_waybill_still_measures_its_mileage(engine):
    with UnitOfWork(engine) as work:
        work.waybills.issue(a_waybill())
        work.commit()
    with UnitOfWork(engine) as work:
        assert work.waybills.get(41207).tenths == 336


def test_an_empty_waybill_carries_no_lading(engine):
    with UnitOfWork(engine) as work:
        work.waybills.issue(a_waybill(lading=None))
        work.commit()
    with UnitOfWork(engine) as work:
        found = work.waybills.get(41207)
    assert found.loaded is False
    assert found.lading is None


def test_the_lading_comes_back_with_its_commodity(engine):
    with UnitOfWork(engine) as work:
        work.waybills.issue(a_waybill())
        work.commit()
    with UnitOfWork(engine) as work:
        found = work.waybills.get(41207)
    assert found.lading.commodity.name == "wheat"
    assert found.lading.commodity.bulk is True
    assert found.lading.weight == Weight(198000)


def test_the_same_number_twice_is_refused(engine):
    with UnitOfWork(engine) as work:
        work.waybills.issue(a_waybill())
        with pytest.raises(WaybillError):
            work.waybills.issue(a_waybill())


def test_a_waybill_for_a_car_nobody_filed_is_refused(engine):
    with UnitOfWork(engine) as work, pytest.raises(UnknownCar):
        work.waybills.issue(a_waybill(car=CarMark.parse("WC 000001")))


def test_a_waybill_nobody_issued_is_refused(engine):
    with UnitOfWork(engine) as work, pytest.raises(WaybillError):
        work.waybills.get(99999)


def test_waybills_come_back_for_one_car(engine):
    with UnitOfWork(engine) as work:
        work.waybills.issue(a_waybill())
        work.waybills.issue(a_waybill(41208, car=CarMark.parse("CN 400101")))
        work.commit()
    with UnitOfWork(engine) as work:
        found = work.waybills.for_car(CarMark.parse("CN 400100"))
    assert [one.number for one in found] == [41207]


def test_waybills_come_back_for_a_span_of_days(engine):
    with UnitOfWork(engine) as work:
        work.waybills.issue(a_waybill())
        work.waybills.issue(a_waybill(41208, dated=date(2025, 2, 3)))
        work.commit()
    with UnitOfWork(engine) as work:
        found = work.waybills.dated_between(date(2025, 1, 1), date(2025, 1, 31))
    assert [one.number for one in found] == [41207]


def test_loaded_waybills_come_back_on_their_own(engine):
    with UnitOfWork(engine) as work:
        work.waybills.issue(a_waybill())
        work.waybills.issue(a_waybill(41208, lading=None))
        work.commit()
    with UnitOfWork(engine) as work:
        assert [one.number for one in work.waybills.loaded_only()] == [41207]


def test_a_diversion_is_a_second_waybill(engine):
    with UnitOfWork(engine) as work:
        first = work.waybills.issue(a_waybill())
        work.waybills.issue(first.divert("HRM", "GRB", number=41208))
        work.commit()
    with UnitOfWork(engine) as work:
        assert work.waybills.get(41207).route == ("PWR", "HRM", "BKR", "ESC")
        assert work.waybills.get(41208).route == ("PWR", "HRM", "GRB")


def test_a_waybill_numbered_below_one_is_refused_by_the_table(engine):
    with pytest.raises(IntegrityError), UnitOfWork(engine) as work:
        work.session.add(
            WaybillRow(
                number=0,
                dated=date(2025, 1, 14),
                car_mark=CarMark.parse("CN 400100"),
                shipper="a",
                consignee="b",
            )
        )
        work.commit()


def test_the_repository_counts_what_it_issued(engine):
    with UnitOfWork(engine) as work:
        work.waybills.issue(a_waybill())
        work.waybills.issue(a_waybill(41208))
        work.commit()
    with UnitOfWork(engine) as work:
        assert len(work.waybills) == 2
