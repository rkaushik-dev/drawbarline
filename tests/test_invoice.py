from datetime import date

import pytest

from drawbarline.demurrage.average import Settlement
from drawbarline.demurrage.counting import Detention
from drawbarline.demurrage.invoice import Invoice, Line, assemble, assemble_average
from drawbarline.demurrage.tariff import Step, Tariff
from drawbarline.errors import TariffError
from drawbarline.marks import CarMark
from drawbarline.money import Money

TARIFF = Tariff("4200", date(2024, 7, 1), 2, 1, (Step(2, 10000), Step(0, 20000)))
JANUARY = date(2025, 1, 1)


def a_detention(number, chargeable=3):
    return Detention(
        car=CarMark.parse(f"CN {number}"),
        industry="ESCPAP1",
        placed_on=date(2025, 1, 14),
        clock_starts=date(2025, 1, 15),
        free_until=date(2025, 1, 16),
        counted_to=date(2025, 1, 22),
        chargeable=chargeable,
        loaded=True,
        constructive=False,
    )


def an_invoice(detentions=None):
    return assemble(4101, "ESCPAP", JANUARY, detentions or [a_detention(1)], TARIFF)


def test_an_invoice_prints_its_number_shipper_month_and_total():
    assert str(an_invoice()) == "demurrage invoice 4101 ESCPAP 2025-01 $400.00"


def test_a_line_prints_the_car_the_days_and_the_money():
    line = an_invoice().lines[0]
    assert str(line) == "CN 000001 ESCPAP1 3 days $400.00"


def test_an_invoice_numbered_below_one_is_refused():
    with pytest.raises(TariffError):
        Invoice(0, "ESCPAP", JANUARY, ())


def test_an_invoice_with_no_shipper_is_refused():
    with pytest.raises(TariffError):
        Invoice(1, "  ", JANUARY, ())


def test_an_invoice_that_does_not_start_a_month_is_refused():
    with pytest.raises(TariffError):
        Invoice(1, "ESCPAP", date(2025, 1, 15), ())


def test_the_total_adds_the_lines():
    invoice = an_invoice([a_detention(1), a_detention(2, chargeable=1)])
    assert invoice.total == Money(40000 + 10000)


def test_the_days_add_up_too():
    invoice = an_invoice([a_detention(1), a_detention(2, chargeable=1)])
    assert invoice.days == 4


def test_a_car_inside_free_time_is_on_the_invoice_and_not_charged():
    invoice = an_invoice([a_detention(1), a_detention(2, chargeable=0)])
    assert invoice.cars == 2
    assert invoice.charged_cars == 1
    assert invoice.free_cars == 1


def test_a_free_line_carries_no_money():
    invoice = an_invoice([a_detention(2, chargeable=0)])
    assert invoice.lines[0].charged is False
    assert invoice.total == Money(0)


def test_an_invoice_of_nothing_but_free_cars_is_empty():
    assert an_invoice([a_detention(2, chargeable=0)]).empty is True


def test_an_invoice_with_a_charge_is_not_empty():
    assert an_invoice().empty is False


def test_lines_come_back_in_car_order():
    invoice = an_invoice([a_detention(9), a_detention(2), a_detention(5)])
    assert [str(line.car) for line in invoice.lines] == [
        "CN 000002",
        "CN 000005",
        "CN 000009",
    ]


def test_a_line_can_be_found_by_car():
    invoice = an_invoice([a_detention(1), a_detention(2)])
    assert invoice.for_car(CarMark.parse("CN 2")).days == 3


def test_a_car_not_on_the_invoice_has_no_line():
    assert an_invoice().for_car(CarMark.parse("WC 1")) is None


def test_a_line_carries_the_dates_the_detention_was_counted_over():
    line = an_invoice().lines[0]
    assert line.placed_on == date(2025, 1, 14)
    assert line.counted_to == date(2025, 1, 22)


def test_an_invoice_with_no_cars_totals_nothing():
    assert assemble(4101, "ESCPAP", JANUARY, [], TARIFF).total == Money(0)


def test_an_average_invoice_carries_one_line():
    settled = Settlement("PWRGRN", 2, 5, Money(36000))
    invoice = assemble_average(4102, settled, JANUARY)
    assert invoice.cars == 1
    assert invoice.average is True


def test_an_average_invoice_shows_the_net_debits_as_its_days():
    settled = Settlement("PWRGRN", 2, 5, Money(36000))
    assert assemble_average(4102, settled, JANUARY).days == 3


def test_an_average_invoice_names_no_car():
    settled = Settlement("PWRGRN", 2, 5, Money(36000))
    assert assemble_average(4102, settled, JANUARY).lines[0].car is None


def test_an_average_line_prints_without_a_car():
    settled = Settlement("PWRGRN", 2, 5, Money(36000))
    line = assemble_average(4102, settled, JANUARY).lines[0]
    assert str(line) == "all cars average agreement 3 days $360.00"


def test_an_average_invoice_says_so_when_it_prints():
    settled = Settlement("PWRGRN", 2, 5, Money(36000))
    assert str(assemble_average(4102, settled, JANUARY)).startswith("average invoice")


def test_a_settled_month_with_nothing_owed_makes_an_empty_invoice():
    settled = Settlement("PWRGRN", 5, 2, Money(0))
    assert assemble_average(4102, settled, JANUARY).empty is True


def test_a_line_carries_the_amount_the_tariff_gives():
    assert Line(None, "x", JANUARY, JANUARY, 2, Money(500)).amount == Money(500)
