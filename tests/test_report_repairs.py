from datetime import date

from drawbarline.marks import CarMark, ReportingMark
from drawbarline.money import Money
from drawbarline.repairs.card import Defect, RepairCard, price
from drawbarline.repairs.codes import JobCode, Responsibility, WhyMade
from drawbarline.repairs.objection import Ground, Objection
from drawbarline.repairs.pricing import (
    Depreciation,
    LaborRate,
    LaborRates,
    PriceLine,
    PriceMaster,
)
from drawbarline.reports.repairs import (
    billing_report,
    by_road,
    exception_report,
    responsibility_split,
)

WHEELSET = JobCode("4312")
COUPLER = JobCode("5104")
CN = ReportingMark("CN")
ELS = ReportingMark("ELS")


def a_master():
    master = PriceMaster()
    master.add(PriceLine(WHEELSET, date(2024, 7, 1), 250, 128000))
    master.add(PriceLine(COUPLER, date(2024, 7, 1), 125, 84000))
    master.depreciate_family(Depreciation("43", 4, 20))
    return master


def some_rates():
    rates = LaborRates()
    rates.file(LaborRate(CN, date(2024, 7, 1), 4200))
    rates.file(LaborRate(ELS, date(2024, 7, 1), 3900))
    return rates


def a_priced(number=88104, road=CN, why="01"):
    card = RepairCard(
        number=number,
        car=CarMark.parse("DBL 104207"),
        performed_by=road,
        on=date(2025, 1, 14),
        station="ESC",
        defects=(Defect(WHEELSET, WhyMade(why)), Defect(COUPLER, WhyMade("03"))),
    )
    return price(card, a_master(), some_rates())


def some_cards():
    return [a_priced(), a_priced(88105, road=ELS), a_priced(88106)]


def test_cards_gather_by_the_road_that_wrote_them():
    found = by_road(some_cards())
    assert [str(one.road) for one in found] == ["CN", "ELS"]
    assert found[0].cards == 2


def test_the_lines_are_counted():
    assert by_road(some_cards())[0].lines == 4


def test_the_split_between_the_owner_and_the_road():
    found = by_road([a_priced()])[0]
    assert found.to_owner == Money(138500)
    assert found.borne_by_the_road == Money(89250)


def test_the_two_halves_add_to_what_was_billed():
    found = by_road(some_cards())[0]
    assert found.to_owner + found.borne_by_the_road == found.billed


def test_no_cards_gather_to_nothing():
    assert by_road([]) == []


def test_the_billing_report_opens_with_a_heading():
    lines = billing_report(some_cards(), "2025-01")
    assert lines[0] == "REPAIR BILLING"


def test_the_billing_report_has_a_line_a_road():
    lines = billing_report(some_cards(), "2025-01")
    assert any(line.startswith("CN") for line in lines)
    assert any(line.startswith("ELS") for line in lines)


def test_the_billing_report_totals_every_road():
    lines = billing_report(some_cards(), "2025-01")
    assert any(line.startswith("2 roads") for line in lines)


def test_a_month_with_no_cards_still_totals():
    lines = billing_report([], "2025-01")
    assert any(line.startswith("0 roads") for line in lines)


def test_the_responsibility_split_covers_every_party():
    found = responsibility_split(some_cards())
    assert set(found) == set(Responsibility)


def test_wear_lands_on_the_owner_in_the_split():
    found = responsibility_split([a_priced()])
    assert found[Responsibility.OWNER] == Money(138500)
    assert found[Responsibility.HANDLING_LINE] == Money(89250)


def test_a_card_of_lading_damage_lands_on_a_third_party():
    found = responsibility_split([a_priced(why="04")])
    assert found[Responsibility.THIRD_PARTY] == Money(138500)


def test_the_exception_report_lists_what_is_open():
    objections = [Objection(88104, 0, Ground.DEPRECIATION, Money(20000), date(2025, 2, 3))]
    cards = {88104: a_priced()}
    lines = exception_report(objections, cards, date(2025, 3, 1))
    assert lines[0] == "OPEN REPAIR EXCEPTIONS"
    assert any(line.startswith("88104") for line in lines)


def test_the_exception_report_counts_the_days_left_to_answer():
    objections = [Objection(88104, 0, Ground.DEPRECIATION, Money(20000), date(2025, 2, 3))]
    lines = exception_report(objections, {88104: a_priced()}, date(2025, 3, 1))
    row = [line for line in lines if line.startswith("88104")][0]
    assert row.split()[-1] == "44"


def test_the_exception_report_totals_what_is_disputed():
    objections = [
        Objection(88104, 0, Ground.DEPRECIATION, Money(20000), date(2025, 2, 3)),
        Objection(88105, 1, Ground.PRICE, Money(5000), date(2025, 2, 3)),
    ]
    lines = exception_report(objections, {}, date(2025, 3, 1))
    total = [line for line in lines if line.startswith("2 open")][0]
    assert "$250.00" in total


def test_nothing_open_reports_nothing():
    lines = exception_report([], {}, date(2025, 3, 1))
    assert any(line.startswith("0 open") for line in lines)


def test_no_line_runs_past_the_paper():
    for line in billing_report(some_cards(), "2025-01"):
        assert len(line) <= 80
