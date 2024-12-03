from datetime import date

import pytest

from drawbarline.lading import SODIUM_HYDROXIDE, WHEAT, Lading
from drawbarline.marks import CarMark
from drawbarline.mileage import MileageError
from drawbarline.waybill import Waybill, WaybillError
from drawbarline.weight import Weight

GRAIN = Lading(WHEAT, Weight(198000))


def a_waybill(**changed):
    fields = {
        "number": 41207,
        "dated": date(2025, 1, 14),
        "car": CarMark.parse("DBL 104207"),
        "shipper": "PWRGRN",
        "consignee": "ESCPAP",
        "route": ("PWR", "HRM", "BKR", "ESC"),
        "lading": GRAIN,
    }
    fields.update(changed)
    return Waybill(**fields)


def test_a_waybill_prints_car_route_and_lading():
    assert str(a_waybill()) == "wb 41207 DBL 104207 PWR to ESC wheat"


def test_an_empty_waybill_says_empty():
    assert str(a_waybill(lading=None)).endswith("empty")


def test_the_route_gives_the_origin_and_destination():
    bill = a_waybill()
    assert bill.origin == "PWR"
    assert bill.destination == "ESC"


def test_a_loaded_waybill_knows_it():
    assert a_waybill().loaded is True


def test_an_empty_waybill_knows_it():
    assert a_waybill(lading=None).loaded is False


def test_a_waybill_takes_its_hazard_from_the_lading():
    caustic = Lading(SODIUM_HYDROXIDE, Weight(180000))
    assert a_waybill(lading=caustic).hazardous is True


def test_an_empty_waybill_is_not_hazardous():
    assert a_waybill(lading=None).hazardous is False


def test_a_waybill_numbered_below_one_is_refused():
    with pytest.raises(WaybillError):
        a_waybill(number=0)


def test_a_route_of_one_place_is_refused():
    with pytest.raises(WaybillError):
        a_waybill(route=("ESC",))


def test_a_route_through_a_place_we_do_not_serve_is_refused():
    with pytest.raises(MileageError):
        a_waybill(route=("PWR", "CHI"))


def test_a_route_that_repeats_a_station_running_is_refused():
    with pytest.raises(WaybillError):
        a_waybill(route=("PWR", "PWR", "ESC"))


def test_a_route_that_comes_back_later_is_allowed():
    bill = a_waybill(route=("PWR", "ESC", "PWR", "IMT"))
    assert bill.destination == "IMT"


def test_a_waybill_with_no_shipper_is_refused():
    with pytest.raises(WaybillError):
        a_waybill(shipper="  ")


def test_a_waybill_with_no_consignee_is_refused():
    with pytest.raises(WaybillError):
        a_waybill(consignee="")


def test_the_mileage_runs_along_the_route():
    assert a_waybill().tenths == 336


def test_the_mileage_reads_as_decimal_miles():
    assert a_waybill().miles == 33.6


def test_a_route_down_the_branch_measures_through_powers():
    assert a_waybill(route=("IMT", "PWR", "ESC")).tenths == 286 + 336


def test_a_waybill_knows_what_it_passes():
    bill = a_waybill()
    assert bill.passes("HRM") is True
    assert bill.passes("GRB") is False


def test_a_diversion_cuts_the_route_and_adds_the_new_place():
    diverted = a_waybill().divert("HRM", "GRB", number=41208)
    assert diverted.route == ("PWR", "HRM", "GRB")
    assert diverted.number == 41208


def test_a_diversion_keeps_the_lading_and_the_car():
    diverted = a_waybill().divert("HRM", "GRB", number=41208)
    assert diverted.lading == GRAIN
    assert diverted.car == a_waybill().car


def test_a_diversion_changes_the_mileage():
    assert a_waybill().divert("HRM", "GRB", number=41208).tenths == 65 + 818


def test_a_diversion_at_a_place_not_on_the_route_is_refused():
    with pytest.raises(WaybillError):
        a_waybill().divert("GRB", "MRN", number=41208)


def test_a_diversion_at_the_origin_is_refused():
    with pytest.raises(WaybillError):
        a_waybill().divert("PWR", "GRB", number=41208)


def test_a_diversion_to_where_the_car_already_is_is_refused():
    with pytest.raises(WaybillError):
        a_waybill().divert("HRM", "HRM", number=41208)


def test_two_waybills_with_the_same_details_compare_equal():
    assert a_waybill() == a_waybill()
