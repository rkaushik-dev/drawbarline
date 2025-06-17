from datetime import date

from drawbarline.demurrage.counting import Detention
from drawbarline.demurrage.tariff import Step, Tariff
from drawbarline.marks import CarMark
from drawbarline.money import Money
from drawbarline.reports.demurrage import (
    by_shipper,
    detention_report,
    free_and_charged,
    shipper_report,
)
from support import a_customer_register

TARIFF = Tariff("4200", date(2024, 7, 1), 2, 1, (Step(2, 10000), Step(0, 20000)))


def a_detention(number, chargeable=3, industry="ESCPAP1"):
    return Detention(
        car=CarMark.parse(f"CN {number}"),
        industry=industry,
        placed_on=date(2025, 1, 14),
        clock_starts=date(2025, 1, 15),
        free_until=date(2025, 1, 16),
        counted_to=date(2025, 1, 22),
        chargeable=chargeable,
        loaded=True,
        constructive=False,
    )


def some():
    return [
        a_detention(1),
        a_detention(2, chargeable=0),
        a_detention(3, chargeable=1, industry="PWRGRN1"),
    ]


def test_the_detention_report_opens_with_a_heading():
    lines = detention_report(some(), TARIFF, "2025-01")
    assert lines[0] == "DEMURRAGE DETENTION"
    assert lines[1] == "2025-01"


def test_every_car_is_on_the_detention_report():
    lines = detention_report(some(), TARIFF, "2025-01")
    assert len([line for line in lines if line.startswith("CN 0000")]) == 3


def test_a_free_car_shows_no_charge():
    lines = detention_report(some(), TARIFF, "2025-01")
    row = [line for line in lines if line.startswith("CN 000002")][0]
    assert "$0.00" in row


def test_a_charged_car_shows_the_tariff_amount():
    lines = detention_report(some(), TARIFF, "2025-01")
    row = [line for line in lines if line.startswith("CN 000001")][0]
    assert "$400.00" in row


def test_the_detention_report_totals_cars_and_days():
    lines = detention_report(some(), TARIFF, "2025-01")
    total = [line for line in lines if line.startswith("3 cars")][0]
    assert "2 charged" in total
    assert "$500.00" in total


def test_the_lines_come_back_by_track_then_car():
    lines = detention_report(some(), TARIFF, "2025-01")
    cars = [line.split("  ")[0] for line in lines if line.startswith("CN 0000")]
    assert cars == ["CN 000001", "CN 000002", "CN 000003"]


def test_an_empty_month_still_totals():
    lines = detention_report([], TARIFF, "2025-01")
    assert any(line.startswith("0 cars") for line in lines)


def test_the_shipper_summary_gathers_by_customer():
    found = by_shipper(some(), TARIFF, a_customer_register())
    assert found["ESCPAP"][0] == 2
    assert found["PWRGRN"][0] == 1


def test_the_shipper_summary_adds_the_days_and_the_money():
    found = by_shipper(some(), TARIFF, a_customer_register())
    assert found["ESCPAP"][1] == 3
    assert found["ESCPAP"][2] == Money(40000)


def test_the_shipper_report_names_the_customer():
    lines = shipper_report(some(), TARIFF, a_customer_register(), "2025-01")
    assert any("Escanaba Paper" in line for line in lines)


def test_the_shipper_report_puts_the_biggest_bill_first():
    lines = shipper_report(some(), TARIFF, a_customer_register(), "2025-01")
    rows = [line for line in lines if line.startswith(("ESCPAP", "PWRGRN"))]
    assert rows[0].startswith("ESCPAP")


def test_the_shipper_report_totals_every_customer():
    lines = shipper_report(some(), TARIFF, a_customer_register(), "2025-01")
    total = [line for line in lines if line.startswith("2 shippers")][0]
    assert "$500.00" in total


def test_a_month_with_no_detentions_reports_no_shippers():
    lines = shipper_report([], TARIFF, a_customer_register(), "2025-01")
    assert any(line.startswith("0 shippers") for line in lines)


def test_the_free_and_charged_split():
    assert free_and_charged(some()) == (1, 2)


def test_nothing_at_all_splits_to_nothing():
    assert free_and_charged([]) == (0, 0)


def test_no_line_runs_past_the_paper():
    for line in detention_report(some(), TARIFF, "2025-01"):
        assert len(line) <= 80
