from datetime import date

import pytest

from drawbarline.errors import BillingError
from drawbarline.marks import ReportingMark
from drawbarline.money import Money
from drawbarline.repairs.codes import JobCode
from drawbarline.repairs.pricing import (
    Depreciation,
    LaborRate,
    LaborRates,
    PriceLine,
    PriceMaster,
)

WHEELSET = JobCode("4312")
COUPLER = JobCode("5104")
CN = ReportingMark("CN")


def a_master():
    master = PriceMaster()
    master.add(PriceLine(WHEELSET, date(2024, 7, 1), 250, 128000))
    master.add(PriceLine(WHEELSET, date(2025, 7, 1), 250, 139000))
    master.add(PriceLine(COUPLER, date(2024, 7, 1), 125, 84000))
    master.depreciate_family(Depreciation("43", 4, 20))
    return master


def test_a_price_line_prints_its_hours_and_material():
    line = PriceLine(WHEELSET, date(2024, 7, 1), 250, 128000)
    assert str(line) == "4312 2.50 hours and $1,280.00"


def test_negative_labor_is_refused():
    with pytest.raises(BillingError):
        PriceLine(WHEELSET, date(2024, 7, 1), -1, 100)


def test_negative_material_is_refused():
    with pytest.raises(BillingError):
        PriceLine(WHEELSET, date(2024, 7, 1), 100, -1)


def test_the_master_hands_back_the_price_in_force():
    master = a_master()
    assert master.line_for(WHEELSET, date(2025, 1, 14)).material == Money(128000)
    assert master.line_for(WHEELSET, date(2025, 7, 1)).material == Money(139000)


def test_a_job_with_no_price_yet_is_refused():
    with pytest.raises(BillingError):
        a_master().line_for(WHEELSET, date(2024, 1, 1))


def test_a_job_nobody_priced_is_refused():
    with pytest.raises(BillingError):
        a_master().line_for(JobCode("9999"), date(2025, 1, 14))


def test_pricing_the_same_job_twice_from_one_date_is_refused():
    master = a_master()
    with pytest.raises(BillingError):
        master.add(PriceLine(WHEELSET, date(2024, 7, 1), 250, 1))


def test_the_master_counts_its_lines():
    assert len(a_master()) == 3


def test_a_new_part_is_paid_in_full():
    assert Depreciation("43", 4, 20).payable_percent(0) == 100


def test_a_part_loses_its_percentage_a_year():
    assert Depreciation("43", 4, 20).payable_percent(5) == 80


def test_depreciation_stops_at_the_floor():
    assert Depreciation("43", 4, 20).payable_percent(40) == 20


def test_a_very_old_part_never_goes_below_the_floor():
    assert Depreciation("43", 4, 20).payable_percent(99) == 20


def test_a_negative_age_is_refused():
    with pytest.raises(BillingError):
        Depreciation("43", 4, 20).payable_percent(-1)


def test_a_percentage_over_a_hundred_is_refused():
    with pytest.raises(BillingError):
        Depreciation("43", 140, 20)


def test_a_floor_over_a_hundred_is_refused():
    with pytest.raises(BillingError):
        Depreciation("43", 4, 140)


def test_depreciation_comes_off_the_material():
    assert Depreciation("43", 4, 20).applied(Money(128000), 5) == Money(102400)


def test_a_family_with_no_rule_is_paid_in_full():
    assert a_master().material_for(COUPLER, date(2025, 1, 14), 20) == Money(84000)


def test_a_family_with_a_rule_is_depreciated():
    assert a_master().material_for(WHEELSET, date(2025, 1, 14), 5) == Money(102400)


def test_depreciation_follows_the_price_in_force():
    assert a_master().material_for(WHEELSET, date(2025, 7, 1), 5) == Money(111200)


def test_the_rule_for_a_job_reads_back_off_its_family():
    assert a_master().depreciation_for(WHEELSET).family == "43"
    assert a_master().depreciation_for(COUPLER) is None


def test_a_labor_rate_prices_the_hours():
    rate = LaborRate(CN, date(2024, 7, 1), 4200)
    assert rate.cost_of(250) == Money(10500)


def test_a_part_hour_is_priced_pro_rata():
    rate = LaborRate(CN, date(2024, 7, 1), 4200)
    assert rate.cost_of(50) == Money(2100)


def test_a_negative_labor_rate_is_refused():
    with pytest.raises(BillingError):
        LaborRate(CN, date(2024, 7, 1), -1)


def test_the_book_hands_back_the_rate_in_force():
    rates = LaborRates()
    rates.file(LaborRate(CN, date(2024, 7, 1), 4200))
    rates.file(LaborRate(CN, date(2025, 7, 1), 4550))
    assert rates.rate_for(CN, date(2025, 1, 14)).cents_an_hour == 4200
    assert rates.rate_for(CN, date(2025, 7, 1)).cents_an_hour == 4550


def test_a_road_with_no_rate_filed_is_refused():
    with pytest.raises(BillingError):
        LaborRates().rate_for(CN, date(2025, 1, 14))


def test_a_road_filing_twice_on_one_day_is_refused():
    rates = LaborRates()
    rates.file(LaborRate(CN, date(2024, 7, 1), 4200))
    with pytest.raises(BillingError):
        rates.file(LaborRate(CN, date(2024, 7, 1), 4300))


def test_the_book_lists_the_roads_that_have_filed():
    rates = LaborRates()
    rates.file(LaborRate(CN, date(2024, 7, 1), 4200))
    rates.file(LaborRate(ReportingMark("ELS"), date(2024, 7, 1), 3900))
    assert [str(road) for road in rates.roads()] == ["CN", "ELS"]
