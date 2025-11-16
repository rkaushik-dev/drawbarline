from datetime import date

import pytest

from drawbarline.demurrage.counting import Detention
from drawbarline.demurrage.tariff import Step, Tariff
from drawbarline.errors import TariffError
from drawbarline.marks import CarMark
from drawbarline.money import Money
from drawbarline.storage import StorageTariff, charge, conversion_day, split

DEMURRAGE = Tariff("4200", date(2024, 7, 1), 2, 1, (Step(2, 10000), Step(0, 20000)))
STORAGE = StorageTariff("4500", date(2024, 7, 1), after_days=5, daily_cents=3500)


def a_detention(chargeable=3, counted_to=date(2025, 1, 22)):
    return Detention(
        car=CarMark.parse("CN 400100"),
        industry="ESCPAP1",
        placed_on=date(2025, 1, 14),
        clock_starts=date(2025, 1, 15),
        free_until=date(2025, 1, 16),
        counted_to=counted_to,
        chargeable=chargeable,
        loaded=True,
        constructive=False,
    )


def test_a_storage_tariff_prints_its_terms():
    assert str(STORAGE) == "storage 4500 at $35.00 a day after 5 days"


def test_a_storage_tariff_with_no_number_is_refused():
    with pytest.raises(TariffError):
        StorageTariff("  ", date(2024, 7, 1), 5, 3500)


def test_storage_starting_on_day_zero_is_refused():
    with pytest.raises(TariffError):
        StorageTariff("4500", date(2024, 7, 1), 0, 3500)


def test_a_storage_rate_that_pays_the_customer_is_refused():
    with pytest.raises(TariffError):
        StorageTariff("4500", date(2024, 7, 1), 5, -1)


def test_a_short_detention_never_converts():
    found = split(a_detention(chargeable=3), STORAGE)
    assert found.converted is False
    assert found.demurrage_days == 3
    assert found.storage_days == 0


def test_a_short_detention_has_no_conversion_day():
    assert conversion_day(a_detention(chargeable=3), STORAGE) is None


def test_a_detention_exactly_at_the_threshold_converts():
    found = split(a_detention(chargeable=5, counted_to=date(2025, 1, 24)), STORAGE)
    assert found.demurrage_days == 5
    assert found.converted_on is not None


def test_the_conversion_day_is_the_fifth_chargeable_day():
    found = conversion_day(a_detention(chargeable=5, counted_to=date(2025, 1, 24)), STORAGE)
    assert found == date(2025, 1, 23)


def test_the_conversion_walks_over_the_weekend():
    long = a_detention(chargeable=10, counted_to=date(2025, 2, 3))
    assert conversion_day(long, STORAGE) == date(2025, 1, 23)


def test_a_long_detention_bills_five_days_of_demurrage():
    found = split(a_detention(chargeable=20, counted_to=date(2025, 2, 14)), STORAGE)
    assert found.demurrage_days == 5


def test_storage_counts_every_day_and_not_only_chargeable_ones():
    found = split(a_detention(chargeable=20, counted_to=date(2025, 2, 14)), STORAGE)
    assert found.storage_days == (date(2025, 2, 14) - date(2025, 1, 23)).days


def test_the_demurrage_half_is_priced_up_the_steps():
    found = split(a_detention(chargeable=20, counted_to=date(2025, 2, 14)), STORAGE)
    assert found.demurrage(DEMURRAGE) == Money(10000 + 10000 + 20000 + 20000 + 20000)


def test_the_storage_half_is_priced_flat():
    found = split(a_detention(chargeable=20, counted_to=date(2025, 2, 14)), STORAGE)
    assert found.storage(STORAGE) == Money(3500 * found.storage_days)


def test_the_total_is_the_two_halves():
    found = split(a_detention(chargeable=20, counted_to=date(2025, 2, 14)), STORAGE)
    assert found.total(DEMURRAGE, STORAGE) == found.demurrage(DEMURRAGE) + found.storage(STORAGE)


def test_the_charge_helper_agrees_with_the_split():
    one = a_detention(chargeable=20, counted_to=date(2025, 2, 14))
    assert charge(one, DEMURRAGE, STORAGE) == split(one, STORAGE).total(DEMURRAGE, STORAGE)


def test_a_short_detention_charges_only_demurrage():
    one = a_detention(chargeable=3)
    assert charge(one, DEMURRAGE, STORAGE) == DEMURRAGE.charge(3)


def test_converting_makes_a_long_hold_cheaper_than_the_top_step():
    one = a_detention(chargeable=20, counted_to=date(2025, 2, 14))
    assert charge(one, DEMURRAGE, STORAGE) < DEMURRAGE.charge(20)


def test_a_short_split_prints_as_demurrage_only():
    found = split(a_detention(chargeable=3), STORAGE)
    assert str(found) == "CN 400100 3 days demurrage"


def test_a_converted_split_prints_both_halves():
    found = split(a_detention(chargeable=20, counted_to=date(2025, 2, 14)), STORAGE)
    assert "days storage from 2025-01-23" in str(found)


def test_a_car_released_on_the_conversion_day_stores_nothing():
    found = split(a_detention(chargeable=5, counted_to=date(2025, 1, 23)), STORAGE)
    assert found.storage_days == 0
    assert found.converted is False
