import pytest

from drawbarline.carhire.liability import Charge, Liability
from drawbarline.clock import Interval, utc
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.money import Money
from drawbarline.reports.carhire import detail_report, liability_report, owed_to
from drawbarline.settlement.period import Period

JANUARY = Period(2025, 1)
CN = ReportingMark("CN")
ELS = ReportingMark("ELS")


def a_liability():
    found = Liability(JANUARY.window)
    window = Interval(utc(2025, 1, 6, 12), utc(2025, 1, 16, 12))
    found.add(Charge(CarMark.parse("CN 400100"), CN, window, Money(111120), Money(4066)))
    found.add(Charge(CarMark.parse("CN 400101"), CN, window, Money(111120), Money(0)))
    found.add(Charge(CarMark.parse("DBL 104207"), ELS, window, Money(55560), Money(1210)))
    return found


def test_the_summary_opens_with_a_heading():
    lines = liability_report(a_liability(), JANUARY)
    assert lines[0] == "CAR HIRE LIABILITY"
    assert lines[1] == "2025-01"


def test_the_summary_has_one_line_a_road():
    lines = liability_report(a_liability(), JANUARY)
    roads = [line for line in lines if line.startswith(("CN ", "ELS"))]
    assert len(roads) == 2


def test_the_summary_counts_the_cars_on_each_road():
    lines = liability_report(a_liability(), JANUARY)
    cn = [line for line in lines if line.startswith("CN")][0]
    assert " 2 " in cn


def test_the_summary_keeps_the_two_halves_apart():
    lines = liability_report(a_liability(), JANUARY)
    cn = [line for line in lines if line.startswith("CN")][0]
    assert "$2,222.40" in cn
    assert "$40.66" in cn


def test_the_summary_totals_every_road():
    lines = liability_report(a_liability(), JANUARY)
    total = [line for line in lines if line.startswith("all roads")][0]
    assert "$2,830.76" in total


def test_the_total_counts_every_car():
    lines = liability_report(a_liability(), JANUARY)
    total = [line for line in lines if line.startswith("all roads")][0]
    assert " 3 " in total


def test_an_empty_liability_still_prints_a_total():
    lines = liability_report(Liability(JANUARY.window), JANUARY)
    assert any(line.startswith("all roads") for line in lines)


def test_the_detail_names_the_road():
    lines = detail_report(a_liability(), CN, JANUARY)
    assert lines[0] == "CAR HIRE DETAIL CN"


def test_the_detail_has_one_line_a_car():
    lines = detail_report(a_liability(), CN, JANUARY)
    cars = [line for line in lines if line.startswith("CN 4001")]
    assert len(cars) == 2


def test_the_detail_prints_the_hours():
    lines = detail_report(a_liability(), CN, JANUARY)
    assert any("240.00" in line for line in lines)


def test_the_detail_prints_the_window_ends():
    lines = detail_report(a_liability(), CN, JANUARY)
    assert any("01-06 12:00" in line for line in lines)


def test_the_detail_totals_the_charges_it_shows():
    lines = detail_report(a_liability(), CN, JANUARY)
    total = [line for line in lines if line.startswith("2 charges")][0]
    assert "$2,222.40" in total


def test_the_detail_leaves_out_other_roads():
    lines = detail_report(a_liability(), CN, JANUARY)
    assert not any("DBL 104207" in line for line in lines)


def test_a_road_with_nothing_owed_prints_an_empty_detail():
    lines = detail_report(Liability(JANUARY.window), CN, JANUARY)
    assert any(line.startswith("0 charges") for line in lines)


def test_the_summary_is_available_as_data():
    assert owed_to(a_liability()) == {
        "CN": Money(226306),
        "ELS": Money(56770),
    }


def test_the_data_and_the_report_agree():
    found = a_liability()
    lines = liability_report(found, JANUARY)
    for road, amount in owed_to(found).items():
        row = [line for line in lines if line.startswith(road)][0]
        assert str(amount) in row


@pytest.mark.parametrize("width", [len(line) for line in liability_report(a_liability(), JANUARY)])
def test_no_line_runs_past_the_paper(width):
    assert width <= 80
