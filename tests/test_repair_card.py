from datetime import date

import pytest

from drawbarline.errors import BillingError
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.mileage import MileageError
from drawbarline.money import Money
from drawbarline.repairs.card import Defect, RepairCard, price
from drawbarline.repairs.codes import JobCode, Responsibility, WhyMade
from drawbarline.repairs.pricing import (
    Depreciation,
    LaborRate,
    LaborRates,
    PriceLine,
    PriceMaster,
)

WHEELSET = JobCode("4312")
COUPLER = JobCode("5104")
CAR = CarMark.parse("DBL 104207")
CN = ReportingMark("CN")
DAY = date(2025, 1, 14)


def a_master():
    master = PriceMaster()
    master.add(PriceLine(WHEELSET, date(2024, 7, 1), 250, 128000))
    master.add(PriceLine(COUPLER, date(2024, 7, 1), 125, 84000))
    master.depreciate_family(Depreciation("43", 4, 20))
    return master


def some_rates():
    rates = LaborRates()
    rates.file(LaborRate(CN, date(2024, 7, 1), 4200))
    return rates


def a_card(defects=None, **changed):
    if defects is None:
        defects = [Defect(WHEELSET, WhyMade("01"))]
    fields = {
        "number": 88104,
        "car": CAR,
        "performed_by": CN,
        "on": DAY,
        "station": "ESC",
        "defects": tuple(defects),
    }
    fields.update(changed)
    return RepairCard(**fields)


def test_a_card_prints_its_number_car_road_and_date():
    assert str(a_card()) == "card 88104 DBL 104207 by CN on 2025-01-14"


def test_a_defect_prints_the_codes_and_who_answers():
    defect = Defect(WHEELSET, WhyMade("01"))
    assert str(defect) == "4312 why 01 x1 to owner"


def test_a_defect_of_no_parts_is_refused():
    with pytest.raises(BillingError):
        Defect(WHEELSET, WhyMade("01"), quantity=0)


def test_a_part_of_negative_age_is_refused():
    with pytest.raises(BillingError):
        Defect(WHEELSET, WhyMade("01"), part_years=-1)


def test_responsibility_follows_the_why_made_code():
    assert Defect(WHEELSET, WhyMade("03")).answered_by is Responsibility.HANDLING_LINE


def test_an_override_needs_a_reason():
    with pytest.raises(BillingError):
        Defect(WHEELSET, WhyMade("01"), overridden=Responsibility.HANDLING_LINE)


def test_an_override_with_a_reason_stands():
    defect = Defect(
        WHEELSET,
        WhyMade("01"),
        overridden=Responsibility.HANDLING_LINE,
        note="wheel was flat from a hand brake left applied",
    )
    assert defect.answered_by is Responsibility.HANDLING_LINE


def test_a_card_numbered_below_one_is_refused():
    with pytest.raises(BillingError):
        a_card(number=0)


def test_a_card_with_no_repairs_is_refused():
    with pytest.raises(BillingError):
        a_card(defects=[])


def test_a_card_written_at_a_place_we_do_not_serve_is_refused():
    with pytest.raises(MileageError):
        a_card(station="CHI")


def test_a_card_lists_the_jobs_on_it():
    card = a_card([Defect(WHEELSET, WhyMade("01")), Defect(COUPLER, WhyMade("03"))])
    assert card.jobs == [WHEELSET, COUPLER]


def test_labor_is_the_hours_at_the_road_rate():
    priced = price(a_card(), a_master(), some_rates())
    assert priced.lines[0].labor == Money(10500)


def test_material_on_a_new_part_is_the_full_price():
    priced = price(a_card(), a_master(), some_rates())
    assert priced.lines[0].material == Money(128000)


def test_material_on_an_old_part_is_depreciated():
    card = a_card([Defect(WHEELSET, WhyMade("01"), part_years=5)])
    priced = price(card, a_master(), some_rates())
    assert priced.lines[0].material == Money(102400)


def test_a_quantity_multiplies_both_halves():
    card = a_card([Defect(WHEELSET, WhyMade("01"), quantity=4)])
    priced = price(card, a_master(), some_rates())
    assert priced.lines[0].labor == Money(42000)
    assert priced.lines[0].material == Money(512000)


def test_a_line_totals_its_two_halves():
    priced = price(a_card(), a_master(), some_rates())
    assert priced.lines[0].amount == Money(10500 + 128000)


def test_a_card_totals_its_lines():
    card = a_card([Defect(WHEELSET, WhyMade("01")), Defect(COUPLER, WhyMade("01"))])
    priced = price(card, a_master(), some_rates())
    assert priced.total == Money(10500 + 128000 + 5250 + 84000)


def test_wear_goes_on_the_owner_bill():
    priced = price(a_card(), a_master(), some_rates())
    assert priced.billed_to_owner == priced.total
    assert priced.borne_by_the_road == Money(0)


def test_handling_damage_stays_with_the_road_that_did_it():
    card = a_card([Defect(COUPLER, WhyMade("03"))])
    priced = price(card, a_master(), some_rates())
    assert priced.billed_to_owner == Money(0)
    assert priced.borne_by_the_road == priced.total


def test_a_mixed_card_splits_between_the_two():
    card = a_card([Defect(WHEELSET, WhyMade("01")), Defect(COUPLER, WhyMade("03"))])
    priced = price(card, a_master(), some_rates())
    assert priced.billed_to_owner == Money(138500)
    assert priced.borne_by_the_road == Money(89250)


def test_joint_work_is_billed_to_the_owner():
    card = a_card([Defect(COUPLER, WhyMade("14"))])
    priced = price(card, a_master(), some_rates())
    assert priced.billed_to_owner == priced.total


def test_a_card_reports_what_each_party_owes():
    card = a_card([Defect(WHEELSET, WhyMade("01")), Defect(COUPLER, WhyMade("04"))])
    priced = price(card, a_master(), some_rates())
    assert priced.owed_by(Responsibility.THIRD_PARTY) == Money(89250)
    assert priced.owed_by(Responsibility.HANDLING_LINE) == Money(0)


def test_a_priced_card_prints_its_split():
    priced = price(a_card(), a_master(), some_rates())
    assert str(priced).endswith("$1,385.00 of which $1,385.00 to the owner")


def test_a_road_with_no_labor_rate_cannot_price_a_card():
    with pytest.raises(BillingError):
        price(a_card(performed_by=ReportingMark("ELS")), a_master(), some_rates())


def test_a_job_with_no_price_cannot_be_billed():
    card = a_card([Defect(JobCode("9999"), WhyMade("01"))])
    with pytest.raises(BillingError):
        price(card, a_master(), some_rates())
