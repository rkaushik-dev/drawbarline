from datetime import date

import pytest

from drawbarline.demurrage.tariff import Step, Tariff, TariffBook
from drawbarline.errors import TariffError
from drawbarline.money import Money

STEPS = (Step(2, 10000), Step(4, 15000), Step(0, 20000))


def a_tariff(**changed):
    fields = {
        "code": "4200",
        "effective": date(2024, 7, 1),
        "free_days_loaded": 2,
        "free_days_empty": 1,
        "steps": STEPS,
    }
    fields.update(changed)
    return Tariff(**fields)


def test_a_tariff_prints_its_number_and_date():
    assert str(a_tariff()) == "tariff 4200 from 2024-07-01"


def test_a_closed_step_prints_the_day_it_runs_through():
    assert str(Step(2, 10000)) == "$100.00 through day 2"


def test_the_last_step_prints_as_running_on():
    assert str(Step(0, 20000)) == "$200.00 and thereafter"


def test_a_step_that_pays_the_customer_is_refused():
    with pytest.raises(TariffError):
        Step(2, -1)


def test_a_step_through_a_negative_day_is_refused():
    with pytest.raises(TariffError):
        Step(-1, 10000)


def test_free_time_differs_between_loads_and_empties():
    tariff = a_tariff()
    assert tariff.free_days(loaded=True) == 2
    assert tariff.free_days(loaded=False) == 1


def test_negative_free_time_is_refused():
    with pytest.raises(TariffError):
        a_tariff(free_days_loaded=-1)


def test_a_tariff_with_no_steps_is_refused():
    with pytest.raises(TariffError):
        a_tariff(steps=())


def test_a_tariff_whose_last_step_ends_is_refused():
    with pytest.raises(TariffError):
        a_tariff(steps=(Step(2, 10000), Step(4, 15000)))


def test_an_open_step_before_the_end_is_refused():
    with pytest.raises(TariffError):
        a_tariff(steps=(Step(0, 10000), Step(4, 15000), Step(0, 20000)))


def test_steps_that_do_not_climb_are_refused():
    with pytest.raises(TariffError):
        a_tariff(steps=(Step(4, 10000), Step(2, 15000), Step(0, 20000)))


def test_a_tariff_with_no_number_is_refused():
    with pytest.raises(TariffError):
        a_tariff(code="  ")


def test_the_first_days_take_the_first_step():
    tariff = a_tariff()
    assert tariff.rate_on_day(1) == Money(10000)
    assert tariff.rate_on_day(2) == Money(10000)


def test_the_middle_days_take_the_second_step():
    tariff = a_tariff()
    assert tariff.rate_on_day(3) == Money(15000)
    assert tariff.rate_on_day(4) == Money(15000)


def test_everything_after_takes_the_last_step():
    tariff = a_tariff()
    assert tariff.rate_on_day(5) == Money(20000)
    assert tariff.rate_on_day(50) == Money(20000)


def test_a_day_numbered_below_one_is_refused():
    with pytest.raises(TariffError):
        a_tariff().rate_on_day(0)


def test_no_chargeable_days_cost_nothing():
    assert a_tariff().charge(0) == Money(0)


def test_one_chargeable_day_costs_the_first_step():
    assert a_tariff().charge(1) == Money(10000)


def test_the_charge_adds_the_steps_as_it_climbs():
    assert a_tariff().charge(5) == Money(10000 + 10000 + 15000 + 15000 + 20000)


def test_a_long_hold_keeps_climbing_at_the_last_step():
    assert a_tariff().charge(6) == a_tariff().charge(5) + Money(20000)


def test_a_negative_hold_is_refused():
    with pytest.raises(TariffError):
        a_tariff().charge(-1)


def test_a_flat_tariff_is_allowed():
    flat = a_tariff(steps=(Step(0, 5000),))
    assert flat.charge(3) == Money(15000)


def test_a_book_hands_back_the_tariff_in_force():
    book = TariffBook()
    book.publish(a_tariff())
    book.publish(a_tariff(code="4300", effective=date(2025, 7, 1)))
    assert book.in_effect_on(date(2025, 1, 14)).code == "4200"
    assert book.in_effect_on(date(2025, 7, 1)).code == "4300"


def test_a_book_with_nothing_in_force_yet_is_refused():
    book = TariffBook()
    book.publish(a_tariff())
    with pytest.raises(TariffError):
        book.in_effect_on(date(2024, 1, 1))


def test_two_tariffs_on_the_same_day_are_refused():
    book = TariffBook()
    book.publish(a_tariff())
    with pytest.raises(TariffError):
        book.publish(a_tariff(code="4300"))


def test_a_book_lists_the_dates_it_changes_on():
    book = TariffBook()
    book.publish(a_tariff(code="4300", effective=date(2025, 7, 1)))
    book.publish(a_tariff())
    assert book.effective_dates() == [date(2024, 7, 1), date(2025, 7, 1)]
    assert len(book) == 2
