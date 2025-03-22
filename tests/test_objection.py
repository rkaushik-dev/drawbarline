from datetime import date

import pytest

from drawbarline.errors import BillingError
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.money import Money
from drawbarline.repairs.card import Defect, RepairCard, price
from drawbarline.repairs.codes import JobCode, WhyMade
from drawbarline.repairs.objection import (
    ExceptionRegister,
    Ground,
    Objection,
    Outcome,
    must_file_by,
)
from drawbarline.repairs.pricing import LaborRate, LaborRates, PriceLine, PriceMaster

WHEELSET = JobCode("4312")
COUPLER = JobCode("5104")
CN = ReportingMark("CN")
DAY = date(2025, 1, 14)


def a_priced_card():
    master = PriceMaster()
    master.add(PriceLine(WHEELSET, date(2024, 7, 1), 250, 128000))
    master.add(PriceLine(COUPLER, date(2024, 7, 1), 125, 84000))
    rates = LaborRates()
    rates.file(LaborRate(CN, date(2024, 7, 1), 4200))
    card = RepairCard(
        number=88104,
        car=CarMark.parse("DBL 104207"),
        performed_by=CN,
        on=DAY,
        station="ESC",
        defects=(Defect(WHEELSET, WhyMade("01")), Defect(COUPLER, WhyMade("01"))),
    )
    return price(card, master, rates)


def an_objection(**changed):
    fields = {
        "card_number": 88104,
        "line": 0,
        "ground": Ground.DEPRECIATION,
        "disputed": Money(20000),
        "filed_on": date(2025, 2, 3),
    }
    fields.update(changed)
    return Objection(**fields)


def test_the_filing_clock_runs_ninety_days():
    assert must_file_by(DAY) == date(2025, 4, 14)


def test_an_objection_prints_the_card_line_and_ground():
    assert str(an_objection()) == ("card 88104 line 0 depreciation not allowed for $200.00")


def test_an_objection_disputing_nothing_is_refused():
    with pytest.raises(BillingError):
        an_objection(disputed=Money(0))


def test_an_objection_on_a_negative_line_is_refused():
    with pytest.raises(BillingError):
        an_objection(line=-1)


def test_every_ground_has_readable_text():
    assert all(ground.text for ground in Ground)


def test_an_objection_in_time_is_filed():
    register = ExceptionRegister()
    register.file(a_priced_card(), an_objection())
    assert len(register) == 1


def test_an_objection_on_the_last_day_is_in_time():
    register = ExceptionRegister()
    register.file(a_priced_card(), an_objection(filed_on=date(2025, 4, 14)))
    assert len(register) == 1


def test_an_objection_out_of_time_is_refused():
    register = ExceptionRegister()
    with pytest.raises(BillingError):
        register.file(a_priced_card(), an_objection(filed_on=date(2025, 4, 15)))


def test_an_objection_against_another_card_is_refused():
    register = ExceptionRegister()
    with pytest.raises(BillingError):
        register.file(a_priced_card(), an_objection(card_number=99999))


def test_an_objection_against_a_line_the_card_has_not_got_is_refused():
    register = ExceptionRegister()
    with pytest.raises(BillingError):
        register.file(a_priced_card(), an_objection(line=7))


def test_disputing_more_than_the_line_was_billed_is_refused():
    register = ExceptionRegister()
    with pytest.raises(BillingError):
        register.file(a_priced_card(), an_objection(disputed=Money(500000)))


def test_the_same_line_twice_is_refused():
    register = ExceptionRegister()
    card = a_priced_card()
    register.file(card, an_objection())
    with pytest.raises(BillingError):
        register.file(card, an_objection(ground=Ground.PRICE))


def test_a_second_line_of_the_same_card_is_allowed():
    register = ExceptionRegister()
    card = a_priced_card()
    register.file(card, an_objection())
    register.file(card, an_objection(line=1, disputed=Money(1000)))
    assert len(register) == 2


def test_an_objection_starts_open():
    register = ExceptionRegister()
    register.file(a_priced_card(), an_objection())
    assert len(register.open_objections()) == 1


def test_allowing_the_whole_amount_closes_it():
    register = ExceptionRegister()
    objection = register.file(a_priced_card(), an_objection())
    found = register.settle(objection, Money(20000))
    assert found.outcome is Outcome.ALLOWED
    assert found.still_owed == Money(0)
    assert register.open_objections() == []


def test_allowing_part_of_it_says_so():
    register = ExceptionRegister()
    objection = register.file(a_priced_card(), an_objection())
    found = register.settle(objection, Money(8000))
    assert found.outcome is Outcome.PART_ALLOWED
    assert found.still_owed == Money(12000)


def test_allowing_nothing_declines_it():
    register = ExceptionRegister()
    objection = register.file(a_priced_card(), an_objection())
    assert register.settle(objection, Money(0)).outcome is Outcome.DECLINED


def test_allowing_more_than_was_disputed_is_refused():
    register = ExceptionRegister()
    objection = register.file(a_priced_card(), an_objection())
    with pytest.raises(BillingError):
        register.settle(objection, Money(30000))


def test_settling_something_never_filed_is_refused():
    with pytest.raises(BillingError):
        ExceptionRegister().settle(an_objection(), Money(100))


def test_settling_twice_is_refused():
    register = ExceptionRegister()
    objection = register.file(a_priced_card(), an_objection())
    register.settle(objection, Money(8000))
    with pytest.raises(BillingError):
        register.settle(objection, Money(9000))


def test_referring_to_arbitration_closes_our_file():
    register = ExceptionRegister()
    objection = register.file(a_priced_card(), an_objection())
    found = register.refer(objection)
    assert found.outcome is Outcome.ARBITRATION
    assert register.open_objections() == []


def test_referring_something_never_filed_is_refused():
    with pytest.raises(BillingError):
        ExceptionRegister().refer(an_objection())


def test_the_allowances_against_a_card_add_up():
    register = ExceptionRegister()
    card = a_priced_card()
    first = register.file(card, an_objection())
    second = register.file(card, an_objection(line=1, disputed=Money(1000)))
    register.settle(first, Money(8000))
    register.settle(second, Money(1000))
    assert register.allowed_against(88104) == Money(9000)


def test_a_card_with_nothing_allowed_shows_nothing():
    assert ExceptionRegister().allowed_against(88104) == Money(0)


def test_resolutions_come_back_in_card_and_line_order():
    register = ExceptionRegister()
    card = a_priced_card()
    second = register.file(card, an_objection(line=1, disputed=Money(1000)))
    first = register.file(card, an_objection())
    register.settle(second, Money(500))
    register.settle(first, Money(500))
    assert [one.objection.line for one in register.resolutions()] == [0, 1]


def test_a_resolution_prints_its_outcome():
    register = ExceptionRegister()
    objection = register.file(a_priced_card(), an_objection())
    assert str(register.settle(objection, Money(20000))).endswith("allowed in full $200.00")
